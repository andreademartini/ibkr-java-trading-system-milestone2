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

    @Column(length = 16)
    private String initialStopMode;

    @Column
    private Boolean fallbackToPoint2Breakout;

    @Column
    private Boolean bollingerPoint1FilterEnabled;

    @Column
    private Integer bollingerPeriod;

    @Column
    private Double bollingerStandardDeviation;

    @Column(length = 16)
    private String bollingerSource;

    @Column(length = 16)
    private String point1PriceMode;

    @Column
    private Boolean point2TargetSizingEnabled;

    @Column
    private Double point2TargetProfitPercent;

    @Column
    private Double minimumPoint2Distance;

    @Column
    private Double tp1PositionPercent;

    @Column
    private Double tp2PositionPercent;

    @Column
    private Double tp3PositionPercent;

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
            String initialStopMode,
            boolean fallbackToPoint2Breakout,
            boolean bollingerPoint1FilterEnabled,
            int bollingerPeriod,
            double bollingerStandardDeviation,
            String bollingerSource,
            String point1PriceMode,
            boolean point2TargetSizingEnabled,
            double point2TargetProfitPercent,
            double minimumPoint2Distance,
            double tp1PositionPercent,
            double tp2PositionPercent,
            double tp3PositionPercent,
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
        this.initialStopMode = initialStopMode;
        this.fallbackToPoint2Breakout = fallbackToPoint2Breakout;
        this.bollingerPoint1FilterEnabled = bollingerPoint1FilterEnabled;
        this.bollingerPeriod = bollingerPeriod;
        this.bollingerStandardDeviation = bollingerStandardDeviation;
        this.bollingerSource = bollingerSource;
        this.point1PriceMode = point1PriceMode;
        this.point2TargetSizingEnabled = point2TargetSizingEnabled;
        this.point2TargetProfitPercent = point2TargetProfitPercent;
        this.minimumPoint2Distance = minimumPoint2Distance;
        this.tp1PositionPercent = tp1PositionPercent;
        this.tp2PositionPercent = tp2PositionPercent;
        this.tp3PositionPercent = tp3PositionPercent;
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

    public String getInitialStopMode() {
        return initialStopMode == null || initialStopMode.isBlank()
                ? "TTE_BAR" : initialStopMode;
    }

    public boolean isFallbackToPoint2Breakout() {
        return fallbackToPoint2Breakout != null && fallbackToPoint2Breakout;
    }

    public boolean isBollingerPoint1FilterEnabled() {
        return bollingerPoint1FilterEnabled == null || bollingerPoint1FilterEnabled;
    }

    public int getBollingerPeriod() {
        return bollingerPeriod == null ? 20 : bollingerPeriod;
    }

    public double getBollingerStandardDeviation() {
        return bollingerStandardDeviation == null ? 2.0 : bollingerStandardDeviation;
    }

    public String getBollingerSource() {
        return bollingerSource == null || bollingerSource.isBlank()
                ? "CLOSE" : bollingerSource;
    }

    public String getPoint1PriceMode() {
        return point1PriceMode == null || point1PriceMode.isBlank()
                ? "EXTREME" : point1PriceMode;
    }

    public boolean isPoint2TargetSizingEnabled() {
        return point2TargetSizingEnabled == null || point2TargetSizingEnabled;
    }

    public double getPoint2TargetProfitPercent() {
        return point2TargetProfitPercent == null ? 2.0 : point2TargetProfitPercent;
    }

    public double getMinimumPoint2Distance() {
        return minimumPoint2Distance == null ? 0.0 : minimumPoint2Distance;
    }

    public double getTp1PositionPercent() {
        return tp1PositionPercent == null ? 60.0 : tp1PositionPercent;
    }

    public double getTp2PositionPercent() {
        return tp2PositionPercent == null ? 20.0 : tp2PositionPercent;
    }

    public double getTp3PositionPercent() {
        return tp3PositionPercent == null ? 20.0 : tp3PositionPercent;
    }
}
