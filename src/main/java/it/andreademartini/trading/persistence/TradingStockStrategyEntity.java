package it.andreademartini.trading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "trading_stock_strategy")
public class TradingStockStrategyEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private TradingStockEntity stock;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 64)
    private String strategy;

    @Column(nullable = false)
    private int fastPeriod;

    @Column(nullable = false)
    private int slowPeriod;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private double initialCapital;

    @Column(nullable = false)
    private double commissionPerOrder;

    @Column(nullable = false)
    private double slippagePercent;

    @Column(nullable = false, length = 16)
    private String timeframe;

    @Column(nullable = false, length = 16)
    private String outputSize;

    @Column(nullable = false)
    private int maxPoints;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TradingStockStrategyEntity() {
    }

    public TradingStockStrategyEntity(
            String id,
            String name,
            String provider,
            String strategy,
            int fastPeriod,
            int slowPeriod,
            long quantity,
            double initialCapital,
            double commissionPerOrder,
            double slippagePercent,
            String timeframe,
            String outputSize,
            int maxPoints,
            int sortOrder,
            Instant now) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.strategy = strategy;
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.quantity = quantity;
        this.initialCapital = initialCapital;
        this.commissionPerOrder = commissionPerOrder;
        this.slippagePercent = slippagePercent;
        this.timeframe = timeframe;
        this.outputSize = outputSize;
        this.maxPoints = maxPoints;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void setStock(TradingStockEntity stock) {
        this.stock = stock;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getProvider() {
        return provider;
    }

    public String getStrategy() {
        return strategy;
    }

    public int getFastPeriod() {
        return fastPeriod;
    }

    public int getSlowPeriod() {
        return slowPeriod;
    }

    public long getQuantity() {
        return quantity;
    }

    public double getInitialCapital() {
        return initialCapital;
    }

    public double getCommissionPerOrder() {
        return commissionPerOrder;
    }

    public double getSlippagePercent() {
        return slippagePercent;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public String getOutputSize() {
        return outputSize;
    }

    public int getMaxPoints() {
        return maxPoints;
    }
}
