package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.config.TradingSystemProperties;

public record JoeRossParameters(
        int pivotStrength,
        double minimumTick,
        int point2BreakoutBufferTicks,
        int maxTteBars,
        int entryBufferTicks,
        int stopBufferTicks,
        boolean minimumRoomEnabled,
        double minimumRoomR,
        boolean allowAfterThreeBarsOnDoubleTriple,
        int doubleTripleToleranceTicks,
        boolean rejectGapOverEntry,
        boolean fallbackToRossHookBreakout) {

    public JoeRossParameters {
        if (pivotStrength < 1) {
            throw new IllegalArgumentException("pivotStrength deve essere almeno 1");
        }
        if (minimumTick <= 0) {
            throw new IllegalArgumentException("minimumTick deve essere positivo");
        }
        if (maxTteBars < 1) {
            throw new IllegalArgumentException("maxTteBars deve essere almeno 1");
        }
        if (point2BreakoutBufferTicks < 0) {
            throw new IllegalArgumentException(
                    "point2BreakoutBufferTicks non puo' essere negativo");
        }
        if (entryBufferTicks < 0 || stopBufferTicks < 0) {
            throw new IllegalArgumentException("I buffer in tick non possono essere negativi");
        }
        if (minimumRoomR < 0) {
            throw new IllegalArgumentException("minimumRoomR non puo' essere negativo");
        }
    }

    public static JoeRossParameters from(TradingSystemProperties.JoeRossProperties source) {
        if (!"R".equalsIgnoreCase(source.getTte().getMinimumRoom().getType())) {
            throw new IllegalArgumentException(
                    "La prima versione Joe Ross supporta minimum-room.type=R");
        }
        return new JoeRossParameters(
                source.getSwing().getPivotStrength(),
                source.getMinimumTick(),
                source.getPoint2BreakoutBufferTicks(),
                source.getTte().getMaxBarsAfterRossHook(),
                source.getTte().getEntryBufferTicks(),
                source.getTte().getStopBufferTicks(),
                source.getTte().getMinimumRoom().isEnabled(),
                source.getTte().getMinimumRoom().getValue(),
                source.getTte().isAllowAfterThreeBarsOnDoubleTriple(),
                source.getTte().getDoubleTripleToleranceTicks(),
                source.getTte().isRejectGapOverEntry(),
                source.getTte().isFallbackToRossHookBreakout());
    }
}
