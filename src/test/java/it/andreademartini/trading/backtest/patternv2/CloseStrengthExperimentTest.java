package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.detectors;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Runs only the explicitly requested C experiment; D is saved as an unexecuted preset. */
@EnabledIfSystemProperty(named="closeStrengthExperiment",matches="true")
class CloseStrengthExperimentTest {
    @Test void executeOnLatestFrozenBaselineSnapshot() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var output=Path.of("outputs/close-strength-evidence");var store=new PatternV2Store(Path.of("data/pattern-v2"));
        var baseline=mapper.readValue(output.resolve("baseline-before.json").toFile(),PatternV2Run.class);
        var range=mapper.readValue(output.resolve("range-before.json").toFile(),PatternV2Run.class);
        assertFalse(baseline.configuration().patternRangeFilterEnabled());assertFalse(baseline.configuration().closeStrengthFilterEnabled());
        var service=new PatternV2Service(new PatternV1Properties(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);
        for(var previous:List.of(baseline,range)) {
            var off=service.simulate(previous.configuration(),store.dataset(previous.datasetId()).bars());
            var before=mapper.valueToTree(previous);var after=mapper.valueToTree(off);
            for(var key:List.of("summary","equityCurve","symbolResults","directionResults","finalCapital","finalCash","finalAvailableCash","maxDrawdown","maxDrawdownPercent","cagr","averageGrossExposurePercent","timeInMarketPercent","priceSeries"))assertEquals(before.path("portfolio").path(key),after.path("portfolio").path(key),"OFF parity "+previous.runId()+": "+key);
            var oldTrades=before.path("portfolio").path("trades").deepCopy();var newTrades=after.path("portfolio").path("trades").deepCopy();
            for(var t:oldTrades)((ObjectNode)t).remove("tradeId");for(var t:newTrades)((ObjectNode)t).remove("tradeId");assertEquals(oldTrades,newTrades,"All existing trade fields preserved");
            assertEquals(before.path("costs"),after.path("costs"));assertEquals(before.path("rangeSignals"),after.path("rangeSignals"));
        }
        var c=baseline.configuration().withName("V2_CLOSE_STRENGTH_080").withRangeFilter(false,baseline.configuration().minimumPatternRangePct()).withCloseStrengthFilter(true,new BigDecimal(".80"));
        // The baseline threshold is already 2.50; disabled Range is otherwise unchanged.
        assertEquals(baseline.configuration().minimumPatternRangePct(),c.minimumPatternRangePct());
        var d=c.withName("V2_RANGE_25_CLOSE_080").withRangeFilter(true,new BigDecimal("2.50"));
        var savedC=service.savePreset(c);var savedD=service.savePreset(d);
        var actual=service.replay(baseline.runId(),c);assertEquals(baseline.runId(),actual.pairedRunId());assertEquals(baseline.datasetId(),actual.datasetId());
        mapper.writeValue(output.resolve("V2_CLOSE_STRENGTH_080.json").toFile(),actual);
        mapper.writeValue(output.resolve("V2_BASELINE.json").toFile(),baseline);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("comparison.json").toFile(),actual.closeStrengthAnalysis());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("presets.json").toFile(),List.of(savedC,savedD));
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("run-comparison.json").toFile(),service.compare(List.of(baseline.runId(),actual.runId())));
        Files.write(output.resolve("V2_CLOSE_STRENGTH_080.xlsx"),new PatternV2ExcelExporter().export(actual));
        Files.write(output.resolve("V2_BASELINE.xlsx"),new PatternV2ExcelExporter().export(baseline));
        Files.writeString(output.resolve("off-parity.json"),"{\"passed\":true,\"rangeOffAndOn\":true,\"allTradeFields\":true,\"equityCurve\":true,\"costs\":true,\"rangeSignals\":true}");
    }
}
