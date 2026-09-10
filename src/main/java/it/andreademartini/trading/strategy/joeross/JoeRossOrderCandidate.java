package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.TradeDirection;

import java.time.Instant;

public record JoeRossOrderCandidate(
        TradeDirection direction,
        int tteBarNumber,
        HistoricalBar tteBar,
        double entryPrice,
        double initialStopPrice,
        double rossHookPrice,
        double roomToRossHook,
        double initialRiskPerShare,
        JoeRoss123Pattern pattern,
        int point2BreakoutBarIndex,
        Instant point2BreakoutTime,
        double point2BreakoutPrice,
        RossHook rossHook,
        boolean rejectGapOverEntry) {
}
