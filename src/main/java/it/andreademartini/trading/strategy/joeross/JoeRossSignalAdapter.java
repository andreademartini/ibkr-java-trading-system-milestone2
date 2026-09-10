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

/** Maps the price-action state machine output to backtest/PAPER signals. */
public final class JoeRossSignalAdapter {

    public static final String STRATEGY_NAME = "JOE_ROSS_123_RH_TTE";

    private JoeRossSignalAdapter() {
    }

    public static List<TradingSignal> toSignals(
            String symbol,
            HistoricalBar currentBar,
            JoeRossBarResult result,
            long displayQuantity) {
        List<TradingSignal> signals = new ArrayList<>();
        for (JoeRossOrderAction action : result.orderActions()) {
            if (action.type() == JoeRossOrderAction.Type.UPSERT_CANDIDATE) {
                JoeRossOrderCandidate candidate = action.candidate();
                signals.add(new TradingSignal(
                        UUID.randomUUID(),
                        symbol,
                        candidate.direction() == TradeDirection.LONG
                                ? SignalAction.BUY : SignalAction.SELL,
                        candidate.entryPrice(),
                        displayQuantity,
                        STRATEGY_NAME,
                        "TTE candidate bar " + candidate.tteBarNumber(),
                        currentBar.timestamp(),
                        orderDetails(candidate)));
            } else if (action.type()
                    == JoeRossOrderAction.Type.CANCEL_CANDIDATE) {
                signals.add(new TradingSignal(
                        UUID.randomUUID(),
                        symbol,
                        SignalAction.HOLD,
                        currentBar.close(),
                        0,
                        STRATEGY_NAME,
                        action.reason(),
                        currentBar.timestamp(),
                        StrategyOrderDetails.cancel(
                                currentBar.index(), action.reason())));
            }
        }
        return List.copyOf(signals);
    }

    private static StrategyOrderDetails orderDetails(
            JoeRossOrderCandidate candidate) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("strategy", STRATEGY_NAME);
        metadata.put("direction", candidate.direction().name());
        metadata.put("point1Price", String.valueOf(candidate.pattern().point1().price()));
        metadata.put("point1Time", candidate.pattern().point1().timestamp().toString());
        metadata.put("point1BarIndex", String.valueOf(candidate.pattern().point1().barIndex()));
        metadata.put("point2Price", String.valueOf(candidate.pattern().point2().price()));
        metadata.put("point2Time", candidate.pattern().point2().timestamp().toString());
        metadata.put("point2BarIndex", String.valueOf(candidate.pattern().point2().barIndex()));
        metadata.put("point3Price", String.valueOf(candidate.pattern().point3().price()));
        metadata.put("point3Time", candidate.pattern().point3().timestamp().toString());
        metadata.put("point3BarIndex", String.valueOf(candidate.pattern().point3().barIndex()));
        metadata.put("point2BreakoutPrice", String.valueOf(candidate.point2BreakoutPrice()));
        metadata.put("point2BreakoutTime", candidate.point2BreakoutTime().toString());
        metadata.put("point2BreakoutBarIndex", String.valueOf(candidate.point2BreakoutBarIndex()));
        metadata.put("rossHookPrice", String.valueOf(candidate.rossHookPrice()));
        metadata.put("rossHookTime", candidate.rossHook().timestamp().toString());
        metadata.put("rossHookBarIndex", String.valueOf(candidate.rossHook().barIndex()));
        metadata.put("tteBarNumber", String.valueOf(candidate.tteBarNumber()));
        metadata.put("tteBarIndex", String.valueOf(candidate.tteBar().index()));
        metadata.put("tteBarOpen", String.valueOf(candidate.tteBar().open()));
        metadata.put("tteBarHigh", String.valueOf(candidate.tteBar().high()));
        metadata.put("tteBarLow", String.valueOf(candidate.tteBar().low()));
        metadata.put("tteBarClose", String.valueOf(candidate.tteBar().close()));
        metadata.put("tteBarTime", candidate.tteBar().timestamp().toString());
        metadata.put("tteEntryPrice", String.valueOf(candidate.entryPrice()));
        metadata.put("initialStopPrice", String.valueOf(candidate.initialStopPrice()));
        metadata.put("roomToRossHook", String.valueOf(candidate.roomToRossHook()));
        metadata.put("initialRiskPerShare", String.valueOf(candidate.initialRiskPerShare()));
        metadata.put("setupStatus", "WAITING_TTE_TRIGGER");
        return new StrategyOrderDetails(
                candidate.entryPrice(),
                candidate.initialStopPrice(),
                candidate.rejectGapOverEntry(),
                false,
                candidate.tteBar().index(),
                metadata);
    }
}
