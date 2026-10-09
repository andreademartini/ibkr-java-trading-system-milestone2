package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.config.TradingSystemProperties;

import java.util.Locale;

public record JoeRoss123TteParameters(
        int pivotStrength,
        double minimumTick,
        int maxTteBars,
        int entryBufferTicks,
        int stopBufferTicks,
        InitialStopMode initialStopMode,
        boolean minimumRoomEnabled,
        String minimumRoomType,
        double minimumRoomValue,
        boolean allowAfterThreeBarsOnDoubleTriple,
        int doubleTripleToleranceTicks,
        boolean fallbackToPoint2Breakout,
        BollingerPoint1FilterParameters bollingerPoint1Filter,
        Point2TargetSizingParameters point2TargetSizing,
        JoeRossTakeProfitParameters takeProfit) {

    public JoeRoss123TteParameters {
        if (pivotStrength < 1) {
            throw new IllegalArgumentException("pivotStrength deve essere almeno 1");
        }
        if (minimumTick <= 0) {
            throw new IllegalArgumentException("minimumTick deve essere positivo");
        }
        if (maxTteBars < 1 || maxTteBars > 3) {
            throw new IllegalArgumentException("maxTteBars deve essere compreso tra 1 e 3");
        }
        if (entryBufferTicks < 0 || stopBufferTicks < 0) {
            throw new IllegalArgumentException("I buffer in tick non possono essere negativi");
        }
        if (minimumRoomValue < 0) {
            throw new IllegalArgumentException("minimumRoomValue non puo' essere negativo");
        }
        initialStopMode = initialStopMode == null ? InitialStopMode.TTE_BAR : initialStopMode;
        minimumRoomType = minimumRoomType == null
                ? "R" : minimumRoomType.trim().toUpperCase(Locale.ROOT);
        if (!minimumRoomType.equals("R") && !minimumRoomType.equals("PRICE")) {
            throw new IllegalArgumentException("minimumRoomType supporta R oppure PRICE");
        }
        bollingerPoint1Filter = bollingerPoint1Filter == null
                ? BollingerPoint1FilterParameters.defaults()
                : bollingerPoint1Filter;
        point2TargetSizing = point2TargetSizing == null
                ? Point2TargetSizingParameters.defaults()
                : point2TargetSizing;
        takeProfit = takeProfit == null
                ? JoeRossTakeProfitParameters.defaults()
                : takeProfit;
    }

    /** Compatibility constructor for callers created before dedicated take profits. */
    public JoeRoss123TteParameters(
            int pivotStrength,
            double minimumTick,
            int maxTteBars,
            int entryBufferTicks,
            int stopBufferTicks,
            InitialStopMode initialStopMode,
            boolean minimumRoomEnabled,
            String minimumRoomType,
            double minimumRoomValue,
            boolean allowAfterThreeBarsOnDoubleTriple,
            int doubleTripleToleranceTicks,
            boolean fallbackToPoint2Breakout,
            BollingerPoint1FilterParameters bollingerPoint1Filter,
            Point2TargetSizingParameters point2TargetSizing) {
        this(pivotStrength, minimumTick, maxTteBars, entryBufferTicks,
                stopBufferTicks, initialStopMode, minimumRoomEnabled,
                minimumRoomType, minimumRoomValue,
                allowAfterThreeBarsOnDoubleTriple, doubleTripleToleranceTicks,
                fallbackToPoint2Breakout, bollingerPoint1Filter,
                point2TargetSizing, JoeRossTakeProfitParameters.defaults());
    }

    /** Compatibility constructor for callers created before the Point-1 filter. */
    public JoeRoss123TteParameters(
            int pivotStrength,
            double minimumTick,
            int maxTteBars,
            int entryBufferTicks,
            int stopBufferTicks,
            InitialStopMode initialStopMode,
            boolean minimumRoomEnabled,
            String minimumRoomType,
            double minimumRoomValue,
            boolean allowAfterThreeBarsOnDoubleTriple,
            int doubleTripleToleranceTicks,
            boolean fallbackToPoint2Breakout,
            BollingerPoint1FilterParameters bollingerPoint1Filter) {
        this(pivotStrength, minimumTick, maxTteBars, entryBufferTicks,
                stopBufferTicks, initialStopMode, minimumRoomEnabled,
                minimumRoomType, minimumRoomValue,
                allowAfterThreeBarsOnDoubleTriple, doubleTripleToleranceTicks,
                fallbackToPoint2Breakout, bollingerPoint1Filter,
                Point2TargetSizingParameters.defaults(),
                JoeRossTakeProfitParameters.defaults());
    }

    /** Compatibility constructor for callers created before the Point-1 filter. */
    public JoeRoss123TteParameters(
            int pivotStrength,
            double minimumTick,
            int maxTteBars,
            int entryBufferTicks,
            int stopBufferTicks,
            InitialStopMode initialStopMode,
            boolean minimumRoomEnabled,
            String minimumRoomType,
            double minimumRoomValue,
            boolean allowAfterThreeBarsOnDoubleTriple,
            int doubleTripleToleranceTicks,
            boolean fallbackToPoint2Breakout) {
        this(pivotStrength, minimumTick, maxTteBars, entryBufferTicks,
                stopBufferTicks, initialStopMode, minimumRoomEnabled,
                minimumRoomType, minimumRoomValue,
                allowAfterThreeBarsOnDoubleTriple, doubleTripleToleranceTicks,
                fallbackToPoint2Breakout,
                BollingerPoint1FilterParameters.defaults(),
                Point2TargetSizingParameters.defaults(),
                JoeRossTakeProfitParameters.defaults());
    }

    public static JoeRoss123TteParameters from(
            TradingSystemProperties.JoeRoss123TteProperties source) {
        return new JoeRoss123TteParameters(
                source.getSwing().getPivotStrength(),
                source.getMinimumTick(),
                source.getTte().getMaxBars(),
                source.getTte().getEntryBufferTicks(),
                source.getTte().getStopBufferTicks(),
                source.getTte().getInitialStopMode(),
                source.getTte().getMinimumRoomToPoint2().isEnabled(),
                source.getTte().getMinimumRoomToPoint2().getType(),
                source.getTte().getMinimumRoomToPoint2().getValue(),
                source.getTte().isAllowAfterThreeBarsOnDoubleTriple(),
                source.getTte().getDoubleTripleToleranceTicks(),
                source.getTte().isFallbackToPoint2Breakout(),
                BollingerPoint1FilterParameters.from(
                        source.getBollingerPoint1Filter()),
                Point2TargetSizingParameters.from(source.getPoint2TargetSizing()),
                JoeRossTakeProfitParameters.from(source.getTakeProfit()));
    }
}
