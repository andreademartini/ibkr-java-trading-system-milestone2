package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.config.TradingSystemProperties;

public record JoeRossTakeProfitParameters(
        double tp1PositionPercent,
        double tp2PositionPercent,
        double tp3PositionPercent) {

    public JoeRossTakeProfitParameters {
        if (tp1PositionPercent < 0 || tp2PositionPercent < 0
                || tp3PositionPercent < 0) {
            throw new IllegalArgumentException(
                    "Le percentuali dei take profit non possono essere negative");
        }
        double total = tp1PositionPercent + tp2PositionPercent
                + tp3PositionPercent;
        if (Math.abs(total - 100.0) > 0.000001) {
            throw new IllegalArgumentException(
                    "Le percentuali dei take profit Joe Ross devono sommare a 100");
        }
    }

    public static JoeRossTakeProfitParameters defaults() {
        return new JoeRossTakeProfitParameters(60.0, 20.0, 20.0);
    }

    public static JoeRossTakeProfitParameters from(
            TradingSystemProperties.JoeRossTakeProfitProperties source) {
        return new JoeRossTakeProfitParameters(
                source.getTp1PercentPosition(),
                source.getTp2PercentPosition(),
                source.getTp3PercentPosition());
    }
}
