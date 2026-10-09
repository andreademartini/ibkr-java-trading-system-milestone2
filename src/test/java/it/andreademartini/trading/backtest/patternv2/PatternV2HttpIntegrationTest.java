package it.andreademartini.trading.backtest.patternv2;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1RunStore;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.*;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:pattern-v2-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "trading.mode=BACKTEST","trading.ibkr.auto-connect=false","trading.order-submission-enabled=false","trading.strategy.auto-execute-signals=false"})
@Import(PatternV2HttpIntegrationTest.IsolatedStore.class)
class PatternV2HttpIntegrationTest {
    @TestConfiguration static class IsolatedStore {
        @Bean @Primary PatternV2Store v2TestStore() throws Exception {return new PatternV2Store(Files.createTempDirectory("pattern-v2-http-"));}
    }
    @Autowired Environment environment;
    @Autowired PatternV1Properties frozen;
    @MockitoBean HistoricalDataService historical;
    @MockitoSpyBean PatternV1RunStore v1Store;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    HttpResponse<byte[]> request(String path,String method,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+environment.getProperty("local.server.port")+path));
        if(body!=null)builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        else builder.method(method,HttpRequest.BodyPublishers.noBody());
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void configurationRunHistoryBenchmarkAndExportAreIndependentEndToEnd() throws Exception {
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        when(historical.load(anyString(),any(),any(),any())).thenAnswer(i->i.getArgument(0).equals("AAPL")?bars:List.of());
        var before=mapper.valueToTree(frozen);
        var base=mapper.readTree(request("/api/backtest/pattern-v2/baseline","POST",null).body());
        assertEquals(9,base.path("symbols").size());
        var patterns=mapper.readTree(request("/api/backtest/pattern-v2/patterns","GET",null).body());assertEquals(4,patterns.path("LONG").size());assertEquals(4,patterns.path("SHORT").size());
        ObjectNode config=base.deepCopy();config.put("name","V2_HTTP_COST_TEST");var g=(ObjectNode)config.get("global");g.put("startDate",START.toString());g.put("endDate",START.plusDays(2).toString());
        var save=request("/api/backtest/pattern-v2/configurations","POST",config);assertEquals(200,save.statusCode());var saved=mapper.readTree(save.body());
        assertEquals(config,mapper.readTree(request("/api/backtest/pattern-v2/configurations/"+saved.path("id").asText(),"GET",null).body()).path("configuration"));
        var response=request("/api/backtest/pattern-v2/run","POST",config);assertEquals(200,response.statusCode(),new String(response.body()));var run=mapper.readTree(response.body());String id=run.path("runId").asText();
        assertEquals("PATTERN_V2",run.path("strategy").asText());assertEquals(9874.436,run.path("portfolio").path("finalCapital").asDouble(),1e-9);
        assertEquals(2,run.path("costs").path("commissions").asDouble());assertEquals(3.564,run.path("costs").path("slippage").asDouble(),1e-9);
        assertEquals(config,run.path("configuration"));
        var history=mapper.readTree(request("/api/backtest/pattern-v2/runs","GET",null).body());assertEquals(id,history.get(0).path("runId").asText());assertEquals("V2_HTTP_COST_TEST",history.get(0).path("configurationName").asText());
        assertEquals("FIXED_AMOUNT",history.get(0).path("maxPositionValueMode").asText());
        ObjectNode dynamic=config.deepCopy();dynamic.put("name","V2_HTTP_DYNAMIC_LIMITS");var limits=(ObjectNode)dynamic.get("global");
        for(var key:List.of("maxPositionValue","maxLongNotional","maxShortNotional")){limits.put(key+"Mode","CURRENT_EQUITY_PERCENT");limits.put(key+"Percent",100.0);}
        var replay=request("/api/backtest/pattern-v2/runs/"+id+"/capital-limits-replay","POST",dynamic);assertEquals(200,replay.statusCode(),new String(replay.body()));
        var dynamicRun=mapper.readTree(replay.body());assertEquals(dynamic,dynamicRun.path("configuration"));
        var comparison=request("/api/backtest/pattern-v2/runs/compare-capital-limits","POST",Map.of("runIds",List.of(id,dynamicRun.path("runId").asText())));assertEquals(200,comparison.statusCode());
        assertEquals(400,request("/api/backtest/pattern-v2/runs/compare","POST",Map.of("runIds",List.of(id,dynamicRun.path("runId").asText()))).statusCode());
        var benchmark=request("/api/backtest/pattern-v2/runs/"+id+"/benchmark","POST",null);assertEquals(200,benchmark.statusCode());var compared=mapper.readTree(benchmark.body());
        assertEquals(compared.path("portfolio").path("finalCapital"),compared.path("benchmark").path("finalCapital"));
        assertEquals(before,mapper.valueToTree(frozen));verify(v1Store,never()).save(any());
        var export=request("/api/backtest/pattern-v2/runs/"+id+"/export/excel","GET",null);assertEquals(200,export.statusCode());assertTrue(export.headers().firstValue("Content-Disposition").orElse("").contains("PATTERN_V2_"));
        var invalid=config.deepCopy();((ObjectNode)invalid.get("global")).put("maxQuantity",0);assertEquals(400,request("/api/backtest/pattern-v2/run","POST",invalid).statusCode());
        assertEquals(404,request("/api/backtest/pattern-v2/runs/"+UUID.randomUUID(),"GET",null).statusCode());
        assertEquals(400,request("/api/backtest/pattern-v2/runs/not-a-uuid","GET",null).statusCode());
        assertEquals(base,mapper.readTree(request("/api/backtest/pattern-v2/reset","POST",null).body()));
        assertEquals(200,request("/pattern-v2.html","GET",null).statusCode());
        assertEquals(200,request("/pattern-v2.js","GET",null).statusCode());
    }
    @Test void enabledRangeFilterPersistsPairThresholdAndReplayThroughHttp() throws Exception {
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        when(historical.load(anyString(),any(),any(),any())).thenAnswer(i->i.getArgument(0).equals("AAPL")?bars:List.of());
        ObjectNode config=(ObjectNode)mapper.readTree(request("/api/backtest/pattern-v2/baseline","POST",null).body());
        config.put("name","V2_RANGE_HTTP");config.put("patternRangeFilterEnabled",true);config.put("minimumPatternRangePct",3.0);
        ((ObjectNode)config.get("global")).put("startDate",START.toString()).put("endDate",START.plusDays(2).toString());
        var response=request("/api/backtest/pattern-v2/run","POST",config);assertEquals(200,response.statusCode(),new String(response.body()));
        var run=mapper.readTree(response.body());var id=run.path("runId").asText();
        assertEquals(1,run.path("patternRangeAnalysis").path("statistics").path("signalsRejected").asInt());
        assertEquals(0,run.path("portfolio").path("trades").size());
        var base=mapper.readTree(request("/api/backtest/pattern-v2/runs/"+run.path("pairedRunId").asText(),"GET",null).body());
        assertEquals(1,base.path("portfolio").path("trades").size());assertEquals(run.path("datasetId"),base.path("datasetId"));
        var history=mapper.readTree(request("/api/backtest/pattern-v2/runs","GET",null).body());
        var entry=java.util.stream.StreamSupport.stream(history.spliterator(),false).filter(r->r.path("runId").asText().equals(id)).findFirst().orElseThrow();
        assertTrue(entry.path("patternRangeFilterEnabled").asBoolean());assertEquals(3,entry.path("minimumPatternRangePct").asDouble());
        var loaded=mapper.readTree(request("/api/backtest/pattern-v2/runs/"+id,"GET",null).body());assertEquals(run,loaded);
        var replay=request("/api/backtest/pattern-v2/runs/"+id+"/replay","POST",null);assertEquals(200,replay.statusCode(),new String(replay.body()));
        assertEquals(run.path("portfolio").path("equityCurve"),mapper.readTree(replay.body()).path("portfolio").path("equityCurve"));
        assertEquals(200,request("/api/backtest/pattern-v2/runs/"+id+"/export/excel","GET",null).statusCode());
        verify(v1Store,never()).save(any());
    }

    @Test void closeStrengthPresetSnapshotReplayAndValidatedComparisonThroughHttp() throws Exception {
        var before=mapper.valueToTree(frozen);var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));
        when(historical.load(anyString(),any(),any(),any())).thenAnswer(i->i.getArgument(0).equals("AAPL")?bars:List.of());
        ObjectNode config=(ObjectNode)mapper.readTree(request("/api/backtest/pattern-v2/baseline","POST",null).body());((ObjectNode)config.get("global")).put("startDate",START.toString()).put("endDate",START.plusDays(2).toString());
        var base=mapper.readTree(request("/api/backtest/pattern-v2/run","POST",config).body());var baseId=base.path("runId").asText();var current=mapper.readTree(request("/api/backtest/pattern-v2/configuration","GET",null).body());
        var close=config.deepCopy();close.put("name","V2_CLOSE_HTTP").put("closeStrengthFilterEnabled",true).put("maxCloseStrength",.805);
        var saved=request("/api/backtest/pattern-v2/configurations/preset","POST",close);assertEquals(200,saved.statusCode(),new String(saved.body()));assertEquals(current,mapper.readTree(request("/api/backtest/pattern-v2/configuration","GET",null).body()));
        var response=request("/api/backtest/pattern-v2/runs/"+baseId+"/replay","POST",close);assertEquals(200,response.statusCode(),new String(response.body()));var run=mapper.readTree(response.body());
        assertEquals(close,run.path("configuration"));assertEquals(baseId,run.path("pairedRunId").asText());assertEquals(base.path("datasetId"),run.path("datasetId"));assertEquals(1,run.path("closeStrengthAnalysis").path("statistics").path("signalsRejected").asInt());assertEquals(0,run.path("portfolio").path("trades").size());
        var compare=request("/api/backtest/pattern-v2/runs/compare","POST",Map.of("runIds",List.of(baseId,run.path("runId").asText())));assertEquals(200,compare.statusCode(),new String(compare.body()));assertEquals(17,mapper.readTree(compare.body()).path("metrics").size());
        assertEquals(400,request("/api/backtest/pattern-v2/runs/compare","POST",Map.of("runIds",List.of(baseId,baseId))).statusCode());
        var invalid=close.deepCopy();invalid.put("maxCloseStrength",1.01);assertEquals(400,request("/api/backtest/pattern-v2/configurations/preset","POST",invalid).statusCode());
        var changed=close.deepCopy();((ObjectNode)changed.get("global")).put("riskPercent",1);assertEquals(400,request("/api/backtest/pattern-v2/runs/"+baseId+"/replay","POST",changed).statusCode());
        var history=mapper.readTree(request("/api/backtest/pattern-v2/runs","GET",null).body());var entry=java.util.stream.StreamSupport.stream(history.spliterator(),false).filter(r->r.path("runId").equals(run.path("runId"))).findFirst().orElseThrow();assertTrue(entry.path("closeStrengthFilterEnabled").asBoolean());assertEquals(.805,entry.path("maxCloseStrength").asDouble());assertEquals(base.path("datasetId"),entry.path("datasetId"));
        assertEquals(before,mapper.valueToTree(frozen));verify(v1Store,never()).save(any());
    }

    @Test void bollingerCheckboxPersistsAndReplaysSnapshotThroughHttp() throws Exception {
        var bars=engulfing("AAPL");when(historical.load(anyString(),any(),any(),any())).thenAnswer(i->i.getArgument(0).equals("AAPL")?bars:List.of());
        ObjectNode c=(ObjectNode)mapper.readTree(request("/api/backtest/pattern-v2/baseline","POST",null).body());((ObjectNode)c.get("global")).put("startDate",START.toString()).put("endDate",START.plusDays(2).toString());assertFalse(c.path("bollingerAtrFilterEnabled").asBoolean());
        var response=request("/api/backtest/pattern-v2/run","POST",c);assertEquals(200,response.statusCode());var baseline=mapper.readTree(response.body());String id=baseline.path("runId").asText();
        c.put("bollingerAtrFilterEnabled",true).put("bollingerStdDev",2.125).put("maxBollingerDistanceAtr",.505);
        assertEquals(200,request("/api/backtest/pattern-v2/configuration","PUT",c).statusCode());assertEquals(c,mapper.readTree(request("/api/backtest/pattern-v2/configuration","GET",null).body()));
        var replay=request("/api/backtest/pattern-v2/runs/"+id+"/replay","POST",c);assertEquals(200,replay.statusCode(),new String(replay.body()));var run=mapper.readTree(replay.body());assertEquals(c,run.path("configuration"));
        assertEquals(1,run.path("bollingerAtrAnalysis").path("statistics").path("insufficientData").asInt());assertEquals(0,run.path("portfolio").path("trades").size());
        var saved=request("/api/backtest/pattern-v2/configurations/preset","POST",c);assertEquals(200,saved.statusCode());assertEquals(c,mapper.readTree(saved.body()).path("configuration"));
        var history=mapper.readTree(request("/api/backtest/pattern-v2/runs","GET",null).body());var entry=java.util.stream.StreamSupport.stream(history.spliterator(),false).filter(r->r.path("runId").equals(run.path("runId"))).findFirst().orElseThrow();assertTrue(entry.path("bollingerAtrFilterEnabled").asBoolean());assertEquals(.505,entry.path("maxBollingerDistanceAtr").asDouble());
        assertEquals(200,request("/api/backtest/pattern-v2/runs/"+run.path("runId").asText()+"/export/excel","GET",null).statusCode());
    }
    @Test void capitalShadowEndpointPreservesSavedPortfolio() throws Exception {
        var bars=new ArrayList<>(engulfing("AAPL"));bars.set(2,bar("AAPL",2,100,107,97,103));when(historical.load(anyString(),any(),any(),any())).thenAnswer(i->i.getArgument(0).equals("AAPL")?bars:List.of());var frozenBefore=mapper.valueToTree(frozen);
        ObjectNode c=(ObjectNode)mapper.readTree(request("/api/backtest/pattern-v2/baseline","POST",null).body());((ObjectNode)c.get("global")).put("startDate",START.toString()).put("endDate",START.plusDays(2).toString());assertFalse(c.path("capitalSkipShadowAnalysisEnabled").asBoolean());var response=request("/api/backtest/pattern-v2/run","POST",c);assertEquals(200,response.statusCode());var before=mapper.readTree(response.body());var id=before.path("runId").asText();
        var analysis=request("/api/backtest/pattern-v2/runs/"+id+"/capital-skips-analysis","POST",null);assertEquals(200,analysis.statusCode(),new String(analysis.body()));var after=mapper.readTree(request("/api/backtest/pattern-v2/runs/"+id,"GET",null).body());assertEquals(before.path("portfolio"),after.path("portfolio"));assertEquals(before.path("configuration"),after.path("configuration"));assertEquals(before.path("executions"),after.path("executions"));assertFalse(after.path("capitalSkipShadowAnalysis").isNull());assertEquals(200,request("/api/backtest/pattern-v2/runs/"+id+"/export/excel","GET",null).statusCode());
        c.put("capitalSkipShadowAnalysisEnabled",true);var saved=request("/api/backtest/pattern-v2/configurations/preset","POST",c);assertEquals(200,saved.statusCode());assertTrue(mapper.readTree(saved.body()).path("configuration").path("capitalSkipShadowAnalysisEnabled").asBoolean());assertEquals(frozenBefore,mapper.valueToTree(frozen));verify(v1Store,never()).save(any());
    }
}
