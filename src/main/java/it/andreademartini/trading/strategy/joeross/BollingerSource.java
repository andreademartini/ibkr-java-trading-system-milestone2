package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;

import java.math.BigDecimal;

public enum BollingerSource {
    OPEN,
    HIGH,
    LOW,
    CLOSE;

    public BigDecimal value(HistoricalBar bar) {
        return switch (this) {
            case OPEN -> bar.getOpen();
            case HIGH -> bar.getHigh();
            case LOW -> bar.getLow();
            case CLOSE -> bar.getClose();
        };
    }
}
