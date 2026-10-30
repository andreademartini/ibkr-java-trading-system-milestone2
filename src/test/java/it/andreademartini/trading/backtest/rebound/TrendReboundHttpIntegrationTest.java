package it.andreademartini.trading.backtest.rebound;

import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:trend-rebound-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "trading.mode=BACKTEST","trading.ibkr.auto-connect=false","trading.order-submission-enabled=false","trading.strategy.auto-execute-signals=false"})
@Import(TrendReboundHttpIntegrationTest.Isolation.class)
class TrendReboundHttpIntegrationTest {
    @TestConfiguration static class Isolation {@Bean @Primary TrendReboundStore isolatedReboundStore()throws Exception{return new TrendReboundStore(Files.createTempDirectory("trend-rebound-http-"));}}
    @Autowired Environment env;@Autowired TrendReboundService service;
    @MockitoSpyBean it.andreademartini.trading.ibkr.IbkrApiClient broker;
    HttpResponse<byte[]> request(String path,String method,Object body)throws Exception {var b=HttpRequest.newBuilder(URI.create("http://localhost:"+env.getProperty("local.server.port")+path));if(body==null)b.method(method,HttpRequest.BodyPublishers.noBody());else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofByteArray(TrendReboundStore.JSON.writeValueAsBytes(body)));return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofByteArray());}
    @Test void catalogMenuConfigurationHistoryReplayAndExcelWorkWithoutAffectingOtherStrategies()throws Exception {
        var old=List.of("/api/backtest/pattern-v1/configuration","/api/backtest/pattern-v2-stable/configuration","/api/backtest/pattern-v3/configuration");var before=new HashMap<String,byte[]>();for(var path:old)before.put(path,request(path,"GET",null).body());
        var catalog=request("/api/backtest/research-strategies","GET",null);assertEquals(200,catalog.statusCode());assertTrue(new String(catalog.body(),java.nio.charset.StandardCharsets.UTF_8).contains(TrendReboundConfiguration.ID));
        for(String page:List.of("/trend-pullback-rebound.html","/pattern-v1.html","/config.html")){var response=request(page,"GET",null);assertEquals(200,response.statusCode());assertTrue(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8).contains("href=\"/trend-pullback-rebound.html\""));}
        var c=TrendReboundEngineTest.config();String base="/api/backtest/trend-pullback-rebound";assertEquals(200,request(base+"/configuration","PUT",c).statusCode());
        var run=service.onSnapshot(c,PatternV2Dataset.capture(Map.of("TEST",TrendReboundEngineTest.series("TEST"))));
        assertEquals(200,request(base+"/runs","GET",null).statusCode());assertEquals(200,request(base+"/runs/"+run.runId(),"GET",null).statusCode());
        var excel=request(base+"/runs/"+run.runId()+"/export/excel","GET",null);assertEquals(200,excel.statusCode());assertTrue(excel.headers().firstValue("Content-Disposition").orElseThrow().contains(TrendReboundConfiguration.ID));
        try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel.body()))){assertNotNull(wb.getSheet("RECONCILIATION"));assertNotNull(wb.getSheet("SIGNALS"));}
        assertEquals(200,request(base+"/runs/"+run.runId()+"/replay","POST",null).statusCode());
        assertEquals(400,request(base+"/run","POST",TrendReboundEngineTest.config("stopAtrMultiplier",-1)).statusCode());
        for(var path:old)assertArrayEquals(before.get(path),request(path,"GET",null).body(),path);
        verify(broker,never()).placeOrder(anyInt(),any(),any());
    }
}
