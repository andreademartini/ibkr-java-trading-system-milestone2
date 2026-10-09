package it.andreademartini.trading.paper.patternv1;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import static it.andreademartini.trading.paper.patternv1.PatternPaperTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:pattern-paper-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "trading.mode=BACKTEST","trading.ibkr.auto-connect=false","trading.order-submission-enabled=false","trading.strategy.auto-execute-signals=false",
    "pattern-paper.admin-token=paper-admin-test-token-32-characters-minimum"})
class PatternPaperHttpIntegrationTest {
    @Autowired Environment environment;
    @Autowired PatternPaperStore store;
    @Autowired PatternPaperRepository repository;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean PatternPaperBroker broker;
    HttpClient client;String csrf;
    @BeforeEach void setup(){client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();when(broker.connection()).thenReturn(new Connection(false,"127.0.0.1",4002,ACCOUNT,List.of(),false,false,false,"Identity not verified",null,null,1));when(broker.limitations()).thenReturn(List.of("Identity not verified"));when(broker.reconcile()).thenReturn(new BrokerSnapshot(1,Instant.now(),List.of(),List.of(),List.of(),List.of(),bd(0),false,"Disconnected"));}
    HttpResponse<byte[]> request(String path,Object body,boolean withCsrf)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+environment.getProperty("local.server.port")+path));
        if(body!=null){builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(PatternPaperStore.json(body)));if(withCsrf&&csrf!=null)builder.header("X-Paper-CSRF",csrf);}
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    void login()throws Exception{var result=request("/api/pattern-v1/paper/login",Map.of("token","paper-admin-test-token-32-characters-minimum"),false);assertEquals(200,result.statusCode());csrf=PatternStableStore.JSON.readTree(result.body()).path("csrf").asText();}
    @Test void controlsRequireAdminAndCsrf()throws Exception{assertEquals(401,request("/api/pattern-v1/paper/start",Map.of("commandId",UUID.randomUUID().toString()),false).statusCode());login();assertEquals(403,request("/api/pattern-v1/paper/stop",Map.of("commandId",UUID.randomUUID().toString()),false).statusCode());assertEquals(200,request("/api/pattern-v1/paper/stop",Map.of("commandId",UUID.randomUUID().toString()),true).statusCode());verify(broker,never()).place(any());}
    @Test void webStartCannotBypassPaperProof()throws Exception{login();var response=request("/api/pattern-v1/paper/start",Map.of("commandId",UUID.randomUUID().toString()),true);assertEquals(200,response.statusCode());var json=PatternStableStore.JSON.readTree(response.body());assertNotEquals("PAPER_AUTO",json.path("session").path("mode").asText());assertTrue(json.path("checks").toString().contains("PAPER_IDENTITY"));verify(broker,never()).place(any());}
    @Test void refreshIsReadOnlyAndWorkbookMatchesDashboard()throws Exception{login();assertEquals(200,request("/api/pattern-v1/paper/refresh",Map.of("commandId",UUID.randomUUID().toString()),true).statusCode());var response=request("/api/pattern-v1/paper/export/excel",null,false);assertEquals(200,response.statusCode());try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.body()))){for(String sheet:List.of("CONFIGURATION","ACCOUNT_STATUS","SIGNALS","ORDERS","EXECUTIONS","OPEN_POSITIONS","CLOSED_TRADES","EQUITY_CURVE","DAILY_PERFORMANCE","ERRORS","BACKTEST_VS_PAPER"))assertNotNull(book.getSheet(sheet),sheet);}verify(broker,never()).place(any());verify(broker,never()).cancelVerifiedUnfilledEntry(any());}
    @Test void killSwitchPersistsAndRequiresConfirmation()throws Exception{login();assertEquals(409,request("/api/pattern-v1/paper/kill",Map.of("commandId",UUID.randomUUID().toString(),"confirmation",""),true).statusCode());assertEquals(200,request("/api/pattern-v1/paper/kill",Map.of("commandId",UUID.randomUUID().toString(),"confirmation","ATTIVA KILL SWITCH"),true).statusCode());assertTrue(store.session().killSwitch);var reloaded=new PatternPaperStore(repository,manager);reloaded.initialize();assertTrue(reloaded.session().killSwitch);assertEquals(Mode.KILL_SWITCH,reloaded.session().mode);verify(broker,never()).place(any());}
    @Test void dashboardAndPaperTabAreServed()throws Exception{login();assertEquals(200,request("/api/pattern-v1/paper/status",null,false).statusCode());var page=request("/pattern-v1.html",null,false);assertEquals(200,page.statusCode());assertTrue(new String(page.body(),java.nio.charset.StandardCharsets.UTF_8).contains("AVVIA PAPER AUTO"));assertEquals(200,request("/pattern-v1-paper.js",null,false).statusCode());}
    @Test void concurrentDatabaseCommandsCannotLoseUpdates()throws Exception{
        var other=new PatternPaperStore(repository,manager);other.initialize();long initial=store.session().generation;
        try(var pool=Executors.newFixedThreadPool(4)){var tasks=new ArrayList<Callable<Void>>();for(int i=0;i<20;i++){final int index=i;tasks.add(()->{(index%2==0?store:other).update(s->{s.generation++;return null;});return null;});}for(var future:pool.invokeAll(tasks))future.get();}
        assertEquals(initial+20,store.session().generation);
    }
}
