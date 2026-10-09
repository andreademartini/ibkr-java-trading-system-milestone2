package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.detectors;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named="capitalShadowExperiment",matches="true")
class CapitalSkipShadowExperimentTest {
    @Test void replayBaselineAndVerifyExactFinancialInvariance() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);var output=Path.of("outputs/capital-shadow-evidence");
        var baseline=mapper.readValue(output.resolve("baseline-before.json").toFile(),PatternV2Run.class);var store=new PatternV2Store(Path.of("data/pattern-v2"));var dataset=store.dataset(baseline.datasetId());
        var service=new PatternV2Service(new PatternV1Properties(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);
        String identity=UUID.randomUUID().toString();var off=service.simulate(baseline.configuration().withCapitalSkipShadowAnalysis(false),dataset.bars(),identity);var on=service.simulate(baseline.configuration().withCapitalSkipShadowAnalysis(true),dataset.bars(),identity);
        assertEquals(off.portfolio(),on.portfolio());assertEquals(off.executions(),on.executions());assertEquals(off.costs(),on.costs());assertEquals(off.analysis(),on.analysis());assertEquals(off.capitalSkipAudits(),on.capitalSkipAudits());
        var before=mapper.valueToTree(baseline);var after=mapper.valueToTree(off);for(var key:List.of("summary","equityCurve","symbolResults","directionResults","finalCapital","finalCash","finalAvailableCash","maxDrawdown","maxDrawdownPercent","cagr","averageGrossExposurePercent","timeInMarketPercent","priceSeries"))assertEquals(before.path("portfolio").path(key),after.path("portfolio").path(key),"Previous baseline parity: "+key);
        var oldTrades=before.path("portfolio").path("trades").deepCopy();var newTrades=after.path("portfolio").path("trades").deepCopy();for(var t:oldTrades)((ObjectNode)t).remove("tradeId");for(var t:newTrades)((ObjectNode)t).remove("tradeId");assertEquals(oldTrades,newTrades);assertEquals(before.path("costs"),after.path("costs"));
        off=off.withResearch(off.patternRangeAnalysis(),dataset.id(),null);on=on.withResearch(on.patternRangeAnalysis(),dataset.id(),null);store.save(on);store.saveShadowAnalysis(on.runId(),on.capitalSkipShadowAnalysis());
        mapper.writeValue(output.resolve("V2_SHADOW_OFF.json").toFile(),off);mapper.writeValue(output.resolve("V2_SHADOW_ON.json").toFile(),on);mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("shadow-analysis.json").toFile(),on.capitalSkipShadowAnalysis());
        Files.write(output.resolve("V2_CAPITAL_SKIPS_SHADOW.xlsx"),new PatternV2ExcelExporter().export(on));
        Files.writeString(output.resolve("parity.json"),"{\"passed\":true,\"entirePortfolio\":true,\"executedTradeIdsIdentical\":true,\"costs\":true,\"capitalSkipsCount\":true,\"priorBaselineParity\":true}");
    }
}
