package it.andreademartini.trading.backtest.patternv1;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.risk.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PatternV1CommissionTest {
    private final PatternV1PortfolioEngine engine = new PatternV1PortfolioEngine(
            new PositionSizingService(new TradePricingService()), detectors());

    @ParameterizedTest
    @CsvSource({"0,100,0", "60,100,1", "200,100,1", "1000,100,5", "10,0.5,0.05", "1000,0.1,1"})
    void feeHonorsMinimumPerShareAndNotionalCap(long quantity, String price, String expected) {
        assertEquals(0, bd(expected).compareTo(PatternV1Commission.ibkr().charge(quantity, bd(price))));
    }

    @Test void defaultsChargeBothExecutionsAndReconcileCash() {
        var bars = new ArrayList<>(engulfing("AAPL")); bars.set(2, bar("AAPL",2,100,107,97,103));
        var request = new PatternV1Request(null,START,START.plusDays(100),BarInterval.DAILY,bd("10000"),null,null);
        var risk = new TradingSystemProperties.RiskProperties();
        var result = engine.run(request, configuration(), risk, Map.of("AAPL",bars));
        var trade = result.trades().getFirst();
        assertEquals(PatternV1Commission.Model.IBKR_US_FIXED, result.settings().commission().model());
        assertEquals(0, bd("0.03").compareTo(result.settings().slippagePercent()));
        assertEquals(60,trade.quantity());
        assertEquals(0, bd("2.8").compareTo(trade.entryCosts()));
        assertEquals(0, bd("5.564").compareTo(trade.costs()));
        assertEquals(0, bd("-125.564").compareTo(trade.netPnl()));
        assertEquals(0, bd("9874.436").compareTo(result.finalCash()));
        assertEquals(result.finalCash(),result.finalCapital());
        assertEquals(0,risk.getSlippagePercent());
    }

    @Test void costsLimitQuantityToRiskBudget() {
        var risk = new TradingSystemProperties.RiskProperties();
        var request = new PatternV1Request(null,START,START.plusDays(100),BarInterval.DAILY,
                bd("10000"),null,null,10000.0,100000L);
        var result = engine.run(request,configuration(),risk,Map.of("AAPL",engulfing("AAPL")));
        var trade = result.trades().getFirst();
        assertEquals(96,trade.quantity());
        assertEquals(0,bd("3.88").compareTo(trade.entryCosts()));
        assertTrue(bd("199.7024").compareTo(trade.desiredRiskAmount()) <= 0);
        assertTrue(bd("201.7618").compareTo(trade.desiredRiskAmount()) > 0);
        // An OPEN position has paid entry costs only.
        assertFalse(trade.closed());
        assertEquals(trade.entryCosts(),trade.costs());
        assertEquals(0,bd("92.12").compareTo(trade.netUnrealizedPnl()));
    }

    @Test void entryFeesAndSlippageCannotOverdrawCashIncludingShortCollateral() {
        for (String symbol : List.of("AAPL", "TSLA")) {
            var risk = new TradingSystemProperties.RiskProperties();risk.setRiskPerTradePercent(100);
            var bars = symbol.equals("AAPL") ? engulfing("AAPL")
                    : List.of(bar("TSLA",0,266,270,265.8,266.5),bar("TSLA",1,265,266,259,260));
            var request = new PatternV1Request(null,START,START.plusDays(100),BarInterval.DAILY,
                    bd("10000"),null,null,10000.0,100000L);
            var result = engine.run(request,configuration(),risk,Map.of(symbol,bars));
            assertTrue(result.finalAvailableCash().signum() >= 0);
            var trade = result.trades().getFirst();
            assertTrue(trade.orderValue().add(trade.entryCosts()).compareTo(trade.availableCashBeforeEntry()) <= 0);
            if (symbol.equals("AAPL")) assertEquals(99,trade.quantity());
        }
    }

    @Test void customParametersAndInvalidFees() {
        var custom = new PatternV1Commission(PatternV1Commission.Model.IBKR_US_FIXED,null,bd("0.01"),bd("2"),bd("0.5"));
        assertEquals(0,bd("3").compareTo(custom.charge(300,bd("100"))));
        assertEquals(0,bd("0.5").compareTo(custom.charge(1,bd("100"))));
        assertThrows(IllegalArgumentException.class, () -> PatternV1Commission.fixed(bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> new PatternV1Commission(null,null,bd("-0.005"),null,null));
        assertThrows(IllegalArgumentException.class, () -> new PatternV1Commission(null,null,null,bd("-1"),null));
        assertThrows(IllegalArgumentException.class, () -> new PatternV1Commission(null,null,null,null,bd("101")));
    }

    @ParameterizedTest
    @CsvSource({"AAPL,TARGET,10354.292", "AAPL,TIMEOUT,10294.31", "TSLA,STOP,9884.469",
            "TSLA,TARGET,10434.634", "TSLA,TIMEOUT,10104.535"})
    void longAndShortExitCostsReconcileForEveryExitReason(String symbol, String reason, String finalCash) {
        boolean isLong=symbol.equals("AAPL");
        var bars=new ArrayList<>(isLong?engulfing("AAPL").subList(0,2)
                :List.of(bar("TSLA",0,266,270,265.8,266.5)));
        int entry=bars.size();
        if (reason.equals("TIMEOUT")) {
            for (int i=entry;i<entry+20;i++) bars.add(isLong?bar(symbol,i,100,105.5,99,105):bar(symbol,i,265,267,259,260));
        } else if (isLong) bars.add(bar(symbol,entry,100,107,99,106));
        else bars.add(reason.equals("STOP")?bar(symbol,entry,265,271,264,270):bar(symbol,entry,265,266,244,245));
        var request=new PatternV1Request(null,START,START.plusDays(100),BarInterval.DAILY,bd("10000"),null,null);
        var result=engine.run(request,configuration(),new TradingSystemProperties.RiskProperties(),Map.of(symbol,bars));
        assertEquals(1,result.trades().size());
        assertEquals(reason,result.trades().getFirst().exitReason());
        assertEquals(0,bd(finalCash).compareTo(result.finalCash()));
        assertEquals(result.finalCash(),result.finalCapital());
        assertEquals(0,result.openPositions());
    }
}
