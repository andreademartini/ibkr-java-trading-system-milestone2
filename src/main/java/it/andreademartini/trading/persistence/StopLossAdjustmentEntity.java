package it.andreademartini.trading.persistence;

import it.andreademartini.trading.orders.StopLossAdjustment;
import it.andreademartini.trading.orders.StopLossAdjustmentReason;
import it.andreademartini.trading.orders.TakeProfitLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stop_loss_adjustment")
public class StopLossAdjustmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String tradeId;

    @Column(precision = 19, scale = 6)
    private BigDecimal oldStopPrice;

    @Column(precision = 19, scale = 6)
    private BigDecimal newStopPrice;

    @Column(nullable = false)
    private long oldQuantity;

    @Column(nullable = false)
    private long newQuantity;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(length = 16)
    private String takeProfitLevel;

    @Column(nullable = false)
    private Instant createdAt;

    protected StopLossAdjustmentEntity() {
    }

    public StopLossAdjustmentEntity(StopLossAdjustment adjustment) {
        this.tradeId = adjustment.tradeId();
        this.oldStopPrice = adjustment.oldStopPrice();
        this.newStopPrice = adjustment.newStopPrice();
        this.oldQuantity = adjustment.oldQuantity();
        this.newQuantity = adjustment.newQuantity();
        this.reason = adjustment.reason().name();
        this.takeProfitLevel = adjustment.takeProfitLevel() == null
                ? null
                : adjustment.takeProfitLevel().name();
        this.createdAt = adjustment.createdAt();
    }

    public Long getId() {
        return id;
    }

    public String getTradeId() {
        return tradeId;
    }

    public BigDecimal getOldStopPrice() {
        return oldStopPrice;
    }

    public BigDecimal getNewStopPrice() {
        return newStopPrice;
    }

    public long getOldQuantity() {
        return oldQuantity;
    }

    public long getNewQuantity() {
        return newQuantity;
    }

    public StopLossAdjustmentReason getReason() {
        return StopLossAdjustmentReason.valueOf(reason);
    }

    public TakeProfitLevel getTakeProfitLevel() {
        return takeProfitLevel == null
                ? null
                : TakeProfitLevel.valueOf(takeProfitLevel);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
