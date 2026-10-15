package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.detectors;
import com.fasterxml.jackson.databind.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Exact offline portfolio replay. No Spring context, providers, broker or paper scheduler. */
@EnabledIfSystemProperty(named="marketRegimeFilterExperiment",matches="true")
class MarketRegimeFilterExperimentTest {
    @Test void compareCurrentThirteenBarPortfolioAndExport() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var store=new PatternV2Store(Path.of("data/pattern-v2"));
        var source=store.run("01919b0e-abc3-4a60-8a44-083f6955cd66");var dataset=store.dataset(source.datasetId());
        var config=store.current().withMarketRegimeFilter(false,14,25);assertEquals(13,config.global().maxHoldingBars());
        PatternV2RunComparison.requireSameTradingConfiguration(source.configuration(),config);
        var current=Files.readAllBytes(Path.of("data/pattern-v2/current.json"));var original=Files.readAllBytes(Path.of("data/pattern-v2/runs",source.runId()+".json"));
        var svc=new PatternV2Service(new PatternV1Properties(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);
        var parity=svc.simulate(config,dataset.bars(),source.runId());
        assertEquals(mapper.valueToTree(source.portfolio()),mapper.valueToTree(parity.portfolio()),"OFF must reproduce entire saved portfolio exactly");assertEquals(source.costs(),parity.costs());
        var filtered=svc.runOnSnapshot(config.withMarketRegimeFilter(true,14,25),dataset.bars());
        var baseline=store.run(filtered.pairedRunId());assertFalse(baseline.configuration().marketRegimeFilterEnabled());assertEquals(dataset.id(),filtered.datasetId());
        assertEquals(parity.portfolio().summary(),baseline.portfolio().summary());assertEquals(parity.portfolio().equityCurve(),baseline.portfolio().equityCurve());
        var sum=filtered.marketRegimeFilterAnalysis().summary();assertEquals(sum.signalsEvaluated(),sum.signalsAccepted()+sum.rejectedWithTrend()+sum.rejectedInsufficientData());
        assertEquals(filtered.portfolio().summary().trades(),filtered.marketRegimeFilterAnalysis().acceptedGroups().stream().mapToLong(g->g.metrics().trades()).sum());
        assertArrayEquals(current,Files.readAllBytes(Path.of("data/pattern-v2/current.json")));assertArrayEquals(original,Files.readAllBytes(Path.of("data/pattern-v2/runs",source.runId()+".json")));
        var out=Path.of("outputs/market-regime-filter-evidence");Files.createDirectories(out);
        Files.write(out.resolve("V2_BASELINE_13_BARS.xlsx"),new PatternV2ExcelExporter().export(baseline));Files.write(out.resolve("V2_FILTERED_ADX14_25_13_BARS.xlsx"),new PatternV2ExcelExporter().export(filtered));
        var report=new LinkedHashMap<String,Object>();report.put("sourceRunId",source.runId());report.put("baselineRunId",baseline.runId());report.put("filteredRunId",filtered.runId());report.put("datasetId",dataset.id());report.put("configuration",filtered.configuration());report.put("exactOffPortfolioParity",true);report.put("currentAndHistoricalRunUnchanged",true);report.put("filterSummary",sum);report.put("comparison",filtered.marketRegimeFilterAnalysis().comparison());report.put("periodsAndYears",filtered.marketRegimeFilterAnalysis().periods());report.put("symbols",filtered.marketRegimeFilterAnalysis().symbols());
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.resolve("comparison.json").toFile(),report);
        System.out.println("MARKET_REGIME_FILTER_EXPERIMENT "+mapper.writeValueAsString(filtered.marketRegimeFilterAnalysis().comparison()));System.out.println("FILTER_COUNTS "+mapper.writeValueAsString(sum));
    }
}
