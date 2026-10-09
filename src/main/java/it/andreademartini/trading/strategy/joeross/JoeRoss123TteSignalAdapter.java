package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.SignalAction;
import it.andreademartini.trading.strategy.StrategyOrderDetails;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.TradingSignal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;

public final class JoeRoss123TteSignalAdapter {

    public static final String STRATEGY_NAME = "JOE_ROSS_123_TTE";

    private JoeRoss123TteSignalAdapter() {
    }

    public static List<TradingSignal> toSignals(
            String symbol,
            HistoricalBar currentBar,
            JoeRoss123TteBarResult result,
            long displayQuantity) {
        List<TradingSignal> signals = new ArrayList<>();
        for (JoeRoss123TteOrderAction action : result.orderActions()) {
            if (action.type() == JoeRoss123TteOrderAction.Type.UPSERT_CANDIDATE) {
                JoeRoss123TteCandidate candidate = action.candidate();
                signals.add(new TradingSignal(
                        UUID.randomUUID(),
                        symbol,
                        candidate.direction() == TradeDirection.LONG
                                ? SignalAction.BUY : SignalAction.SELL,
                        candidate.entryPrice(),
                        displayQuantity,
                        STRATEGY_NAME,
                        "TTE Point 3 candidate bar " + candidate.tteBarNumber(),
                        currentBar.timestamp(),
                        orderDetails(candidate)));
            } else if (action.type()
                    == JoeRoss123TteOrderAction.Type.CANCEL_CANDIDATE) {
                signals.add(new TradingSignal(
                        UUID.randomUUID(), symbol, SignalAction.HOLD,
                        currentBar.close(), 0, STRATEGY_NAME, action.reason(),
                        currentBar.timestamp(),
                        StrategyOrderDetails.cancel(currentBar.index(), action.reason())));
            }
        }
        return List.copyOf(signals);
    }

    private static StrategyOrderDetails orderDetails(
            JoeRoss123TteCandidate candidate) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("strategy", STRATEGY_NAME);
        metadata.put("direction", candidate.direction().name());
        point(metadata, "point1", candidate.point1());
        point(metadata, "point2", candidate.point2());
        point(metadata, "point3", candidate.point3AtCandidate());
        metadata.put("tteBarNumber", String.valueOf(candidate.tteBarNumber()));
        metadata.put("tteBarIndex", String.valueOf(candidate.tteBar().index()));
        metadata.put("tteBarOpen", String.valueOf(candidate.tteBar().open()));
        metadata.put("tteBarHigh", String.valueOf(candidate.tteBar().high()));
        metadata.put("tteBarLow", String.valueOf(candidate.tteBar().low()));
        metadata.put("tteBarClose", String.valueOf(candidate.tteBar().close()));
        metadata.put("tteBarTime", candidate.tteBar().timestamp().toString());
        metadata.put("point3Open", String.valueOf(candidate.tteBar().open()));
        metadata.put("point3High", String.valueOf(candidate.tteBar().high()));
        metadata.put("point3Low", String.valueOf(candidate.tteBar().low()));
        metadata.put("point3Close", String.valueOf(candidate.tteBar().close()));
        metadata.put("point3Date", candidate.tteBar().timestamp().toString());
        metadata.put("tteEntryCandidate", String.valueOf(candidate.entryPrice()));
        metadata.put("entryCandidate", String.valueOf(candidate.entryPrice()));
        metadata.put("tteEntryPrice", String.valueOf(candidate.entryPrice()));
        metadata.put("entryTickBuffer", String.valueOf(candidate.entryBufferTicks()));
        metadata.put("stopTickBuffer", String.valueOf(candidate.stopBufferTicks()));
        metadata.put("initialStopPrice", String.valueOf(candidate.initialStopPrice()));
        metadata.put("roomToPoint2", String.valueOf(candidate.roomToPoint2()));
        metadata.put("delta", String.valueOf(candidate.point2Delta()));
        metadata.put("point2Delta", String.valueOf(candidate.point2Delta()));
        metadata.put("deltaFormula", candidate.direction() == TradeDirection.LONG
                ? "P2_HIGH - P3_HIGH" : "P3_LOW - P2_LOW");
        metadata.put("point3RangeRiskPerShare", BigDecimal.valueOf(
                        candidate.tteBar().high())
                .subtract(BigDecimal.valueOf(candidate.tteBar().low()))
                .stripTrailingZeros().toPlainString());
        metadata.put("initialRiskPerShare", String.valueOf(candidate.initialRiskPerShare()));
        metadata.put("setupStatus", "WAITING_TTE_TRIGGER");
        metadata.put("point2TargetSizingEnabled",
                String.valueOf(candidate.point2TargetSizing().enabled()));
        metadata.put("targetProfitPercent",
                String.valueOf(candidate.point2TargetSizing().targetProfitPercent()));
        metadata.put("targetSizingPercent",
                String.valueOf(candidate.point2TargetSizing().targetProfitPercent()));
        metadata.put("minimumPoint2Distance",
                String.valueOf(candidate.point2TargetSizing().minimumPoint2Distance()));
        metadata.put("tp1Source", "POINT2");
        metadata.put("tp1Reason", "JOE_ROSS_POINT2_TARGET");
        metadata.put("tp1Percent", String.valueOf(
                candidate.takeProfit().tp1PositionPercent()));
        metadata.put("tp2Percent", String.valueOf(
                candidate.takeProfit().tp2PositionPercent()));
        metadata.put("tp3Percent", String.valueOf(
                candidate.takeProfit().tp3PositionPercent()));
        BollingerPoint1Check check = candidate.bollingerPoint1Check();
        if (check != null) {
            metadata.put("bollingerMiddle", value(check.bollingerMiddle()));
            metadata.put("bollingerUpper", value(check.bollingerUpper()));
            metadata.put("bollingerLower", value(check.bollingerLower()));
            metadata.put("bollingerPeriod", String.valueOf(check.bollingerPeriod()));
            metadata.put("bollingerStdDevMultiplier",
                    value(check.bollingerStdDevMultiplier()));
            metadata.put("bollingerPoint1Valid",
                    String.valueOf(check.bollingerPoint1Valid()));
            metadata.put("bollingerDistance", value(check.bollingerDistance()));
            metadata.put("bollingerPoint1PriceMode", check.point1PriceMode().name());
        }
        return new StrategyOrderDetails(
                candidate.entryPrice(),
                candidate.initialStopPrice(),
                false,
                false,
                candidate.tteBar().index(),
                metadata);
    }

    private static void point(
            Map<String, String> metadata,
            String prefix,
            SwingPoint point) {
        metadata.put(prefix + "Price", String.valueOf(point.price()));
        metadata.put(prefix + "Time", point.timestamp().toString());
        metadata.put(prefix + "Timestamp", point.timestamp().toString());
        metadata.put(prefix + "BarIndex", String.valueOf(point.barIndex()));
        metadata.put(prefix + "Date", point.timestamp().toString());
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
