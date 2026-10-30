package it.andreademartini.trading.backtest.reboundv2;

import it.andreademartini.trading.backtest.rebound.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static it.andreademartini.trading.backtest.reboundv2.TrendReboundV2Configuration.ExitMode.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:rebound-v2-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "trading.mode=BACKTEST","trading.ibkr.auto-connect=false","trading.order-submission-enabled=false","trading.strategy.auto-execute-signals=false"})
@Import(TrendReboundV2HttpIntegrationTest.Isolation.class)
class TrendReboundV2HttpIntegrationTest {
    @TestConfiguration static class Isolation {
        @Bean @Primary TrendReboundStore isolatedV1()throws Exception{return new TrendReboundStore(Files.createTempDirectory("v2-http-v1-"));}
        @Bean @Primary TrendReboundV2Store isolatedV2()throws Exception{return new TrendReboundV2Store(Files.createTempDirectory("v2-http-v2-"));}
    }
    @Autowired Environment env;@Autowired TrendReboundStore v1;
    @MockitoSpyBean it.andreademartini.trading.ibkr.IbkrApiClient broker;
    HttpResponse<byte[]> request(String path,String method,Object body)throws Exception {var b=HttpRequest.newBuilder(URI.create("http://localhost:"+env.getProperty("local.server.port")+path));if(body==null)b.method(method,HttpRequest.BodyPublishers.noBody());else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofByteArray(TrendReboundStore.JSON.writeValueAsBytes(body)));return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofByteArray());}
    @Test void allThreeModesExecuteViaHttpAndComparisonsExportOnlyRealCompatibleRunsWithoutBrokerWrites()throws Exception {
        var paths=List.of("/api/backtest/pattern-v1/configuration","/api/backtest/pattern-v2-stable/configuration","/api/backtest/pattern-v3/configuration");var before=new HashMap<String,byte[]>();for(var path:paths)before.put(path,request(path,"GET",null).body());
        var c=TrendReboundV2Test.config();var original=TrendReboundV2Test.baseline(c,TrendReboundV2Test.bars());v1.dataset(PatternV2Dataset.capture(Map.of("TEST",TrendReboundV2Test.bars())));v1.configuration(original.configuration());v1.save(original);
        byte[] originalCurrent=request("/api/backtest/trend-pullback-rebound/configuration","GET",null).body();String base="/api/backtest/trend-pullback-rebound-v2";
        var defaults=TrendReboundStore.JSON.readTree(request(base+"/configuration","GET",null).body());assertEquals("ATR_TARGET_ONLY",defaults.path("exitMode").asText());assertTrue(defaults.path("takeProfitEnabled").asBoolean());
        var menu=request("/trend-pullback-rebound-v2.html","GET",null);assertEquals(200,menu.statusCode());assertTrue(new String(menu.body(),java.nio.charset.StandardCharsets.UTF_8).contains("TREND_PULLBACK_REBOUND_V2_RESEARCH"));assertTrue(new String(request("/api/backtest/research-strategies","GET",null).body(),java.nio.charset.StandardCharsets.UTF_8).contains(TrendReboundV2Configuration.ID));
        assertEquals(200,request(base+"/reference/"+original.runId()+"/configuration","GET",null).statusCode());
        var ids=new ArrayList<String>();for(var mode:List.of(RECOVERY_ONLY,ATR_TARGET_ONLY,FIRST_OF_BOTH)){
            var response=request(base+"/run?referenceV1RunId="+original.runId(),"POST",TrendReboundV2Test.config("exitMode",mode));assertEquals(200,response.statusCode(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8));var json=TrendReboundStore.JSON.readTree(response.body());ids.add(json.path("runId").asText());assertEquals(mode.name(),json.path("configuration").path("exitMode").asText());if(mode==RECOVERY_ONLY)assertTrue(json.path("regressionAudit").path("recoveryOnlyMatchesV1").asBoolean());
        }
        var compared=request(base+"/runs/compare","POST",Map.of("runIds",ids));assertEquals(200,compared.statusCode());assertEquals(3,TrendReboundStore.JSON.readTree(compared.body()).path("rows").size());
        assertEquals(400,request(base+"/runs/compare","POST",Map.of("runIds",List.of(ids.getFirst()))).statusCode());
        var excel=request(base+"/runs/"+ids.get(1)+"/export/excel","GET",null);assertEquals(200,excel.statusCode());assertTrue(excel.headers().firstValue("Content-Disposition").orElseThrow().contains("TREND_PULLBACK_REBOUND_V2_"+ids.get(1)));
        var combined=request(base+"/runs/compare/excel","POST",Map.of("runIds",ids));assertEquals(200,combined.statusCode());try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(combined.body()))){assertEquals(4,book.getSheet("EXIT_MODE_COMPARISON").getPhysicalNumberOfRows());assertEquals(4,book.getSheet("REGRESSION_AUDIT").getPhysicalNumberOfRows());}
        assertEquals(200,request(base+"/runs/"+ids.get(1)+"/replay","POST",null).statusCode());assertEquals(4,TrendReboundStore.JSON.readTree(request(base+"/runs","GET",null).body()).size());assertEquals(1,TrendReboundStore.JSON.readTree(request("/api/backtest/trend-pullback-rebound/runs","GET",null).body()).size());
        assertArrayEquals(originalCurrent,request("/api/backtest/trend-pullback-rebound/configuration","GET",null).body());for(var path:paths)assertArrayEquals(before.get(path),request(path,"GET",null).body(),path);verify(broker,never()).placeOrder(anyInt(),any(),any());
    }
}
