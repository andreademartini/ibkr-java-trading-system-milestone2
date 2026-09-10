package it.andreademartini.trading.strategy.joeross;

public record JoeRossPatternStatistics(
        long patterns123Found,
        long point2Breakouts,
        long rossHooksFormed,
        long validTteCandidates,
        long tteTriggered,
        long tteExpired,
        long tteRejectedInsufficientRoom,
        long tteRejectedGap,
        long tteMissedRossHookBroken,
        long setupsInvalidated,
        long longTradesTriggered,
        long shortTradesTriggered) {
}
