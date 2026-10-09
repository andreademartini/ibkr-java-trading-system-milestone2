package it.andreademartini.trading.strategy.joeross;

public record JoeRoss123TteStatistics(
        long point1Candidates,
        long point1AcceptedByBollinger,
        long point1RejectedByBollinger,
        long patterns123Found,
        long point3CorrectionsStarted,
        long tteBar1Candidates,
        long tteBar2Candidates,
        long tteBar3Candidates,
        long tteTriggeredBar1,
        long tteTriggeredBar2,
        long tteTriggeredBar3,
        long tteTriggered,
        long tteExpired,
        long tteMissedPoint2Breakout,
        long tteRejectedInsufficientRoom,
        long setupsInvalidated,
        long longTradesTriggered,
        long shortTradesTriggered) {
}
