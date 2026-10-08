package it.andreademartini.trading.backtest.patternv1;

import it.andreademartini.trading.marketdata.BarInterval;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Target, pattern and direction cannot be overridden by a backtest request. */
public record PatternV1Request(List<String> symbols, LocalDate startDate, LocalDate endDate,
                               BarInterval timeframe, BigDecimal initialCapital,
                               BigDecimal commissionPerOrder, BigDecimal slippagePercent,
                               Double maxOrderValue, Long maxQuantity, PatternV1Commission commission) {
    public PatternV1Request(List<String> symbols, LocalDate startDate, LocalDate endDate,
                            BarInterval timeframe, BigDecimal initialCapital,
                            BigDecimal commissionPerOrder, BigDecimal slippagePercent,
                            Double maxOrderValue, Long maxQuantity) {
        this(symbols, startDate, endDate, timeframe, initialCapital, commissionPerOrder,
                slippagePercent, maxOrderValue, maxQuantity, null);
    }
    public PatternV1Request(List<String> symbols, LocalDate startDate, LocalDate endDate,
                            BarInterval timeframe, BigDecimal initialCapital,
                            BigDecimal commissionPerOrder, BigDecimal slippagePercent) {
        this(symbols, startDate, endDate, timeframe, initialCapital, commissionPerOrder,
                slippagePercent, null, null);
    }
}
