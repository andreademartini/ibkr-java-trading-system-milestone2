package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.strategy.TradeDirection;

public record JoeRoss123Pattern(
        TradeDirection direction,
        SwingPoint point1,
        SwingPoint point2,
        SwingPoint point3) {
}
