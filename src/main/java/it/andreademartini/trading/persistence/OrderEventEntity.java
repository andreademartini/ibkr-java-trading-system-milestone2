package it.andreademartini.trading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "order_event")
public class OrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int ibkrOrderId;

    private Integer parentOrderId;

    @Column(length = 64)
    private String tradeId;

    @Column(length = 32)
    private String role;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 16)
    private String side;

    @Column(length = 16)
    private String direction;

    @Column(length = 32)
    private String tradeState;

    @Column(nullable = false, length = 32)
    private String orderType;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, length = 64)
    private String status;

    private Double referencePrice;
    private Double limitPrice;
    private Double stopPrice;
    private Double trailingPercent;

    @Column(nullable = false, length = 1_000)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    protected OrderEventEntity() {
    }

    public OrderEventEntity(
            int ibkrOrderId,
            Integer parentOrderId,
            String tradeId,
            String role,
            String symbol,
            String side,
            String direction,
            String tradeState,
            String orderType,
            long quantity,
            String status,
            Double referencePrice,
            Double limitPrice,
            Double stopPrice,
            Double trailingPercent,
            String message,
            Instant createdAt) {
        this.ibkrOrderId = ibkrOrderId;
        this.parentOrderId = parentOrderId;
        this.tradeId = tradeId;
        this.role = role;
        this.symbol = symbol;
        this.side = side;
        this.direction = direction;
        this.tradeState = tradeState;
        this.orderType = orderType;
        this.quantity = quantity;
        this.status = status;
        this.referencePrice = referencePrice;
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
        this.trailingPercent = trailingPercent;
        this.message = message;
        this.createdAt = createdAt;
    }
}
