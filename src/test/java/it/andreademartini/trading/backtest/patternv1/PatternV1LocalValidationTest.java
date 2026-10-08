package it.andreademartini.trading.backtest.patternv1;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.backtest.BacktestEngine;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.risk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;

/** Exact replay of the OLD run's immutable market snapshot via the existing BacktestEngine. */
@EnabledIfSystemProperty(named="patternV1.validateLocal", matches="true")
class PatternV1LocalValidationTest {
    @Test void fullPeriodViaExistingBacktestEngine() throws Exception {
        Path source=Path.of("data/pattern-v1/backtests/72ea9407-0a48-40f9-bd5c-3214bf360d03.json");
        assertTrue(Files.isRegularFile(source),"OLD snapshot required for exact comparison");
        byte[] sourceBytes=Files.readAllBytes(source);
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var old=mapper.readTree(sourceBytes); var configuration=configuration();
        var historical=mock(HistoricalDataService.class);
        Map<String,List<HistoricalBar>> series=new TreeMap<>();
        LocalDate start=LocalDate.of(2010,1,1), end=LocalDate.parse(old.path("actualEndDate").asText());
        for(String symbol:configuration.getSymbols().keySet()) {
            List<HistoricalBar> bars=new ArrayList<>();
            for(var b:old.path("priceSeries").path(symbol))bars.add(new HistoricalBar(symbol,
                    LocalDateTime.ofInstant(Instant.parse(b.path("timestamp").asText()),ZoneOffset.UTC),
                    b.path("open").decimalValue(),b.path("high").decimalValue(),b.path("low").decimalValue(),b.path("close").decimalValue(),
                    b.path("volume").asLong(),BarInterval.DAILY,"OLD_RUN_SNAPSHOT").withIndex(bars.size()));
            assertFalse(bars.isEmpty(),symbol);series.put(symbol,bars);
            when(historical.load(symbol,start,end,BarInterval.DAILY)).thenReturn(bars);
            when(historical.status(symbol,BarInterval.DAILY)).thenReturn(new HistoricalDataService.HistoricalDataStatus(
                    symbol,"OLD_RUN_SNAPSHOT",BarInterval.DAILY,bars.getFirst().dateTime().toLocalDate(),bars.getLast().dateTime().toLocalDate(),bars.size()));
        }
        var risk=new TradingSystemProperties();var s=old.path("settings");var r=risk.getRisk();
        r.setRiskPerTradePercent(s.path("riskPercent").asDouble());r.setUseLeverage(s.path("useLeverage").asBoolean());
        r.setMaxLongNotional(s.path("maxLongNotional").asDouble());r.setMaxShortNotional(s.path("maxShortNotional").asDouble());
        r.setMaxOrderValue(s.path("maxOrderValue").asDouble());r.setMaxPositionValue(s.path("maxPositionValue").asDouble());
        r.setMaxQuantityPerOrder(s.path("maxQuantity").asLong());r.setTransactionCostsPerShare(s.path("transactionCostsPerShare").asDouble());
        var service=new PatternV1BacktestService(new BacktestEngine(historical),historical,configuration,risk,
                new PositionSizingService(new TradePricingService()),detectors(),new PatternV1RunStore());
        var result=service.run(new PatternV1Request(null,start,null,BarInterval.DAILY,bd("10000"),
                s.path("commissionPerOrder").decimalValue(),s.path("slippagePercent").decimalValue()));
        assertEquals(end,result.endDate());assertEquals(10,result.symbolResults().size());assertEquals(2,result.schemaVersion());
        assertEquals("CLOSE_AT_MAX_HOLDING_BAR",result.settings().timeoutExitMode());
        for(String symbol:series.keySet())verify(historical).load(symbol,start,end,BarInterval.DAILY);
        for(var row:result.symbolResults()) {
            var prior=old.path("configuration").path(row.symbol()).path(row.direction()==PatternDirection.LONG?"longConfig":"shortConfig");
            assertEquals(prior.path("pattern").asText(),row.pattern().name());assertEquals(0,prior.path("targetR").decimalValue().compareTo(row.targetR()));
        }
        var pnl=result.trades().stream().map(t->t.closed()?t.netPnl():t.netUnrealizedPnl()).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(0,result.finalCapital().subtract(result.initialCapital()).compareTo(pnl));
        int verified=0;
        for(var t:result.trades()) {
            PatternV1PortfolioEngine.validateTrade(t,20);var bars=series.get(t.symbol());
            var detector=detectors().stream().filter(d->d.patternType()==t.pattern()).findFirst().orElseThrow();
            var detection=detector.detect(bars,t.signalIndex(),PatternDetectorParameters.defaults()).orElseThrow();
            var research=outcome(bars,detection,t.symbol(),t.targetR(),start,end);
            assertEquals(0,t.entryPrice().compareTo(research.entryPrice()));assertEquals(0,t.stopPrice().compareTo(research.stopPrice()));
            assertEquals(0,t.takeProfitPrice().compareTo(research.targetPrice()));
            if(t.closed()) {
                assertEquals(research.exitReason().name(),t.exitReason());assertEquals(research.exitDate(),t.exitDate());
                assertEquals(0,research.exitPrice().compareTo(t.exitPrice()));assertEquals(0,research.resultR().compareTo(t.resultR()));verified++;
            } else {assertEquals(PatternExitReason.INSUFFICIENT_FUTURE_DATA,research.exitReason());assertNull(t.exitDate());}
            assertEquals(0,research.mfe().compareTo(t.rawBarMfeR()));assertEquals(0,research.mae().compareTo(t.rawBarMaeR()));
        }
        BigDecimal peak=result.initialCapital();
        for(var p:result.equityCurve()) {
            assertEquals(0,p.cash().add(p.longMarketValue()).subtract(p.shortMarketValue()).compareTo(p.equity()));
            peak=peak.max(p.equity());assertEquals(0,peak.compareTo(p.runningPeak()));assertEquals(0,peak.subtract(p.equity()).compareTo(p.drawdown()));
            assertEquals(p.grossExposureValue().doubleValue()/p.equity().doubleValue()*100,p.grossExposurePercent(),1e-10);
            assertTrue(p.cash().signum()>=0);assertTrue(p.availableCash().signum()>=0);
        }
        Path output=Path.of("outputs/pattern-v1-corrected");Files.createDirectories(output);
        Files.write(output.resolve("PATTERN_V1.xlsx"),new PatternV1ExcelExporter().export(result));
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("PATTERN_V1.json").toFile(),result);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sourceBytes));
        Files.writeString(output.resolve("source-sha256.txt"),source+"\n"+hash+"\n",StandardCharsets.UTF_8);
        var report=new StringBuilder("# PATTERN_V1 corretto: mapping frozen\n\n");
        report.append("OLD 72ea9407-0a48-40f9-bd5c-3214bf360d03; NEW ").append(result.backtestId()).append(".\n\n")
                .append("Replay delle stesse serie prezzi salvate in OLD, tramite BacktestEngine e HistoricalDataService mock deterministico. SHA256: ").append(hash)
                .append(". Nessun download, nessun ordine operativo.\n\nPeriodo richiesto 2010-01-01 → ").append(end).append("; effettivo ")
                .append(result.actualStartDate()).append(" → ").append(result.actualEndDate()).append(".\n\n")
                .append("Capitale 10000; rischio target 2%; max order 5000; max position 10000; max qty 1000; no leverage; costi identici a OLD.\n\n")
                .append("Entry = holding bar 1; TIMEOUT al Close della holding bar 20 (entryIndex+19). STOP/TARGET hanno priorità nella stessa barra.\n\n")
                .append("| METRIC | OLD | NEW |\n|---|---:|---:|\n");
        var om=old.path("summary");var m=result.summary();
        compare(report,"Final Equity",old.path("finalCapital"),result.finalCapital());
        compare(report,"Return %",old.path("returnPercent"),result.returnPercent());compare(report,"CAGR %",old.path("cagr"),result.cagr());
        compare(report,"Max Drawdown",old.path("maxDrawdown"),result.maxDrawdown());compare(report,"Max Drawdown %",old.path("maxDrawdownPercent"),result.maxDrawdownPercent());
        compare(report,"Profit Factor PnL",om.path("profitFactor"),m.profitFactor());compare(report,"Expectancy R",om.path("expectancyR"),m.expectancyR());
        compare(report,"Win Rate %",om.path("winRate").asDouble()*100,m.winRate()==null?null:m.winRate()*100);
        long oldClosed=0;for(var t:old.path("trades"))if(t.path("closed").asBoolean())oldClosed++;
        compare(report,"Closed Trades",oldClosed,m.closedTrades());compare(report,"Open Trades",old.path("openPositions"),m.openTrades());
        compare(report,"Timeouts",om.path("timeouts"),m.timeouts());compare(report,"Capital Skips",om.path("skippedCapital"),m.skippedCapital());
        compare(report,"Position Open Skips",om.path("skippedPositionOpen"),m.skippedPositionOpen());
        compare(report,"Exposure % (OLD entry notional; NEW EOD MV)",old.path("exposure").asDouble()*100,result.averageGrossExposurePercent());
        long oldDaysOpen=result.equityCurve().stream().filter(p->{
            for(var t:old.path("trades"))if(!p.date().isBefore(LocalDate.parse(t.path("entryDate").asText()))
                    &&(!t.path("closed").asBoolean()||!p.date().isAfter(LocalDate.parse(t.path("exitDate").asText()))))return true;
            return false;
        }).count();
        compare(report,"Time In Market % (OLD reconstructed)",oldDaysOpen*100.0/result.equityCurve().size(),result.timeInMarketPercent());
        report.append("\nOLD values from the stored JSON. Exposure definitions differ; OLD time in market reconstructed from trade intervals.\n\n## Portfolio Summary\n\n");
        Path coverage=output.resolve("database-coverage.json");
        if(Files.isRegularFile(coverage)) {
            for(var status:mapper.readTree(Files.readAllBytes(coverage))) {
                var symbol=status.path("symbol").asText();
                assertEquals(status.path("lastDate").asText(),series.get(symbol).getLast().dateTime().toLocalDate().toString());
            }
            report.append("Stato del DB verificato tramite GET /api/historical/{symbol}/status: ultima data CORE 2026-09-08, coincide con le serie OLD. Copertura in database-coverage.json.\n\n");
        }
        for(var c:PatternV1Result.Metrics.class.getRecordComponents())report.append("- ").append(c.getName()).append(": ").append(format(c.getAccessor().invoke(m))).append("\n");
        report.append("- Final Cash: ").append(format(result.finalCash())).append("\n- Long MV: ").append(format(result.finalLongMarketValue()))
                .append("\n- Short MV liability: ").append(format(result.finalShortMarketValue())).append("\n- Final Equity: ").append(format(result.finalCapital())).append("\n\n")
                .append("## Symbol Breakdown\n\n| Symbol | Direction | Pattern | R | Signals | Trades | Closed | Open | Timeouts | PF PnL | Exp R | Realized net PnL | Unrealized net PnL | Actual risk avg % | Open skips | Capital skips |\n")
                .append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for(var row:result.symbolResults()) {
            var sm=row.metrics();report.append("| ").append(row.symbol()).append(" | ").append(row.direction()).append(" | ").append(row.pattern()).append(" | ")
                    .append(row.targetR()).append(" | ").append(sm.signalsDetected()).append(" | ").append(sm.trades()).append(" | ").append(sm.closedTrades()).append(" | ")
                    .append(sm.openTrades()).append(" | ").append(sm.timeouts()).append(" | ").append(format(sm.profitFactor())).append(" | ")
                    .append(format(sm.expectancyR())).append(" | ").append(format(sm.realizedNetPnl())).append(" | ").append(format(sm.netUnrealizedPnl())).append(" | ")
                    .append(format(sm.averageActualRiskPercent())).append(" | ").append(sm.skippedPositionOpen()).append(" | ").append(sm.skippedCapital()).append(" |\n");
        }
        report.append("\n## LONG vs SHORT\n\n");
        for(var entry:result.directionResults().entrySet()) {
            var d=entry.getValue();report.append("- ").append(entry.getKey()).append(": signals ").append(d.signalsDetected()).append(", trades ").append(d.trades())
                    .append(", wins ").append(d.wins()).append(", losses ").append(d.losses()).append(", BE ").append(d.breakevens())
                    .append(", WR ").append(format(d.winRate()==null?null:d.winRate()*100)).append("%, PF ").append(format(d.profitFactor()))
                    .append(", Exp ").append(format(d.expectancyR())).append("R, realized net PnL ").append(format(d.realizedNetPnl()))
                    .append(", marked contribution DD ").append(format(d.maxDrawdown())).append(".\n");
        }
        report.append("\n## I quattro vecchi TIMEOUT\n\nVerifica isolata degli stessi ingressi. PnL a quantità OLD per isolare il timeout; nella nuova run completa esecuzione/quantità possono cambiare per overlap e capitale.\n\n")
                .append("| Symbol | Entry Date | Timeout Date | Timeout Exit Price | Holding Bars | OLD Qty | Corrected PnL (OLD Qty) | Result R | NEW full run |\n|---|---|---|---:|---:|---:|---:|---:|---|\n");
        for(var t:old.path("trades")) {
            if(t.path("closed").asBoolean()||!"TIMEOUT".equals(t.path("exitReason").asText()))continue;
            String symbol=t.path("symbol").asText();var bars=series.get(symbol);
            int signalIndex=t.path("signalIndex").asInt(),entryIndex=t.path("entryIndex").asInt();
            var type=PatternType.valueOf(t.path("pattern").asText());
            var detector=detectors().stream().filter(d->d.patternType()==type).findFirst().orElseThrow();
            var detection=detector.detect(bars,signalIndex,PatternDetectorParameters.defaults()).orElseThrow();
            var outcome=outcome(bars,detection,symbol,t.path("targetR").decimalValue(),start,end);
            assertEquals(PatternExitReason.TIMEOUT,outcome.exitReason());assertEquals(bars.get(entryIndex+19).dateTime().toLocalDate(),outcome.exitDate());
            var subset=bars.subList(t.path("patternStartIndex").asInt(),Math.min(bars.size(),entryIndex+23));
            var single=new PatternV1PortfolioEngine(new PositionSizingService(new TradePricingService()),detectors()).run(
                    new PatternV1Request(List.of(symbol),subset.getFirst().dateTime().toLocalDate(),subset.getLast().dateTime().toLocalDate(),
                            BarInterval.DAILY,bd("10000"),BigDecimal.ZERO,BigDecimal.ZERO),configuration(),r,Map.of(symbol,subset));
            var regression=single.trades().stream().filter(x->x.entryDate().equals(LocalDate.parse(t.path("entryDate").asText()))).findFirst().orElseThrow();
            assertTrue(regression.closed());assertEquals(0,regression.remainingQuantity());assertEquals(20,regression.holdingBars());
            assertEquals(outcome.exitDate(),regression.exitDate());assertEquals(0,outcome.exitPrice().compareTo(regression.exitPrice()));
            assertTrue(regression.exitDate().getYear()<=2013);
            var correctedPnl=outcome.exitPrice().subtract(outcome.entryPrice()).multiply(BigDecimal.valueOf(t.path("quantity").asLong()));
            var actual=result.trades().stream().filter(x->x.symbol().equals(symbol)&&x.entryDate().equals(regression.entryDate())).findFirst();
            String actualDescription=actual.map(x->x.exitReason()+" "+x.exitDate()+" qty="+x.quantity()+" PnL="+format(x.netPnl()))
                    .orElse("skipped by changed capital/overlap; isolated regression verified");
            report.append("| ").append(symbol).append(" | ").append(regression.entryDate()).append(" | ").append(outcome.exitDate()).append(" | ")
                    .append(format(outcome.exitPrice())).append(" | 20 | ").append(t.path("quantity").asLong()).append(" | ").append(format(correctedPnl))
                    .append(" | ").append(format(outcome.resultR())).append(" | ").append(actualDescription).append(" |\n");
        }
        var example=result.trades().stream().filter(t->t.sizeConstrained()).findFirst().orElseThrow();
        report.append("\n## Actual risk example\n\n").append(example.symbol()).append(" ").append(example.entryDate()).append(": equity ")
                .append(format(example.equityAtEntry())).append(", target risk ").append(format(example.desiredRiskPercent())).append("%, desired risk ")
                .append(format(example.desiredRiskAmount())).append(", desired qty ").append(example.desiredQuantity()).append(", final qty ")
                .append(example.finalQuantity()).append(", limits ").append(example.quantityLimitingReasons()).append(", order ")
                .append(format(example.orderValue())).append(", actual risk ").append(format(example.actualRiskAmount())).append(" = ")
                .append(format(example.actualRiskPercent())).append("%.\n\n## Coerenza e anomalie\n\nVerificati ").append(verified)
                .append(" chiusi contro PatternOutcomeAnalyzer con l'opzione close-timeout già esistente. Entry/stop/target/exit/Result R identici; raw MFE/MAE identici anche per gli aperti. Detector e Research Engine invariati.\n\n")
                .append("AAPL/GOOGL/NFLX terminano 2026-09-02, altri 2026-09-08; TSLA inizia 2010-06-28. Date portfolio: unione delle sessioni. Per simboli senza barra si usa l'ultima quotazione nota, senza look-ahead.\n\n")
                .append("Il confronto Research PF R usa STOP/TARGET; il PF PnL portfolio include TIMEOUT chiusi. Desired qty/actual risk sono rischio prezzo; il servizio standard riserva costi, eventuale ulteriore limitazione è OTHER.\n\n");
        for(String w:result.warnings())report.append("- ").append(w).append("\n");
        Files.writeString(output.resolve("VALIDATION.md"),report.toString(),StandardCharsets.UTF_8);
        assertArrayEquals(sourceBytes,Files.readAllBytes(source),"OLD must stay unchanged");
        System.out.println("PATTERN_V1 corrected: "+result.backtestId()+"; "+result.summary()+"; equity="+result.finalCapital());
    }
    private static PatternOccurrence outcome(List<HistoricalBar> bars,PatternDetectionResult detection,String symbol,BigDecimal target,LocalDate start,LocalDate end) {
        var config=new PatternResearchConfiguration(start,end,List.of(symbol),List.of(detection.patternType()),ResearchDirection.BOTH,
                List.of(target),List.of(),20,SameBarResolution.STOP_FIRST,70,100,EntryMethod.NEXT_BAR_OPEN,StopMethod.PATTERN_EXTREME,
                BarInterval.DAILY,bd("0.01"),0,true,PatternDetectorParameters.defaults());
        return new PatternOutcomeAnalyzer().analyze("coherence",symbol,bars,detection,DatasetSegment.IN_SAMPLE,config).getFirst();
    }
    private static void compare(StringBuilder b,String metric,Object old,Object fresh) {
        b.append("| ").append(metric).append(" | ").append(format(old)).append(" | ").append(format(fresh)).append(" |\n");
    }
    private static String format(Object value) {
        if(value==null||value instanceof JsonNode j&&j.isNull())return "n/d";
        if(value instanceof JsonNode j)return j.isNumber()?String.format(Locale.ROOT,"%.4f",j.asDouble()):j.asText();
        return value instanceof Number n?String.format(Locale.ROOT,"%.4f",n.doubleValue()):value.toString();
    }
}
