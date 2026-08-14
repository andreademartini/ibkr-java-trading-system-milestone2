package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.persistence.TradingStockEntity;
import it.andreademartini.trading.persistence.TradingStockRepository;
import it.andreademartini.trading.persistence.TradingStockStrategyEntity;
import it.andreademartini.trading.strategy.TradeDirection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ActionProfileService {

    private final TradingStockRepository repository;
    private final TradingSystemProperties properties;

    public ActionProfileService(
            TradingStockRepository repository,
            TradingSystemProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public ActionProfilesResponse response() {
        ensureDefaultStock();
        return responseFrom(repository.findAllByOrderBySymbolAsc());
    }

    @Transactional
    public ActionProfilesResponse save(ActionProfilesRequest request) {
        List<ActionStockConfig> stocks = requestedStocks(request);
        if (stocks.isEmpty()) {
            throw new IllegalArgumentException("Inserire almeno uno stock");
        }

        repository.deleteAll();
        repository.flush();
        repository.saveAll(stocks.stream()
                .map(this::entity)
                .toList());
        repository.flush();
        return responseFrom(repository.findAllByOrderBySymbolAsc());
    }

    private List<ActionStockConfig> requestedStocks(ActionProfilesRequest request) {
        if (request == null) {
            return List.of();
        }
        if (request.stocks() != null && !request.stocks().isEmpty()) {
            return request.stocks();
        }
        if (request.profiles() != null && !request.profiles().isEmpty()) {
            return stocksFromProfiles(request.profiles());
        }
        return List.of();
    }

    private List<ActionStockConfig> stocksFromProfiles(List<ActionProfileConfig> profiles) {
        Map<String, List<ActionProfileConfig>> bySymbol = new LinkedHashMap<>();
        for (ActionProfileConfig profile : profiles) {
            String symbol = normalizeSymbol(profile.symbol());
            bySymbol.computeIfAbsent(symbol, _ignored -> new ArrayList<>())
                    .add(profile);
        }

        return bySymbol.entrySet().stream()
                .map(entry -> new ActionStockConfig(
                        UUID.randomUUID().toString(),
                        entry.getKey(),
                        entry.getKey(),
                        "STK",
                        "SMART",
                        "USD",
                        "ARCA",
                        entry.getValue()))
                .toList();
    }

    private TradingStockEntity entity(ActionStockConfig stock) {
        String symbol = normalizeSymbol(stock.symbol());
        Instant now = Instant.now();
        TradingStockEntity entity = new TradingStockEntity(
                blankToDefault(stock.id(), UUID.randomUUID().toString()),
                symbol,
                blankToDefault(stock.name(), symbol),
                blankToDefault(stock.securityType(), "STK"),
                blankToDefault(stock.exchange(), "SMART"),
                blankToDefault(stock.currency(), "USD"),
                blankToDefault(stock.primaryExchange(), "ARCA"),
                now);

        List<ActionProfileConfig> strategies = stock.strategies() == null
                ? List.of()
                : stock.strategies();
        if (strategies.isEmpty()) {
            strategies = List.of(defaultStrategy(symbol));
        }

        for (int index = 0; index < strategies.size(); index++) {
            entity.addStrategy(strategyEntity(symbol, strategies.get(index), index, now));
        }
        return entity;
    }

    private TradingStockStrategyEntity strategyEntity(
            String symbol,
            ActionProfileConfig profile,
            int sortOrder,
            Instant now) {
        int fastPeriod = profile.fastPeriod() == null
                ? properties.getStrategy().getFastPeriod()
                : profile.fastPeriod();
        int slowPeriod = profile.slowPeriod() == null
                ? properties.getStrategy().getSlowPeriod()
                : profile.slowPeriod();
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException(
                    "Nel profilo " + symbol + " slowPeriod deve essere maggiore di fastPeriod");
        }

        StrategyType strategy = profile.strategy() == null
                ? properties.getStrategy().getType()
                : profile.strategy();
        return new TradingStockStrategyEntity(
                blankToDefault(profile.id(), UUID.randomUUID().toString()),
                blankToDefault(profile.name(), symbol + " " + strategy),
                provider(profile.provider()).name(),
                strategy.name(),
                fastPeriod,
                slowPeriod,
                profile.quantity() == null
                        ? properties.getStrategy().getQuantity()
                        : Math.max(1, profile.quantity()),
                profile.initialCapital() == null
                        ? 10_000.0
                        : Math.max(1.0, profile.initialCapital()),
                profile.commissionPerOrder() == null
                        ? 0.0
                        : Math.max(0.0, profile.commissionPerOrder()),
                profile.slippagePercent() == null
                        ? 0.0
                        : Math.max(0.0, profile.slippagePercent()),
                direction(profile.tradeDirection()).name(),
                normalizeTimeframe(profile.timeframe()),
                normalizeOutputSize(profile.outputSize()),
                profile.maxPoints() == null
                        ? 100
                        : Math.max(1, Math.min(5_000, profile.maxPoints())),
                sortOrder,
                now);
    }

    private ActionProfilesResponse responseFrom(List<TradingStockEntity> entities) {
        List<ActionStockConfig> stocks = entities.stream()
                .map(this::stockConfig)
                .toList();
        List<ActionProfileConfig> profiles = stocks.stream()
                .flatMap(stock -> stock.strategies().stream())
                .toList();
        return new ActionProfilesResponse(
                Arrays.asList(MarketDataProvider.values()),
                Arrays.asList(StrategyType.values()),
                stocks,
                profiles);
    }

    private ActionStockConfig stockConfig(TradingStockEntity entity) {
        return new ActionStockConfig(
                entity.getId(),
                entity.getSymbol(),
                entity.getName(),
                entity.getSecurityType(),
                entity.getExchange(),
                entity.getCurrency(),
                entity.getPrimaryExchange(),
                entity.getStrategies().stream()
                        .map(strategy -> profileConfig(entity.getSymbol(), strategy))
                        .toList());
    }

    private ActionProfileConfig profileConfig(
            String symbol,
            TradingStockStrategyEntity entity) {
        return new ActionProfileConfig(
                entity.getId(),
                entity.getName(),
                symbol,
                MarketDataProvider.IBKR,
                StrategyType.valueOf(entity.getStrategy()),
                entity.getFastPeriod(),
                entity.getSlowPeriod(),
                entity.getQuantity(),
                entity.getInitialCapital(),
                entity.getCommissionPerOrder(),
                entity.getSlippagePercent(),
                direction(entity.getTradeDirection()),
                entity.getTimeframe(),
                entity.getOutputSize(),
                entity.getMaxPoints());
    }

    private void ensureDefaultStock() {
        if (repository.count() > 0) {
            return;
        }

        TradingSystemProperties.InstrumentProperties instrument =
                properties.getMarketData().getInstruments().isEmpty()
                        ? new TradingSystemProperties.InstrumentProperties()
                        : properties.getMarketData().getInstruments().get(0);
        String symbol = normalizeSymbol(instrument.getSymbol());
        ActionStockConfig stock = new ActionStockConfig(
                UUID.randomUUID().toString(),
                symbol,
                symbol,
                blankToDefault(instrument.getSecurityType(), "STK"),
                blankToDefault(instrument.getExchange(), "SMART"),
                blankToDefault(instrument.getCurrency(), "USD"),
                blankToDefault(instrument.getPrimaryExchange(), "ARCA"),
                List.of(defaultStrategy(symbol)));
        repository.save(entity(stock));
        repository.flush();
    }

    private ActionProfileConfig defaultStrategy(String symbol) {
        TradingSystemProperties.StrategyProperties strategy = properties.getStrategy();
        return new ActionProfileConfig(
                UUID.randomUUID().toString(),
                symbol + " crossover",
                symbol,
                properties.getMarketData().getProvider(),
                strategy.getType(),
                strategy.getFastPeriod(),
                strategy.getSlowPeriod(),
                strategy.getQuantity(),
                10_000.0,
                0.0,
                0.0,
                TradeDirection.LONG_SHORT,
                "DAILY",
                "compact",
                100);
    }

    private MarketDataProvider provider(MarketDataProvider provider) {
        return MarketDataProvider.IBKR;
    }

    private TradeDirection direction(TradeDirection direction) {
        return direction == null ? TradeDirection.LONG_SHORT : direction;
    }

    private TradeDirection direction(String value) {
        if (value == null || value.isBlank()) {
            return TradeDirection.LONG_SHORT;
        }
        try {
            return TradeDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return TradeDirection.LONG_SHORT;
        }
    }

    private String normalizeSymbol(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTimeframe(String value) {
        return value == null || value.isBlank() ? "DAILY" : value.trim();
    }

    private String normalizeOutputSize(String value) {
        return value == null || value.isBlank()
                ? "compact"
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
