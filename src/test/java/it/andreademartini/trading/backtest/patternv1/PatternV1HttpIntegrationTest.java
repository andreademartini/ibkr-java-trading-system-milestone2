package it.andreademartini.trading.backtest.patternv1;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.persistence.*;
import it.andreademartini.trading.marketdata.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.datasource.url=jdbc:h2:mem:pattern-v1-http-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "trading.mode=BACKTEST", "trading.ibkr.auto-connect=false",
        "trading.order-submission-enabled=false", "trading.strategy.auto-execute-signals=false",
        "historical-data.provider=STOOQ", "logging.level.it.andreademartini.trading=warn"})
class PatternV1HttpIntegrationTest {
    @Autowired Environment environment;
    @Autowired HistoricalBarRepository repository;
    @Autowired PatternV1BacktestService service;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final HttpClient client=HttpClient.newHttpClient();
    private HttpResponse<byte[]> request(String path,String json) throws Exception {
        var uri=URI.create("http://localhost:"+environment.getProperty("local.server.port")+path);
        var builder=HttpRequest.newBuilder(uri);
        if(json!=null)builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void actualHistoricalLoaderApiPersistenceAndExcelWork() throws Exception {
        repository.deleteAll();
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        for(var bar:bars)repository.save(new HistoricalBarEntity("STOOQ","AAPL","DAILY",bar,java.time.Instant.now()));
        var configuration=request("/api/backtest/pattern-v1/legacy/configuration",null);
        assertEquals(200,configuration.statusCode());assertEquals(9,mapper.readTree(configuration.body()).path("symbols").size());
        var response=request("/api/backtest/pattern-v1/legacy/run","{\"symbols\":[\"AAPL\"],\"startDate\":\"2020-01-01\",\"endDate\":\"2020-01-03\",\"initialCapital\":10000,\"commissionPerOrder\":0,\"slippagePercent\":0}");
        assertEquals(200,response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));
        var tree=mapper.readTree(response.body());assertEquals("PATTERN_V1",tree.path("strategy").asText());
        assertEquals(1,tree.path("trades").size());assertEquals("STOP",tree.path("trades").get(0).path("exitReason").asText());
        assertEquals(9880,tree.path("finalCapital").asDouble());
        assertEquals(9880,tree.path("finalCash").asDouble());assertEquals(2,tree.path("schemaVersion").asInt());
        assertEquals(6000,tree.path("settings").path("maxOrderValue").asDouble());
        assertEquals(0,tree.path("trades").get(0).path("remainingQuantity").asLong());
        String id=tree.path("backtestId").asText();
        assertEquals(200,request("/api/backtest/pattern-v1/legacy/"+id,null).statusCode());
        var excel=request("/api/backtest/pattern-v1/legacy/"+id+"/export/excel",null);assertEquals(200,excel.statusCode());
        assertTrue(excel.headers().firstValue("Content-Type").orElse("").contains("spreadsheetml"));assertEquals('P',excel.body()[0]);assertEquals('K',excel.body()[1]);
        assertEquals(400,request("/api/backtest/pattern-v1/legacy/run","{\"symbols\":[\"AAPL\"],\"timeframe\":\"HOURLY\"}").statusCode());
        assertEquals(404,request("/api/backtest/pattern-v1/legacy/"+UUID.randomUUID(),null).statusCode());
        assertEquals(400,request("/api/backtest/pattern-v1/legacy/not-a-uuid",null).statusCode());
        var page=request("/pattern-v1.html",null);assertEquals(200,page.statusCode());
        assertTrue(new String(page.body(),StandardCharsets.UTF_8).contains("id=\"v1-mapping\""));
        assertEquals(200,request("/pattern-v1.js",null).statusCode());assertEquals(200,request("/pattern-v1-metrics.js",null).statusCode());
        assertEquals(200,request("/vendor/lightweight-charts.standalone.production.js",null).statusCode());
    }
    @Test void requestedSizingLimitsSurviveNormalizationAndPersistence() throws Exception {
        repository.deleteAll();
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        for(var bar:bars)repository.save(new HistoricalBarEntity("STOOQ","AAPL","DAILY",bar,java.time.Instant.now()));
        for (int maxQuantity : new int[] {1000, 10}) {
            var response=request("/api/backtest/pattern-v1/legacy/run", mapper.writeValueAsString(Map.of(
                    "symbols", List.of("AAPL"), "startDate", "2020-01-01", "endDate", "2020-01-03",
                    "initialCapital", 10000, "commissionPerOrder", 0, "slippagePercent", 0,
                    "maxOrderValue", 6000, "maxQuantity", maxQuantity)));
            assertEquals(200,response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));
            var json=mapper.readTree(response.body());
            assertEquals(6000,json.path("settings").path("maxOrderValue").asDouble());
            assertEquals(maxQuantity,json.path("settings").path("maxQuantity").asLong());
            assertEquals(Math.min(60,maxQuantity),json.path("trades").get(0).path("quantity").asLong());
            assertEquals(10000 - Math.min(60,maxQuantity)*2,json.path("finalCapital").asDouble());
            var saved=request("/api/backtest/pattern-v1/legacy/"+json.path("backtestId").asText(),null);
            assertEquals(200,saved.statusCode());
            assertEquals(json,mapper.readTree(saved.body()));
        }
    }
    @Test void commissionDefaultsAndCustomParametersSurviveApiPersistenceAndExport() throws Exception {
        repository.deleteAll();
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        for(var bar:bars)repository.save(new HistoricalBarEntity("STOOQ","AAPL","DAILY",bar,java.time.Instant.now()));
        for (String maximum : List.of("default", "1", "0.01")) {
            Map<String,Object> body=new LinkedHashMap<>(Map.of("symbols",List.of("AAPL"),
                    "startDate","2020-01-01","endDate","2020-01-03","initialCapital",10000));
            if (!maximum.equals("default")) body.put("commission",Map.of("model","IBKR_US_FIXED",
                    "perShare",0.1,"minimumPerOrder",2,"maximumPercentOrderValue",Double.parseDouble(maximum)));
            var response=request("/api/backtest/pattern-v1/legacy/run",mapper.writeValueAsString(body));
            assertEquals(200,response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));
            var json=mapper.readTree(response.body());var trade=json.path("trades").get(0);
            assertEquals("IBKR_US_FIXED",json.path("settings").path("commission").path("model").asText());
            assertEquals(0.03,json.path("settings").path("slippagePercent").asDouble());
            assertEquals(60,trade.path("quantity").asLong());
            double expectedCosts=maximum.equals("default")?5.564:maximum.equals("1")?15.564:4.752;
            assertEquals(expectedCosts,trade.path("costs").asDouble(),1e-9);
            assertEquals(9880-expectedCosts,json.path("finalCash").asDouble(),1e-9);
            String id=json.path("backtestId").asText();
            var saved=request("/api/backtest/pattern-v1/legacy/"+id,null);
            assertEquals(200,saved.statusCode());assertEquals(json,mapper.readTree(saved.body()));
            var excel=request("/api/backtest/pattern-v1/legacy/"+id+"/export/excel",null);
            assertEquals(200,excel.statusCode());
            try (var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel.body()))) {
                boolean modelFound=false;
                for (var row:workbook.getSheet("CONFIGURATION")) {
                    if ("commission.model".equals(row.getCell(0).getStringCellValue())) {
                        assertEquals("IBKR_US_FIXED",row.getCell(1).getStringCellValue());modelFound=true;
                    }
                }
                assertTrue(modelFound);
            }
        }
        assertEquals(400,request("/api/backtest/pattern-v1/legacy/run", "{\"commission\":{\"model\":\"IBKR_US_FIXED\",\"perShare\":-0.005}}").statusCode());
    }
    @Test void timeoutIsClosedAndPersistedThroughTheApi() throws Exception {
        repository.deleteAll();
        var bars=new ArrayList<>(engulfing("AAPL").subList(0,2));
        for(int i=2;i<22;i++)bars.add(bar("AAPL",i,100,105.5,99,i==21?105:100));
        bars.add(bar("AAPL",22,100,1000,1,900));
        for(var bar:bars)repository.save(new HistoricalBarEntity("STOOQ","AAPL","DAILY",bar,java.time.Instant.now()));
        var response=request("/api/backtest/pattern-v1/legacy/run","{\"symbols\":[\"AAPL\"],\"startDate\":\"2020-01-01\",\"endDate\":\"2020-01-23\",\"initialCapital\":10000,\"commissionPerOrder\":0,\"slippagePercent\":0}");
        assertEquals(200,response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));
        var json=mapper.readTree(response.body());var trade=json.path("trades").get(0);
        assertEquals("TIMEOUT",trade.path("exitReason").asText());assertTrue(trade.path("closed").asBoolean());
        assertEquals(0,trade.path("remainingQuantity").asLong());assertEquals(20,trade.path("holdingBars").asInt());
        assertEquals(105,trade.path("exitPrice").asDouble());assertEquals(2.5,trade.path("resultR").asDouble());
        assertEquals("2020-01-22",trade.path("exitDate").asText());assertEquals(0,json.path("summary").path("openTrades").asInt());
        assertEquals(10300,json.path("finalCash").asDouble());
        var saved=request("/api/backtest/pattern-v1/legacy/"+json.path("backtestId").asText(),null);
        assertEquals(200,saved.statusCode());assertTrue(mapper.readTree(saved.body()).path("trades").get(0).path("closed").asBoolean());
        assertEquals("CLOSE_AT_MAX_HOLDING_BAR",json.path("settings").path("timeoutExitMode").asText());
    }
}
