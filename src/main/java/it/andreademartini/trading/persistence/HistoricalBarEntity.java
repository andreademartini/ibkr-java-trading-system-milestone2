package it.andreademartini.trading.persistence;

import it.andreademartini.trading.marketdata.HistoricalBar;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "historical_bar",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_historical_bar_series_label",
                columnNames = {"provider", "symbol", "timeframe", "bar_label"}),
        indexes = @Index(
                name = "idx_historical_bar_series_time",
                columnList = "provider,symbol,timeframe,bar_timestamp"))
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

    @Column(nullable = false)
    private double open;

    @Column(nullable = false)
    private double high;

    @Column(nullable = false)
    private double low;

    @Column(nullable = false)
    private double close;

    @Column(nullable = false)
    private long volume;

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
        updateFrom(bar, fetchedAt);
    }

    public void updateFrom(HistoricalBar bar, Instant fetchedAt) {
        this.timestamp = bar.timestamp();
        this.open = bar.open();
        this.high = bar.high();
        this.low = bar.low();
        this.close = bar.close();
        this.volume = bar.volume();
        this.fetchedAt = fetchedAt;
    }

    public String getBarLabel() {
        return barLabel;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public HistoricalBar toHistoricalBar(int index) {
        return new HistoricalBar(
                index,
                timestamp,
                barLabel,
                open,
                high,
                low,
                close,
                volume);
    }
}
