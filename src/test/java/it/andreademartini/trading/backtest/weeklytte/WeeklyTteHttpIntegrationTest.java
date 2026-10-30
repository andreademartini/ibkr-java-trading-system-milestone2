package it.andreademartini.trading.backtest.weeklytte;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:weekly-tte-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "trading.mode=BACKTEST","trading.ibkr.auto-connect=false","trading.order-submission-enabled=false","trading.strategy.auto-execute-signals=false"})
@Import(WeeklyTteHttpIntegrationTest.Isolation.class)
class WeeklyTteHttpIntegrationTest {
    @TestConfiguration static class Isolation {
        @Bean @Primary WeeklyTteStore isolatedWeeklyStore()throws Exception{return new WeeklyTteStore(Files.createTempDirectory("weekly-http-"));}
    }
    @Autowired Environment env;
    @Autowired WeeklyTteService service;
    @MockitoSpyBean it.andreademartini.trading.ibkr.IbkrApiClient broker;
    HttpResponse<byte[]> request(String path,String method,Object body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+env.getProperty("local.server.port")+path));
        if(body==null)b.method(method,HttpRequest.BodyPublishers.noBody());else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofByteArray(WeeklyTteStore.JSON.writeValueAsBytes(body)));
        return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void completeHttpWorkflowAndFrozenConfigurationsUnchanged()throws Exception{
        var mapper=WeeklyTteStore.JSON;
        var beforeV1=request("/api/backtest/pattern-v1/configuration","GET",null).body();
        var beforeV2=request("/api/backtest/pattern-v2-stable/configuration","GET",null).body();
        var beforeV3=request("/api/backtest/pattern-v3/configuration","GET",null).body();
        assertEquals(200,request("/weekly-high-tte.html","GET",null).statusCode());assertEquals(200,request("/weekly-high-tte.js","GET",null).statusCode());
        var c=WeeklyTteTest.config();assertEquals(200,request("/api/backtest/weekly-high-tte/configuration","PUT",c).statusCode());
        var config=mapper.readTree(request("/api/backtest/weekly-high-tte/configuration","GET",null).body());assertEquals("WEEKLY_HIGH_TTE",config.path("mode").asText());
        var snapshot=it.andreademartini.trading.backtest.patternv2.PatternV2Dataset.capture(Map.of("TEST",WeeklyTteTest.fixture()));
        var run=service.onSnapshot(c,snapshot);String root="/api/backtest/weekly-high-tte/runs/"+run.runId();
        assertEquals(200,request(root,"GET",null).statusCode());assertEquals(200,request(root+"/export/excel","GET",null).statusCode());
        assertEquals(200,request(root+"/replay","POST",null).statusCode());
        var modes=request(root+"/modes","POST",null);assertEquals(200,modes.statusCode());
        var ids=new ArrayList<String>();mapper.readTree(modes.body()).forEach(r->ids.add(r.path("runId").asText()));
        assertEquals(200,request("/api/backtest/weekly-high-tte/runs/compare","POST",Map.of("runIds",ids)).statusCode());
        var workbook=request("/api/backtest/weekly-high-tte/runs/compare/excel","POST",Map.of("runIds",ids));assertEquals(200,workbook.statusCode());
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(workbook.body()))){
            var header=book.getSheet("MODE_COMPARISON").getRow(0);assertEquals(4,header.getPhysicalNumberOfCells());
            assertEquals("WEEKLY_HIGH_BREAKOUT",header.getCell(1).getStringCellValue());assertNotNull(book.getSheet("COMPARISON_CONTEXT"));
        }
        assertEquals(200,request(root+"/sensitivity","POST",null).statusCode());
        assertTrue(mapper.readTree(request("/api/backtest/weekly-high-tte/runs","GET",null).body()).path("runs").size()>=6);
        assertEquals(400,request("/api/backtest/weekly-high-tte/run","POST",WeeklyTteTest.config("riskPercent",0)).statusCode());
        assertEquals(404,request("/api/backtest/weekly-high-tte/runs/"+UUID.randomUUID(),"GET",null).statusCode());
        assertArrayEquals(beforeV1,request("/api/backtest/pattern-v1/configuration","GET",null).body());
        assertArrayEquals(beforeV2,request("/api/backtest/pattern-v2-stable/configuration","GET",null).body());
        assertArrayEquals(beforeV3,request("/api/backtest/pattern-v3/configuration","GET",null).body());
        verify(broker,never()).placeOrder(anyInt(),any(),any());
    }
    @Test void historicalShortRunIsAvailableThroughHttpAndExcelWithoutBrokerWrites()throws Exception{
        var c=HistoricalWeeklyTteTest.config("allowLong",false);
        assertEquals(200,request("/api/backtest/weekly-high-tte/configuration","PUT",c).statusCode());
        var snapshot=it.andreademartini.trading.backtest.patternv2.PatternV2Dataset.capture(Map.of("TEST",HistoricalWeeklyTteTest.mirror(WeeklyTteTest.fixture())));
        var run=service.onSnapshot(c,snapshot);String root="/api/backtest/weekly-high-tte/runs/"+run.runId();
        var result=WeeklyTteStore.JSON.readTree(request(root,"GET",null).body());
        assertEquals("SHORT",result.path("trades").get(0).path("direction").asText());assertFalse(result.path("analysis").path("historicalLevels").isEmpty());
        assertEquals(2,result.path("analysis").path("directionPerformance").size());assertFalse(result.path("analysis").path("levelChartData").isEmpty());
        assertEquals(200,request(root+"/replay","POST",null).statusCode());
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(request(root+"/export/excel","GET",null).body()))){assertNotNull(book.getSheet("HISTORICAL_LEVELS"));assertNotNull(book.getSheet("DIRECTION_PERFORMANCE"));}
        verify(broker,never()).placeOrder(anyInt(),any(),any());
    }
}
