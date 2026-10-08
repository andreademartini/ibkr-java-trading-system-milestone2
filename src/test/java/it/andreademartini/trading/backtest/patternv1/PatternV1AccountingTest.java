package it.andreademartini.trading.backtest.patternv1;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1Result.QuantityLimitingReason.*;
import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.strategy.patternv1.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.math.BigDecimal;

class PatternV1AccountingTest {
    private final PositionSizingService standard=new PositionSizingService(new TradePricingService());
    private final PatternV1PortfolioEngine engine=new PatternV1PortfolioEngine(standard,detectors());
    private final TradingSystemProperties.RiskProperties risk=new TradingSystemProperties.RiskProperties();
    // Accounting fixtures use a fixed cap independently of the default percentage.
    private PatternV1Request request(){return new PatternV1Request(null,START,START.plusDays(100),BarInterval.DAILY,bd("10000"),BigDecimal.ZERO,BigDecimal.ZERO,7500.0,null);}
    private List<HistoricalBar> timeout(double close) {
        var bars=new ArrayList<>(engulfing("AAPL").subList(0,2));
        for(int i=2;i<22;i++)bars.add(bar("AAPL",i,100,105.5,99,i==21?close:100));
        return bars;
    }
    private PatternV1Result run(List<HistoricalBar> bars){return engine.run(request(),configuration(),risk,Map.of("AAPL",bars));}
    @Test void timeoutReleasesPositionAndCashAndAllowsNextSignal() {
        risk.setRiskPerTradePercent(100);risk.setMaxOrderValue(10000);
        var bars=timeout(101);
        bars.add(bar("AAPL",22,100,102,98.5,99));bars.add(bar("AAPL",23,98.8,102,98.6,101.5));
        bars.add(bar("AAPL",24,100,105,99,101));
        var result=run(bars);assertEquals(2,result.trades().size());
        var first=result.trades().getFirst();var second=result.trades().getLast();
        assertEquals("TIMEOUT",first.exitReason());assertTrue(first.closed());assertEquals(0,first.remainingQuantity());
        assertEquals(20,first.holdingBars());assertEquals(START.plusDays(21),first.exitDate());
        assertEquals(0,bd("10075").compareTo(second.availableCashBeforeEntry()));
        assertEquals(START.plusDays(24),second.entryDate());assertEquals(0,result.summary().skippedCapital());
        var timeoutDay=result.equityCurve().get(21);assertEquals(0,timeoutDay.openPositions());
        assertEquals(0,timeoutDay.cash().compareTo(timeoutDay.equity()));
        assertTrue(result.events().stream().noneMatch(e->e.date().isAfter(first.exitDate())&&e.eventType().equals("SIGNAL_SKIPPED_POSITION_ALREADY_OPEN")));
    }
    @Test void closedOnlyMetricsIncludeRealTimeoutReturnsAndSeparateBreakevens() {
        var win=new ArrayList<>(engulfing("AAPL"));win.set(2,bar("AAPL",2,100,107,99,106));
        var loss=new ArrayList<>(engulfing("AAPL"));loss.set(2,bar("AAPL",2,100,105,97,98));
        List<PatternV1Result.Trade> trades=List.of(run(win).trades().getFirst(),run(loss).trades().getFirst(),
                run(timeout(100)).trades().getFirst(),run(engulfing("AAPL")).trades().getFirst());
        var m=PatternV1MetricsCalculator.calculate(trades,List.of(),0);
        assertEquals(3,m.closedTrades());assertEquals(1,m.openTrades());assertEquals(1,m.wins());assertEquals(1,m.losses());assertEquals(1,m.breakevens());
        assertEquals(.5,m.winRate());assertEquals(3,m.profitFactor());assertEquals(3,m.profitFactorR());
        assertEquals(2.0/3,m.expectancyR(),1e-12);assertEquals(m.expectancyR(),m.averageR());assertEquals(m.expectancyR(),m.weightedExpectancyR());
        assertEquals(450,m.averageWinner());assertEquals(-150,m.averageLoser());assertEquals(3,m.averageWinnerR());assertEquals(-1,m.averageLoserR());
        assertEquals(300,m.realizedGrossPnl());assertEquals(75,m.unrealizedPnl());assertEquals(375,m.netPnl());
    }
    @Test void costBreakevenDoesNotCorruptPriceRExpectancy() {
        risk.setRiskPerTradePercent(3);
        var request=new PatternV1Request(null,START,START.plusDays(100),BarInterval.DAILY,bd("10000"),bd("37.5"),BigDecimal.ZERO,7500.0,null);
        var trade=engine.run(request,configuration(),risk,Map.of("AAPL",timeout(101))).trades().getFirst();
        assertEquals(0,trade.netPnl().signum());assertEquals(0,bd("0.5").compareTo(trade.resultR()));
        var m=PatternV1MetricsCalculator.calculate(List.of(trade),List.of(),0);
        assertEquals(1,m.breakevens());assertNull(m.winRate());assertEquals(.5,m.expectancyR());assertEquals(.5,m.weightedExpectancyR());
    }
    @Test void dailyExposureUsesCurrentMarketValueAndDrawdownUsesCurrentEquity() {
        var bars=new ArrayList<>(engulfing("AAPL"));
        bars.add(bar("AAPL",3,101,105,99,104));bars.add(bar("AAPL",4,104,105,99,99));
        var result=run(bars);var up=result.equityCurve().get(3);var down=result.equityCurve().get(4);
        assertEquals(0,bd("2500").compareTo(up.cash()));assertEquals(0,bd("7800").compareTo(up.longMarketValue()));
        assertEquals(0,bd("10300").compareTo(up.equity()));assertEquals(7800.0/10300*100,up.grossExposurePercent(),1e-10);
        assertEquals(0,bd("375").compareTo(down.drawdown()));assertEquals(375.0/10300*100,down.drawdownPercent(),1e-10);
        assertEquals(60,result.timeInMarketPercent());assertEquals(375,result.maxDrawdown());
        assertEquals(result.equityCurve().stream().mapToDouble(PatternV1Result.EquityPoint::grossExposurePercent).average().orElseThrow(),result.averageGrossExposurePercent(),1e-10);
        var mutated=new ArrayList<>(bars);mutated.set(4,bar("AAPL",4,104,1000,1,900));
        var changed=run(mutated);assertEquals(up,changed.equityCurve().get(3));
    }
    @Test void shortCashProceedsAreRestrictedAndEquitySubtractsLiability() {
        var bars=List.of(bar("TSLA",0,266,270,265.8,266.5),bar("TSLA",1,265,266,259,260));
        var result=engine.run(request(),configuration(),risk,Map.of("TSLA",bars));var t=result.trades().getFirst();
        assertFalse(t.closed());assertEquals(28,t.quantity());assertEquals(0,bd("7420").compareTo(t.orderValue()));
        assertEquals(0,bd("17420").compareTo(result.finalCash()));assertEquals(0,bd("2580").compareTo(result.finalAvailableCash()));
        assertEquals(0,bd("7280").compareTo(result.finalShortMarketValue()));assertEquals(0,bd("10140").compareTo(result.finalCapital()));
        assertEquals(0,bd("1.2").compareTo(t.rawBarMfeR()));assertEquals(0,bd("0.2").compareTo(t.executedMaeR()));
    }
    @Test void shortTimeoutClosesAndStopsMeasuringAfterTwentyBars() {
        var bars=new ArrayList<>(List.of(bar("TSLA",0,266,270,265.8,266.5)));
        for(int i=1;i<=20;i++)bars.add(bar("TSLA",i,265,267,259,260));
        bars.add(bar("TSLA",21,265,1000,1,900));
        var result=engine.run(request(),configuration(),risk,Map.of("TSLA",bars));var t=result.trades().getFirst();
        assertEquals("TIMEOUT",t.exitReason());assertTrue(t.closed());assertEquals(20,t.holdingBars());assertEquals(0,t.remainingQuantity());
        assertEquals(0,bd("1").compareTo(t.resultR()));assertEquals(0,bd("1.2").compareTo(t.rawBarMfeR()));
        assertEquals(0,bd("0.4").compareTo(t.rawBarMaeR()));assertEquals(0,result.finalShortMarketValue().signum());
        assertEquals(0,result.finalCapital().compareTo(result.finalCash()));
    }
    @Test void sizingAuditsDesiredQuantityThenEachLimitAndActualRisk() {
        var plan=new PatternV1Strategy(configuration().getSymbols(),detectors()).onNextOpen(
                new PatternV1Strategy(configuration().getSymbols(),detectors()).onClosedBar("AAPL",engulfing("AAPL"),1).getFirst(),2,START.plusDays(2),bd("100"));
        var calculator=new PatternV1PositionSizing(standard);
        var d=calculator.calculate(plan,bd("10000"),bd("10000"),risk,configuration(),BigDecimal.ZERO,BigDecimal.ZERO);
        assertEquals(100,d.desiredQuantity());assertEquals(50,d.quantity());assertEquals(MAX_ORDER_VALUE,d.reason());
        assertEquals(0,bd("200").compareTo(d.desiredRiskAmount()));assertEquals(0,bd("100").compareTo(d.actualRiskAmount()));assertEquals(0,bd("1").compareTo(d.actualRiskPercent()));
        risk.setMaxQuantityPerOrder(20);d=calculator.calculate(plan,bd("10000"),bd("10000"),risk,configuration(),BigDecimal.ZERO,BigDecimal.ZERO);
        assertEquals(20,d.quantity());assertEquals(MAX_QUANTITY,d.reason());
        risk.setMaxQuantityPerOrder(1000);risk.setMaxPositionValue(1500);
        d=calculator.calculate(plan,bd("10000"),bd("10000"),risk,configuration(),BigDecimal.ZERO,BigDecimal.ZERO);
        assertEquals(15,d.quantity());assertEquals(MAX_POSITION_VALUE,d.reason());
        d=calculator.calculate(plan,bd("10000"),bd("350"),risk,configuration(),bd("1"),bd("0.1"));
        assertEquals(3,d.quantity());assertEquals(INSUFFICIENT_CAPITAL,d.reason());assertTrue(d.reasons().contains(NO_LEVERAGE));assertTrue(d.sizeConstrained());
        assertTrue(d.availableAfter().signum()>=0);assertEquals(0,bd("0.06").compareTo(d.actualRiskPercent()));
        var config=configuration();config.setMinQuantity(4);
        d=calculator.calculate(plan,bd("10000"),bd("350"),risk,config,BigDecimal.ZERO,BigDecimal.ZERO);
        assertEquals(0,d.quantity());assertEquals(3,d.finalPossibleQuantity());assertTrue(d.minimumRejected());
        risk.setUseLeverage(true);d=calculator.calculate(plan,bd("10000"),bd("0"),risk,configuration(),BigDecimal.ZERO,BigDecimal.ZERO);
        assertEquals(15,d.quantity());assertFalse(d.capitalConstrained());
    }
    @Test void cashLimitIncludesEntryCostsAndReducesQuantityRatherThanSkippingAnAffordableOrder() {
        risk.setMaxOrderValue(10000);risk.setRiskPerTradePercent(100);
        var strategy=new PatternV1Strategy(configuration().getSymbols(),detectors());
        var plan=strategy.onNextOpen(strategy.onClosedBar("AAPL",engulfing("AAPL"),1).getFirst(),2,START.plusDays(2),bd("100"));
        var d=new PatternV1PositionSizing(standard).calculate(plan,bd("10000"),bd("10000"),risk,configuration(),bd("1"),bd("0.1"));
        assertEquals(99,d.quantity());assertEquals(INSUFFICIENT_CAPITAL,d.reason());assertTrue(d.availableAfter().signum()>0);
    }
    @Test void cagrUsesTheDisplayedEffectivePeriod() {
        assertEquals(7.177346253629313,PatternV1MetricsCalculator.cagr(10000,20000,10),1e-10);
        assertNull(PatternV1MetricsCalculator.cagr(10000,20000,0));
    }
    @ParameterizedTest @ValueSource(strings={"AAPL","QQQ","XLK","NVDA"})
    void oldTimeoutSymbolsCannotBecomeMultiYearOpenPositions(String symbol) {
        List<HistoricalBar> bars=new ArrayList<>();
        if(symbol.equals("QQQ")||symbol.equals("XLK"))bars.add(bar(symbol,0,100,100.5,98,100.4));
        else {
            bars.add(bar(symbol,0,100,101,98,99));
            bars.add(bar(symbol,1,98.5,101,98.2,symbol.equals("NVDA")?99.8:100.5));
        }
        int entryIndex=bars.size();
        for(int i=entryIndex;i<entryIndex+20;i++)bars.add(bar(symbol,i,100,105.5,99,105));
        // A distant final mark must never be applied to the first, already timed-out trade.
        var future=bar(symbol,entryIndex+20,100,1000,1,900);
        bars.add(new HistoricalBar(symbol,START.plusYears(16).atStartOfDay(),future.getOpen(),future.getHigh(),future.getLow(),future.getClose(),
                future.volume(),BarInterval.DAILY,"TEST"));
        var result=engine.run(new PatternV1Request(null,START,START.plusYears(16),BarInterval.DAILY,bd("10000"),BigDecimal.ZERO,BigDecimal.ZERO),
                configuration(),risk,Map.of(symbol,bars));
        var t=result.trades().getFirst();assertEquals("TIMEOUT",t.exitReason());assertTrue(t.closed());assertEquals(0,t.remainingQuantity());
        assertEquals(20,t.holdingBars());assertEquals(START.plusDays(entryIndex+19),t.exitDate());assertEquals(0,bd("105").compareTo(t.exitPrice()));
        assertEquals(0,bd("2.5").compareTo(t.resultR()));assertTrue(t.rawBarMfeR().doubleValue()<3);
        assertTrue(result.trades().stream().allMatch(x->x.closed()?x.holdingBars()<=20:x.holdingBars()<20));
    }
    @Test void capitalSkipEventsExposeRequiredCapitalAndPossibleQuantity() {
        risk.setMaxOrderValue(10000);risk.setRiskPerTradePercent(100);
        var config=configuration();
        Map<String,List<HistoricalBar>> series=new TreeMap<>();
        for(String symbol:List.of("AAPL","GOOGL","NFLX","NVDA","TSLA")) {
            config.getSymbols().put(symbol,config.getSymbols().get("AAPL"));
            series.put(symbol,engulfing(symbol));
        }
        var result=engine.run(request(),config,risk,series);
        var e=result.events().stream().filter(x->x.eventType().equals("SIGNAL_SKIPPED_CAPITAL")).findFirst().orElseThrow();
        assertEquals("NFLX",e.symbol());assertEquals(0,((BigDecimal)e.details().get("availableCapital")).signum());
        assertEquals(0L,e.details().get("finalPossibleQuantity"));assertTrue(((Number)e.details().get("desiredQuantity")).longValue()>0);
        assertTrue(((BigDecimal)e.details().get("requiredCapital")).signum()>0);assertEquals(3,result.summary().skippedCapital());
    }
    @Test void invalidClosedQuantityOrTimeoutOpenStateFailsFast() throws Exception {
        var t=run(timeout(105)).trades().getFirst();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var json=mapper.valueToTree(t);((com.fasterxml.jackson.databind.node.ObjectNode)json).put("remainingQuantity",1);
        var invalid=mapper.treeToValue(json,PatternV1Result.Trade.class);
        assertThrows(IllegalStateException.class,()->PatternV1PortfolioEngine.validateTrade(invalid,20));
        ((com.fasterxml.jackson.databind.node.ObjectNode)json).put("closed",false);
        var openTimeout=mapper.treeToValue(json,PatternV1Result.Trade.class);
        assertThrows(IllegalStateException.class,()->PatternV1PortfolioEngine.validateTrade(openTimeout,20));
    }
}
