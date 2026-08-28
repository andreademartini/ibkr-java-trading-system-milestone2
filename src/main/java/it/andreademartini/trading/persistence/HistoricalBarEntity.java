package it.andreademartini.trading.persistence;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "historical_bar",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_historical_bar_series_label",
                        columnNames = {"provider", "symbol", "timeframe", "bar_label"}),
                @UniqueConstraint(
                        name = "uk_historical_bar_symbol_datetime_interval_provider",
                        columnNames = {"symbol", "date_time", "bar_interval", "provider"})
        },
        indexes = {
                @Index(
                        name = "idx_historical_bar_series_time",
                        columnList = "provider,symbol,timeframe,bar_timestamp"),
                @Index(
                        name = "idx_historical_bar_symbol_interval_time",
                        columnList = "provider,symbol,bar_interval,date_time")
        })
public class HistoricalBarEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 16)
    private String timeframe;

    @Column(name = "bar_label", nullable = false, length = 32)
    private String barLabel;

    @Column(name = "bar_timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "date_time")
    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "bar_interval", length = 16)
    private BarInterval interval;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal open;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal high;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal low;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal close;

    @Column(nullable = false)
    private long volume;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_adjustment", length = 16)
    private PriceAdjustment priceAdjustment;

    @Column
    private Instant createdAt;

    @Column(nullable = false)
    private Instant fetchedAt;

    protected HistoricalBarEntity() {
    }

    public HistoricalBarEntity(
            String provider,
            String symbol,
            String timeframe,
            HistoricalBar bar,
            Instant fetchedAt) {
        this.provider = provider;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.barLabel = bar.label();
        updateFrom(new HistoricalBar(
                bar.index(),
                symbol,
                bar.timestamp(),
                bar.label(),
                bar.dateTime(),
                bar.getOpen(),
                bar.getHigh(),
                bar.getLow(),
                bar.getClose(),
                bar.volume(),
                BarInterval.fromTimeframe(timeframe),
                provider,
                bar.priceAdjustment()), fetchedAt);
    }

    public HistoricalBarEntity(HistoricalBar bar) {
        this.provider = bar.provider();
        this.symbol = bar.symbol();
        this.timeframe = bar.interval().timeframe();
        this.barLabel = bar.label();
        updateFrom(bar, Instant.now());
    }

    public void updateFrom(HistoricalBar bar, Instant fetchedAt) {
        if (bar.symbol() != null && !bar.symbol().isBlank()) {
            this.symbol = bar.symbol();
        }
        if (bar.provider() != null && !bar.provider().isBlank()) {
            this.provider = bar.provider();
        }
        this.interval = bar.interval();
        this.timeframe = bar.interval().timeframe();
        this.barLabel = bar.label();
        this.timestamp = bar.timestamp();
        this.dateTime = bar.dateTime();
        this.open = bar.getOpen();
        this.high = bar.getHigh();
        this.low = bar.getLow();
        this.close = bar.getClose();
        this.volume = bar.volume();
        this.priceAdjustment = bar.priceAdjustment();
        if (this.createdAt == null) {
            this.createdAt = fetchedAt;
        }
        this.fetchedAt = fetchedAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (fetchedAt == null) {
            fetchedAt = now;
        }
        if (dateTime == null && timestamp != null) {
            dateTime = timestamp.atZone(ZoneOffset.UTC).toLocalDateTime();
        }
        if (interval == null) {
            interval = BarInterval.fromTimeframe(timeframe);
        }
        if (priceAdjustment == null) {
            priceAdjustment = PriceAdjustment.RAW;
        }
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public String getBarLabel() {
        return barLabel;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public BarInterval getInterval() {
        return interval;
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

    public PriceAdjustment getPriceAdjustment() {
        return priceAdjustment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public HistoricalBar toHistoricalBar(int index) {
        LocalDateTime resolvedDateTime = dateTime == null
                ? timestamp.atZone(ZoneOffset.UTC).toLocalDateTime()
                : dateTime;
        BarInterval resolvedInterval = interval == null
                ? BarInterval.fromTimeframe(timeframe)
                : interval;
        return new HistoricalBar(
                index,
                symbol,
                timestamp,
                barLabel,
                resolvedDateTime,
                open,
                high,
                low,
                close,
                volume,
                resolvedInterval,
                provider,
                priceAdjustment == null ? PriceAdjustment.RAW : priceAdjustment);
    }
}
