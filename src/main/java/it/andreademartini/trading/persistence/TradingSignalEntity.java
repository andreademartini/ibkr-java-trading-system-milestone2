package it.andreademartini.trading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "trading_signal")
public class TradingSignalEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column
    private Double entryStopPrice;

    @Column
    private Double initialStopPrice;

    @Column
    private Boolean rejectGapOverEntry;

    @Column
    private Integer activeAfterBarIndex;

    @Column(length = 8000)
    private String strategyMetadataJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected TradingSignalEntity() {
    }

    public TradingSignalEntity(
            String id,
            String symbol,
            String action,
            double price,
            long quantity,
            String source,
            String reason,
            Double entryStopPrice,
            Double initialStopPrice,
            Boolean rejectGapOverEntry,
            Integer activeAfterBarIndex,
            String strategyMetadataJson,
            Instant createdAt) {
        this.id = id;
        this.symbol = symbol;
        this.action = action;
        this.price = price;
        this.quantity = quantity;
        this.source = source;
        this.reason = reason;
        this.entryStopPrice = entryStopPrice;
        this.initialStopPrice = initialStopPrice;
        this.rejectGapOverEntry = rejectGapOverEntry;
        this.activeAfterBarIndex = activeAfterBarIndex;
        this.strategyMetadataJson = strategyMetadataJson;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public Double getEntryStopPrice() {
        return entryStopPrice;
    }

    public Double getInitialStopPrice() {
        return initialStopPrice;
    }

    public Boolean getRejectGapOverEntry() {
        return rejectGapOverEntry;
    }

    public Integer getActiveAfterBarIndex() {
        return activeAfterBarIndex;
    }

    public String getStrategyMetadataJson() {
        return strategyMetadataJson;
    }
}
