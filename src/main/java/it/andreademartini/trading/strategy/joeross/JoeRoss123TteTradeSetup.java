package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.strategy.TradeDirection;

import java.time.Instant;

public record JoeRoss123TteTradeSetup(
        TradeDirection direction,
        SwingPoint point1,
        SwingPoint point2,
        SwingPoint point3AtEntry,
        JoeRoss123TteCandidate tte,
        int triggerBarIndex,
        Instant triggerTime,
        double triggerPrice,
        JoeRoss123TteState setupStatus) {
}
