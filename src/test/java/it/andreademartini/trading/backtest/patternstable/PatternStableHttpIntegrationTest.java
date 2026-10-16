package it.andreademartini.trading.backtest.patternstable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.engine.StableSimulation;
import it.andreademartini.trading.backtest.patternv2.PatternV2Store;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.orders.PaperOrderExecutionService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:pattern-stable-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "trading.mode=BACKTEST","trading.ibkr.auto-connect=false","trading.order-submission-enabled=false","trading.strategy.auto-execute-signals=false"})
class PatternStableHttpIntegrationTest {
    @Autowired Environment environment;
    @MockitoBean PatternStableService stable;
    @MockitoBean PaperOrderExecutionService orders;
    HttpResponse<byte[]> request(String path,String body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+environment.getProperty("local.server.port")+path));
        if(body!=null)b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void officialApiFrozenVersionHistoryAndExcelNeverInvokeExecution()throws Exception{
        var version=new PatternStableService(null,new PatternV2Store(),configuration(),new TradingSystemProperties()).version();
        var simulation=new StableSimulation().simulate(version.configuration(),Map.of("AAPL",engulfing("AAPL")),UUID.randomUUID().toString());
        var run=new PatternStableService.Run(simulation.runId(),version.strategyId(),version.strategyVersion(),Instant.now(),version.referenceRunId(),version.configurationHash(),"test-dataset",simulation);
        when(stable.version()).thenReturn(version);when(stable.run()).thenReturn(run);when(stable.result(run.runId())).thenReturn(run);when(stable.history()).thenReturn(List.of(run));
        var c=request("/api/backtest/pattern-v1/configuration",null);assertEquals(200,c.statusCode());assertEquals("2.3",PatternStableStore.JSON.readTree(c.body()).path("strategyVersion").asText());
        assertEquals("OPEN_FIRST",PatternStableStore.JSON.readTree(c.body()).path("configuration").path("exitFillPolicy").asText());
        assertEquals(400,request("/api/backtest/pattern-v1/run","{\"initialCapital\":20000}").statusCode());
        assertEquals(200,request("/api/backtest/pattern-v1/run","{}").statusCode());verify(stable,times(1)).run();
        var riskConfig=BacktestRiskParameter.apply(version.configuration(),8.0);
        var riskSimulation=new StableSimulation().simulate(riskConfig,Map.of("AAPL",engulfing("AAPL")),UUID.randomUUID().toString());
        var riskRun=new PatternStableService.Run(riskSimulation.runId(),version.strategyId(),version.strategyVersion()+"-risk-test",Instant.now(),version.referenceRunId(),PatternStableStore.hash(riskConfig),"test-dataset",riskSimulation);
        when(stable.runWithRisk(8.0)).thenReturn(riskRun);
        var riskResponse=request("/api/backtest/pattern-v1/run","{\"riskPercent\":8}");assertEquals(200,riskResponse.statusCode());assertEquals(8,PatternStableStore.JSON.readTree(riskResponse.body()).path("settings").path("riskPercent").asDouble());verify(stable).runWithRisk(8.0);
        assertEquals(400,request("/api/backtest/pattern-v1/run","{\"riskPercent\":8,\"maxHoldingBars\":20}").statusCode());
        assertEquals(200,request("/api/backtest/pattern-v1/"+run.runId(),null).statusCode());
        assertEquals(200,request("/api/backtest/pattern-v1/"+run.runId()+"/metadata",null).statusCode());
        assertEquals(200,request("/api/backtest/pattern-v1/runs",null).statusCode());
        assertEquals(200,request("/api/backtest/pattern-v1/legacy/configuration",null).statusCode());
        var exported=request("/api/backtest/pattern-v1/"+run.runId()+"/export/excel",null);assertEquals(200,exported.statusCode());
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(exported.body()))){assertNotNull(book.getSheet("STRATEGY_VERSION"));assertNotNull(book.getSheet("TRADES"));}
        assertEquals(200,request("/pattern-v1.html",null).statusCode());assertEquals(200,request("/pattern-v1-legacy.html",null).statusCode());assertEquals(200,request("/pattern-v2.html",null).statusCode());
        verifyNoInteractions(orders);
    }
}
