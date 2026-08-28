package it.andreademartini.trading.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class HistoricalBar {

    private final int index;
    private final Instant timestamp;
    private final String label;
    private final String symbol;
    private final LocalDateTime dateTime;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final long volume;
    private final BarInterval interval;
    private final String provider;
    private final PriceAdjustment priceAdjustment;

    public HistoricalBar(
            int index,
            Instant timestamp,
            String label,
            double open,
            double high,
            double low,
            double close,
            long volume) {
        this(
                index,
                "",
                timestamp,
                label,
                toDateTime(timestamp),
                BigDecimal.valueOf(open),
                BigDecimal.valueOf(high),
                BigDecimal.valueOf(low),
                BigDecimal.valueOf(close),
                volume,
                BarInterval.DAILY,
                "",
                PriceAdjustment.RAW);
    }

    public HistoricalBar(
            String symbol,
            LocalDateTime dateTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            BarInterval interval,
            String provider) {
        this(
                symbol,
                dateTime,
                open,
                high,
                low,
                close,
                volume,
                interval,
                provider,
                PriceAdjustment.RAW);
    }

    public HistoricalBar(
            String symbol,
            LocalDateTime dateTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            BarInterval interval,
            String provider,
            PriceAdjustment priceAdjustment) {
        this(
                0,
                symbol,
                toInstant(dateTime),
                label(dateTime, interval),
                dateTime,
                open,
                high,
                low,
                close,
                volume,
                interval,
                provider,
                priceAdjustment);
    }

    public HistoricalBar(
            int index,
            String symbol,
            Instant timestamp,
            String label,
            LocalDateTime dateTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            BarInterval interval,
            String provider,
            PriceAdjustment priceAdjustment) {
        this.index = index;
        this.symbol = normalize(symbol);
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.dateTime = Objects.requireNonNull(dateTime, "dateTime");
        this.interval = interval == null ? BarInterval.DAILY : interval;
        this.label = label == null || label.isBlank()
                ? label(this.dateTime, this.interval)
                : label.trim();
        this.open = Objects.requireNonNull(open, "open");
        this.high = Objects.requireNonNull(high, "high");
        this.low = Objects.requireNonNull(low, "low");
        this.close = Objects.requireNonNull(close, "close");
        this.volume = volume;
        this.provider = normalize(provider);
        this.priceAdjustment = priceAdjustment == null
                ? PriceAdjustment.RAW
                : priceAdjustment;
    }

    public HistoricalBar withIndex(int index) {
        return new HistoricalBar(
                index,
                symbol,
                timestamp,
                label,
                dateTime,
                open,
                high,
                low,
                close,
                volume,
                interval,
                provider,
                priceAdjustment);
    }

    public int index() {
        return index;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String label() {
        return label;
    }

    public double open() {
        return open.doubleValue();
    }

    public double high() {
        return high.doubleValue();
    }

    public double low() {
        return low.doubleValue();
    }

    public double close() {
        return close.doubleValue();
    }

    public long volume() {
        return volume;
    }

    public String symbol() {
        return symbol;
    }

    public LocalDateTime dateTime() {
        return dateTime;
    }

    public BarInterval interval() {
        return interval;
    }

    public String provider() {
        return provider;
    }

    public PriceAdjustment priceAdjustment() {
        return priceAdjustment;
    }

    public int getIndex() {
        return index;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getLabel() {
        return label;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public long getVolume() {
        return volume;
    }

    public BarInterval getInterval() {
        return interval;
    }

    public String getProvider() {
        return provider;
    }

    public PriceAdjustment getPriceAdjustment() {
        return priceAdjustment;
    }

    private static LocalDateTime toDateTime(Instant timestamp) {
        return Objects.requireNonNull(timestamp, "timestamp")
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        return Objects.requireNonNull(dateTime, "dateTime")
                .toInstant(ZoneOffset.UTC);
    }

    private static String label(LocalDateTime dateTime, BarInterval interval) {
        if (interval == null || interval == BarInterval.DAILY) {
            return dateTime.toLocalDate().toString();
        }
        return dateTime.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HistoricalBar that)) {
            return false;
        }
        return index == that.index
                && volume == that.volume
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(label, that.label)
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(dateTime, that.dateTime)
                && Objects.equals(open, that.open)
                && Objects.equals(high, that.high)
                && Objects.equals(low, that.low)
                && Objects.equals(close, that.close)
                && interval == that.interval
                && Objects.equals(provider, that.provider)
                && priceAdjustment == that.priceAdjustment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                index,
                timestamp,
                label,
                symbol,
                dateTime,
                open,
                high,
                low,
                close,
                volume,
                interval,
                provider,
                priceAdjustment);
    }

    @Override
    public String toString() {
        return "HistoricalBar{"
                + "symbol='" + symbol + '\''
                + ", dateTime=" + dateTime
                + ", open=" + open
                + ", high=" + high
                + ", low=" + low
                + ", close=" + close
                + ", volume=" + volume
                + ", interval=" + interval
                + ", provider='" + provider + '\''
                + ", priceAdjustment=" + priceAdjustment
                + '}';
    }
}
