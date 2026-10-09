import com.fasterxml.jackson.databind.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv1.*;
import it.andreademartini.trading.config.*;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.marketdata.stooq.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.strategy.patternv1.*;
import java.nio.file.*;
import java.math.*;
import java.util.*;

public class NvdaSplitCostCheck {
    static Map<String,Object> metrics(PatternV2Run run) {
        var executions=run.executions().stream().filter(e->e.trade().symbol().equals("NVDA")).toList();
        var trades=executions.stream().map(PatternV2Run.Execution::trade).filter(PatternV1Result.Trade::closed).toList();
        Map<String,Object> m=new LinkedHashMap<>();m.put("trades",trades.size());
        m.put("grossPnl",trades.stream().map(PatternV1Result.Trade::grossPnl).reduce(BigDecimal.ZERO,BigDecimal::add));
        m.put("netPnl",trades.stream().map(PatternV1Result.Trade::netPnl).reduce(BigDecimal.ZERO,BigDecimal::add));
        m.put("commissions",executions.stream().map(PatternV2Run.Execution::commission).reduce(BigDecimal.ZERO,BigDecimal::add));
        m.put("slippage",executions.stream().map(PatternV2Run.Execution::slippageCost).reduce(BigDecimal.ZERO,BigDecimal::add));
        m.put("portfolioFinalEquity",run.portfolio().finalCapital());return m;
    }
    public static void main(String[] args) throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var original=mapper.readValue(Path.of(args[0]).toFile(),PatternV2Run.class);var config=original.configuration();
        // Replay the exact saved OHLC snapshot, avoiding changes to the downloaded CSV since the source run.
        Map<String,List<HistoricalBar>> bars=new TreeMap<>();
        original.portfolio().priceSeries().forEach((symbol,values)->bars.put(symbol,values.stream().map(b->new HistoricalBar(symbol,
                java.time.Instant.parse(b.timestamp()).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime(),BigDecimal.valueOf(b.open()),BigDecimal.valueOf(b.high()),BigDecimal.valueOf(b.low()),BigDecimal.valueOf(b.close()),b.volume(),BarInterval.DAILY,"STOOQ",PriceAdjustment.RAW)).toList()));
        List<PatternDetector> detectors=List.of(new BullishEngulfingDetector(),new BearishEngulfingDetector(),new HammerDetector(),new ShootingStarDetector(),new BullishHaramiDetector(),new BearishHaramiDetector(),new PiercingPatternDetector(),new DarkCloudCoverDetector());
        var service=new PatternV2Service(new PatternV1Properties(),new TradingSystemProperties(),null,detectors,new PositionSizingService(new TradePricingService()),new PatternV2Store(Path.of("outputs/pattern-v2-evidence/unused-store")));
        var legacy=service.simulate(config.withSplitCosts(new PatternV2SplitCosts(List.of())),bars);
        var corrected=service.simulate(config.withSplitCosts(PatternV2SplitCosts.defaults()),bars);
        if(!mapper.valueToTree(original.portfolio().summary()).equals(mapper.valueToTree(legacy.portfolio().summary())))throw new IllegalStateException("Legacy replay differs from saved run");
        Map<String,Object> report=new LinkedHashMap<>();report.put("sourceRun",original.runId());report.put("period",config.global().startDate()+" / "+config.global().endDate());
        report.put("legacyReplayMatchesSavedRun",true);report.put("before",metrics(legacy));report.put("after",metrics(corrected));report.put("configuration",corrected.configuration());
        report.put("example2010",corrected.executions().stream().filter(e->e.trade().symbol().equals("NVDA")&&e.trade().entryDate().getYear()==2010).limit(3).toList());
        var path=Path.of("outputs/pattern-v2-evidence/nvda-split-cost-check.json");mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),report);
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("outputs/pattern-v2-evidence/NVDA_SPLIT_CORRECTED_RUN.json").toFile(),corrected);
        Files.write(Path.of("outputs/pattern-v2-evidence/PATTERN_V2_NVDA_SPLIT_CHECK.xlsx"),new PatternV2ExcelExporter().export(corrected));
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("before",metrics(legacy),"after",metrics(corrected))));
    }
}
