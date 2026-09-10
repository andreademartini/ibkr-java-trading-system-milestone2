package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.strategy.TradeDirection;

import java.time.Instant;

public record JoeRossTradeSetup(
        TradeDirection direction,
        JoeRoss123Pattern pattern,
        int point2BreakoutBarIndex,
        Instant point2BreakoutTime,
        double point2BreakoutPrice,
        RossHook rossHook,
        JoeRossOrderCandidate tte,
        int triggerBarIndex,
        Instant triggerTime,
        double triggerPrice,
        JoeRossSetupState setupStatus) {
}
