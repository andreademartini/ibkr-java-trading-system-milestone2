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

/** Opt-in exact offline replay; never calls a data provider, broker or V1 store. */
@EnabledIfSystemProperty(named="marketRegimeExperiment",matches="true")
class MarketRegimeExperimentTest {
    @Test void currentElevenBarV2HasIdenticalEconomicsAndExport() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);var store=new PatternV2Store(Path.of("data/pattern-v2"));
        var source=store.run("d92eea67-e743-4be1-bfbe-6da19e129d20");var dataset=store.dataset(source.datasetId());var config=source.configuration().withMarketRegimeAnalysis(false);
        var current=Files.readAllBytes(Path.of("data/pattern-v2/current.json"));var original=Files.readAllBytes(Path.of("data/pattern-v2/runs",source.runId()+".json"));
        assertEquals(store.current().global().maxHoldingBars(),config.global().maxHoldingBars());assertEquals(11,config.global().maxHoldingBars());
        PatternV2RunComparison.requireSameTradingConfiguration(store.current(),config);
        var g=config.global();assertEquals(10000,g.initialCapital());assertEquals(30,g.maxOrderValuePercent());assertEquals(2,g.riskPercent());assertFalse(g.useLeverage());assertEquals(PatternV2Configuration.OrderValueMode.CURRENT_EQUITY_PERCENT,g.maxOrderValueMode());
        assertEquals(PatternV2Configuration.CapitalLimitMode.CURRENT_EQUITY_PERCENT,g.maxPositionValueMode());assertEquals(100,g.maxPositionValuePercent());assertEquals(PatternV2Configuration.CapitalLimitMode.CURRENT_EQUITY_PERCENT,g.maxLongNotionalMode());assertEquals(100,g.maxLongNotionalPercent());assertEquals(PatternV2Configuration.CapitalLimitMode.CURRENT_EQUITY_PERCENT,g.maxShortNotionalMode());assertEquals(100,g.maxShortNotionalPercent());
        assertFalse(config.patternRangeFilterEnabled());assertFalse(config.closeStrengthFilterEnabled());assertFalse(config.bollingerAtrFilterEnabled());
        var service=new PatternV2Service(new PatternV1Properties(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);
        var off=service.simulate(config,dataset.bars(),source.runId());assertEquals(mapper.valueToTree(source.portfolio()),mapper.valueToTree(off.portfolio()));assertEquals(source.costs(),off.costs());
        var onSameIdentity=service.simulate(config.withMarketRegimeAnalysis(true),dataset.bars(),source.runId());assertEquals(mapper.valueToTree(off.portfolio()),mapper.valueToTree(onSameIdentity.portfolio()));assertEquals(off.executions(),onSameIdentity.executions());assertEquals(off.costs(),onSameIdentity.costs());assertEquals(off.analysis(),onSameIdentity.analysis());
        var on=service.runOnSnapshot(config.withName("V2_MARKET_REGIME_ANALYSIS_11_BARS").withMarketRegimeAnalysis(true),dataset.bars());assertEquals(source.datasetId(),on.datasetId());assertEquals(off.portfolio().finalCapital(),on.portfolio().finalCapital());assertEquals(off.portfolio().summary(),on.portfolio().summary());assertEquals(off.portfolio().equityCurve(),on.portfolio().equityCurve());
        var out=Path.of("outputs/market-regime-evidence");Files.createDirectories(out);mapper.writeValue(out.resolve("V2_MARKET_REGIME_ON.json").toFile(),on);Files.write(out.resolve("V2_MARKET_REGIME_ON.xlsx"),new PatternV2ExcelExporter().export(on));
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.resolve("regime-summary.json").toFile(),on.marketRegimeAnalysis());
        var evidence=new LinkedHashMap<String,Object>();evidence.put("sourceRunId",source.runId());evidence.put("analysisRunId",on.runId());evidence.put("datasetId",dataset.id());evidence.put("maxHoldingBars",g.maxHoldingBars());evidence.put("entirePortfolioExactSameIdentity",true);evidence.put("executionsCostsAndOriginalAnalysisIdentical",true);evidence.put("persistedOnRunEquityAndSummaryIdentical",true);evidence.put("originalRunUnchanged",true);evidence.put("currentConfigurationUnchanged",true);evidence.put("indicatorReference","Official TA-Lib regression series and published expected ADX/DI values");evidence.put("finalEquity",on.portfolio().finalCapital());evidence.put("summary",on.portfolio().summary());evidence.put("cagr",on.portfolio().cagr());evidence.put("maxDrawdownPercent",on.portfolio().maxDrawdownPercent());
        assertArrayEquals(current,Files.readAllBytes(Path.of("data/pattern-v2/current.json")));assertArrayEquals(original,Files.readAllBytes(Path.of("data/pattern-v2/runs",source.runId()+".json")));
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.resolve("parity.json").toFile(),evidence);
        long closed=on.portfolio().trades().stream().filter(t->t.closed()).count();assertEquals(closed,on.marketRegimeAnalysis().overview().stream().mapToLong(r->r.metrics().trades()).sum());assertEquals(closed,on.marketRegimeAnalysis().adxBuckets().stream().mapToLong(r->r.metrics().trades()).sum());
    }
}
