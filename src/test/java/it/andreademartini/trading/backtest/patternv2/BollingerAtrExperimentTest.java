package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.detectors;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import it.andreademartini.trading.risk.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Explicit research replay; no historical provider or broker dependency. */
@EnabledIfSystemProperty(named="bollingerAtrExperiment",matches="true")
class BollingerAtrExperimentTest {
    @Test void baselineParityAndFilteredComparisonOnExactDataset() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);var store=new PatternV2Store(Path.of("data/pattern-v2"));
        var source=store.run(mapper.readTree(Path.of("outputs/capital-limits-evidence/V2_DYNAMIC_LIMITS.json").toFile()).path("runId").asText());var dataset=store.dataset(source.datasetId());
        var config=source.configuration().withName("V2_BASELINE_DYNAMIC_30").withBollingerAtr(false,20,new BigDecimal("2.0"),14,new BigDecimal("0.50"));
        assertFalse(config.patternRangeFilterEnabled());assertFalse(config.closeStrengthFilterEnabled());assertEquals(PatternV2Configuration.CapitalLimitMode.CURRENT_EQUITY_PERCENT,config.global().maxPositionValueMode());
        var service=new PatternV2Service(new PatternV1Properties(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);
        var parity=service.simulate(config,dataset.bars(),source.runId());var before=mapper.valueToTree(source.portfolio());var after=mapper.valueToTree(parity.portfolio());before.fieldNames().forEachRemaining(key->assertTrue(before.path(key).equals(after.path(key)),"Previous portfolio mismatch: "+key));assertEquals(source.costs(),parity.costs());assertEquals(source.analysis(),parity.analysis());
        var current=Files.readAllBytes(Path.of("data/pattern-v2/current.json"));var baseline=service.runOnSnapshot(config,dataset.bars());var filtered=service.replay(baseline.runId(),config.withName("V2_BOLLINGER_ATR_05").withBollingerAtr(true,20,new BigDecimal("2.0"),14,new BigDecimal("0.50")));
        var comparison=service.compare(List.of(baseline.runId(),filtered.runId()));var out=Path.of("outputs/bollinger-atr-evidence");Files.createDirectories(out);
        for(var run:List.of(baseline,filtered)){store.saveConfiguration(run.configuration(),false);mapper.writeValue(out.resolve(run.configuration().name()+".json").toFile(),run);Files.write(out.resolve(run.configuration().name()+".xlsx"),new PatternV2ExcelExporter().export(run));}
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.resolve("comparison.json").toFile(),comparison);assertArrayEquals(current,Files.readAllBytes(Path.of("data/pattern-v2/current.json")));
        for(var e:filtered.executions())assertEquals(BollingerAtrAnalysis.Status.ACCEPTED,e.bollingerAtr().status());
        long classified=filtered.bollingerAtrAnalysis().buckets().stream().mapToLong(g->g.signals()).sum();assertEquals(filtered.bollingerAtrSignals().stream().filter(s->s.distanceAtr()!=null).count(),classified);
        Files.writeString(out.resolve("parity.json"),"{\"entirePreviousPortfolioIdenticalWithFilterOff\":true,\"costsIdentical\":true,\"analysisIdentical\":true,\"currentConfigurationUnchanged\":true,\"exactDataset\":true,\"noBrokerDependencies\":true}");
        System.out.println(mapper.writeValueAsString(comparison));
    }
}
