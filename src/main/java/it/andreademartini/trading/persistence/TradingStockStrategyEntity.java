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

    @Column(length = 16)
    private String tradeDirection = "LONG_SHORT";

    @Column(nullable = false, length = 16)
    private String timeframe;

    @Column(nullable = false, length = 16)
    private String outputSize;

    @Column(nullable = false)
    private int maxPoints;

    @Column
    private Integer pivotStrength;

    @Column
    private Double minimumTick;

    @Column
    private Integer tteMaxBars;

    @Column
    private Integer entryBufferTicks;

    @Column
    private Integer stopBufferTicks;

    @Column
    private Boolean minimumRoomEnabled;

    @Column(length = 8)
    private String minimumRoomType;

    @Column
    private Double minimumRoomValue;

    @Column
    private Boolean allowDoubleTriple;

    @Column
    private Integer doubleTripleToleranceTicks;

    @Column
    private Boolean rejectGapOverEntry;

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
            String tradeDirection,
            String timeframe,
            String outputSize,
            int maxPoints,
            int pivotStrength,
            double minimumTick,
            int tteMaxBars,
            int entryBufferTicks,
            int stopBufferTicks,
            boolean minimumRoomEnabled,
            String minimumRoomType,
            double minimumRoomValue,
            boolean allowDoubleTriple,
            int doubleTripleToleranceTicks,
            boolean rejectGapOverEntry,
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
        this.tradeDirection = tradeDirection == null || tradeDirection.isBlank()
                ? "LONG_SHORT"
                : tradeDirection;
        this.timeframe = timeframe;
        this.outputSize = outputSize;
        this.maxPoints = maxPoints;
        this.pivotStrength = pivotStrength;
        this.minimumTick = minimumTick;
        this.tteMaxBars = tteMaxBars;
        this.entryBufferTicks = entryBufferTicks;
        this.stopBufferTicks = stopBufferTicks;
        this.minimumRoomEnabled = minimumRoomEnabled;
        this.minimumRoomType = minimumRoomType;
        this.minimumRoomValue = minimumRoomValue;
        this.allowDoubleTriple = allowDoubleTriple;
        this.doubleTripleToleranceTicks = doubleTripleToleranceTicks;
        this.rejectGapOverEntry = rejectGapOverEntry;
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

    public String getTradeDirection() {
        return tradeDirection == null || tradeDirection.isBlank()
                ? "LONG_SHORT"
                : tradeDirection;
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

    public int getPivotStrength() { return pivotStrength == null ? 2 : pivotStrength; }

    public double getMinimumTick() { return minimumTick == null ? 0.01 : minimumTick; }

    public int getTteMaxBars() { return tteMaxBars == null ? 3 : tteMaxBars; }

    public int getEntryBufferTicks() { return entryBufferTicks == null ? 1 : entryBufferTicks; }

    public int getStopBufferTicks() { return stopBufferTicks == null ? 1 : stopBufferTicks; }

    public boolean isMinimumRoomEnabled() {
        return minimumRoomEnabled == null || minimumRoomEnabled;
    }

    public String getMinimumRoomType() {
        return minimumRoomType == null || minimumRoomType.isBlank() ? "R" : minimumRoomType;
    }

    public double getMinimumRoomValue() {
        return minimumRoomValue == null ? 0.5 : minimumRoomValue;
    }

    public boolean isAllowDoubleTriple() {
        return allowDoubleTriple != null && allowDoubleTriple;
    }

    public int getDoubleTripleToleranceTicks() {
        return doubleTripleToleranceTicks == null ? 1 : doubleTripleToleranceTicks;
    }

    public boolean isRejectGapOverEntry() {
        return rejectGapOverEntry == null || rejectGapOverEntry;
    }
}
