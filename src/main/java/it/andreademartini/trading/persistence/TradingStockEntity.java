package it.andreademartini.trading.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "trading_stock",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trading_stock_symbol",
                columnNames = "symbol"))
public class TradingStockEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 16)
    private String securityType;

    @Column(nullable = false, length = 32)
    private String exchange;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(length = 32)
    private String primaryExchange;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "stock",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("sortOrder ASC, name ASC")
    private final List<TradingStockStrategyEntity> strategies = new ArrayList<>();

    protected TradingStockEntity() {
    }

    public TradingStockEntity(
            String id,
            String symbol,
            String name,
            String securityType,
            String exchange,
            String currency,
            String primaryExchange,
            Instant now) {
        this.id = id;
        this.symbol = symbol;
        this.name = name;
        this.securityType = securityType;
        this.exchange = exchange;
        this.currency = currency;
        this.primaryExchange = primaryExchange;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addStrategy(TradingStockStrategyEntity strategy) {
        strategies.add(strategy);
        strategy.setStock(this);
    }

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getSecurityType() {
        return securityType;
    }

    public String getExchange() {
        return exchange;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPrimaryExchange() {
        return primaryExchange;
    }

    public List<TradingStockStrategyEntity> getStrategies() {
        return strategies;
    }
}
