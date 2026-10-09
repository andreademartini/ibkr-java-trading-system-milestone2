package it.andreademartini.trading.backtest.patternv2;

import com.fasterxml.jackson.databind.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Explicit local research execution, never part of the ordinary test suite. */
@EnabledIfSystemProperty(named="patternRangeExperiment", matches="true")
class PatternRangeExperimentTest {
    @Test void executeOnFrozenLocalInputs() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var output=Path.of("outputs/pattern-range-evidence");
        var config=mapper.readValue(output.resolve("configuration-before.json").toFile(),PatternV2Configuration.class);
        Map<String,List<HistoricalBar>> bars=new TreeMap<>();
        for(var symbol:config.symbols()) if(symbol.enabled()) {
            var csv=Files.walk(Path.of("data/stooq"));
            Path file;
            try(csv){file=csv.filter(p->p.getFileName().toString().equals(symbol.symbol()+".US.csv")).findFirst().orElseThrow();}
            List<HistoricalBar> list=new ArrayList<>();
            for(var line:Files.readAllLines(file).subList(1,Files.readAllLines(file).size())) {
                var c=line.split(",");var date=LocalDate.parse(c[0]);if(date.isBefore(config.global().startDate()))continue;
                list.add(new HistoricalBar(symbol.symbol(),date.atStartOfDay(),new BigDecimal(c[1]),new BigDecimal(c[2]),new BigDecimal(c[3]),new BigDecimal(c[4]),Long.parseLong(c[5]),BarInterval.DAILY,"STOOQ"));
            }
            bars.put(symbol.symbol(),List.copyOf(list));
        }
        var json=mapper.valueToTree(config);
        ((com.fasterxml.jackson.databind.node.ObjectNode)json.get("global")).put("endDate",bars.values().stream().flatMap(List::stream).map(b->b.dateTime().toLocalDate()).max(Comparator.naturalOrder()).orElseThrow().toString());
        config=mapper.treeToValue(json,PatternV2Configuration.class);
        var detectors=List.<PatternDetector>of(new BullishEngulfingDetector(),new BearishEngulfingDetector(),new HammerDetector(),new ShootingStarDetector(),new BullishHaramiDetector(),new BearishHaramiDetector(),new PiercingPatternDetector(),new DarkCloudCoverDetector());
        var service=new PatternV2Service(new PatternV1Properties(),new TradingSystemProperties(),null,detectors,new PositionSizingService(new TradePricingService()),new PatternV2Store(Path.of("data/pattern-v2")));
        var off=service.simulate(config,bars);
        var previous=mapper.readTree(output.resolve("before-run.json").toFile());
        var after=mapper.readTree(mapper.writeValueAsBytes(off));
        for(var key:List.of("summary","equityCurve","symbolResults","directionResults","finalCapital","finalCash","finalAvailableCash","maxDrawdown","maxDrawdownPercent","cagr","averageGrossExposurePercent","timeInMarketPercent","priceSeries"))
            org.junit.jupiter.api.Assertions.assertEquals(previous.path("portfolio").path(key),after.path("portfolio").path(key),"OFF parity: "+key);
        var oldTrades=previous.path("portfolio").path("trades").deepCopy();var newTrades=after.path("portfolio").path("trades").deepCopy();
        for(var t:oldTrades)((com.fasterxml.jackson.databind.node.ObjectNode)t).remove("tradeId");
        for(var t:newTrades)((com.fasterxml.jackson.databind.node.ObjectNode)t).remove("tradeId");
        org.junit.jupiter.api.Assertions.assertEquals(oldTrades,newTrades,"Every pre-filter trade field preserved");
        org.junit.jupiter.api.Assertions.assertEquals(previous.path("costs"),after.path("costs"));
        var result=service.runOnSnapshot(config.withName("V2_PATTERN_RANGE_2_5").withRangeFilter(true,new BigDecimal("2.50")),bars);
        var store=new PatternV2Store(Path.of("data/pattern-v2"));var baseline=store.run(result.pairedRunId());
        mapper.writeValue(output.resolve("V2_PATTERN_RANGE_2_5.json").toFile(),result);
        mapper.writeValue(output.resolve("V2_BASELINE.json").toFile(),baseline);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("comparison.json").toFile(),result.patternRangeAnalysis());
        Files.write(output.resolve("V2_PATTERN_RANGE_2_5.xlsx"),new PatternV2ExcelExporter().export(result));
        Files.write(output.resolve("V2_BASELINE.xlsx"),new PatternV2ExcelExporter().export(baseline));
        Files.writeString(output.resolve("off-parity.json"),"{\"passed\":true,\"allTradeFields\":true,\"equityCurve\":true,\"costs\":true}");
    }
}
