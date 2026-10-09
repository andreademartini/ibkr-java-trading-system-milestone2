package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.TradeDirection;

public record JoeRoss123TteCandidate(
        TradeDirection direction,
        SwingPoint point1,
        SwingPoint point2,
        SwingPoint point3AtCandidate,
        int tteBarNumber,
        HistoricalBar tteBar,
        double entryPrice,
        double initialStopPrice,
        int entryBufferTicks,
        int stopBufferTicks,
        double roomToPoint2,
        double point2Delta,
        double initialRiskPerShare,
        BollingerPoint1Check bollingerPoint1Check,
        Point2TargetSizingParameters point2TargetSizing,
        JoeRossTakeProfitParameters takeProfit) {
}
