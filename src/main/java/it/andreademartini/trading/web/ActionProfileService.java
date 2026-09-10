package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.persistence.TradingStockEntity;
import it.andreademartini.trading.persistence.TradingStockRepository;
import it.andreademartini.trading.persistence.TradingStockStrategyEntity;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ActionProfileService {

    private static final List<DefaultUniverseInstrument> DEFAULT_UNIVERSE = List.of(
            new DefaultUniverseInstrument("AAPL", "Azione", "Tecnologia", "liquidissima, ottima per test", "NASDAQ"),
            new DefaultUniverseInstrument("MSFT", "Azione", "Tecnologia", "liquidita elevata", "NASDAQ"),
            new DefaultUniverseInstrument("NVDA", "Azione", "Semiconductor/AI", "forte momentum", "NASDAQ"),
            new DefaultUniverseInstrument("AMZN", "Azione", "Consumer/Cloud", "movimenti ampi", "NASDAQ"),
            new DefaultUniverseInstrument("META", "Azione", "Tecnologia", "buon trend/momentum", "NASDAQ"),
            new DefaultUniverseInstrument("GOOGL", "Azione", "Tecnologia", "molto liquida", "NASDAQ"),
            new DefaultUniverseInstrument("TSLA", "Azione", "Auto/Tech", "volatilita elevata", "NASDAQ"),
            new DefaultUniverseInstrument("AMD", "Azione", "Semiconductor", "buona per breakout", "NASDAQ"),
            new DefaultUniverseInstrument("AVGO", "Azione", "Semiconductor", "trend forti", "NASDAQ"),
            new DefaultUniverseInstrument("NFLX", "Azione", "Media/Tech", "volatilita interessante", "NASDAQ"),
            new DefaultUniverseInstrument("JPM", "Azione", "Banche", "settore diverso dal tech", "NYSE"),
            new DefaultUniverseInstrument("BAC", "Azione", "Banche", "molto liquida", "NYSE"),
            new DefaultUniverseInstrument("GS", "Azione", "Finanziari", "buoni movimenti", "NYSE"),
            new DefaultUniverseInstrument("XOM", "Azione", "Energia", "utile per diversificare", "NYSE"),
            new DefaultUniverseInstrument("CVX", "Azione", "Energia", "buona liquidita", "NYSE"),
            new DefaultUniverseInstrument("CAT", "Azione", "Industriale", "ciclica", "NYSE"),
            new DefaultUniverseInstrument("BA", "Azione", "Industriale", "volatilita elevata", "NYSE"),
            new DefaultUniverseInstrument("DIS", "Azione", "Media", "liquidita elevata", "NYSE"),
            new DefaultUniverseInstrument("NKE", "Azione", "Consumer", "pattern tecnici frequenti", "NYSE"),
            new DefaultUniverseInstrument("KO", "Azione", "Consumer defensive", "meno volatile", "NYSE"),
            new DefaultUniverseInstrument("SPY", "ETF", "S&P 500", "benchmark principale", "ARCA"),
            new DefaultUniverseInstrument("QQQ", "ETF", "Nasdaq-100", "tecnologia/growth", "NASDAQ"),
            new DefaultUniverseInstrument("IWM", "ETF", "Russell 2000", "small cap", "ARCA"),
            new DefaultUniverseInstrument("DIA", "ETF", "Dow Jones", "large cap industrial", "ARCA"),
            new DefaultUniverseInstrument("XLK", "ETF", "Technology", "ottimo per spread con tech", "ARCA"),
            new DefaultUniverseInstrument("XLF", "ETF", "Financials", "spread con banche", "ARCA"),
            new DefaultUniverseInstrument("XLE", "ETF", "Energy", "spread con oil stocks", "ARCA"),
            new DefaultUniverseInstrument("SMH", "ETF", "Semiconductor", "ottimo con NVDA/AMD/AVGO", "NASDAQ"),
            new DefaultUniverseInstrument("XLI", "ETF", "Industrials", "utile con CAT/BA", "ARCA"),
            new DefaultUniverseInstrument("XLY", "ETF", "Consumer discretionary", "utile con AMZN/TSLA/NKE", "ARCA"));

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
        return responseFrom(ensureDefaultUniverse());
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
                        defaultPrimaryExchange(entry.getKey()),
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
                blankToDefault(stock.primaryExchange(), defaultPrimaryExchange(symbol)),
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
        TradingSystemProperties.JoeRossProperties joe =
                properties.getStrategy().getJoeRoss123RhTte();
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
                profile.pivotStrength() == null
                        ? joe.getSwing().getPivotStrength()
                        : Math.max(1, profile.pivotStrength()),
                profile.minimumTick() == null
                        ? joe.getMinimumTick()
                        : Math.max(0.00000001, profile.minimumTick()),
                profile.tteMaxBars() == null
                        ? joe.getTte().getMaxBarsAfterRossHook()
                        : Math.max(1, profile.tteMaxBars()),
                profile.entryBufferTicks() == null
                        ? joe.getTte().getEntryBufferTicks()
                        : Math.max(0, profile.entryBufferTicks()),
                profile.stopBufferTicks() == null
                        ? joe.getTte().getStopBufferTicks()
                        : Math.max(0, profile.stopBufferTicks()),
                profile.minimumRoomEnabled() == null
                        ? joe.getTte().getMinimumRoom().isEnabled()
                        : profile.minimumRoomEnabled(),
                "R",
                profile.minimumRoomValue() == null
                        ? joe.getTte().getMinimumRoom().getValue()
                        : Math.max(0, profile.minimumRoomValue()),
                profile.allowDoubleTriple() == null
                        ? joe.getTte().isAllowAfterThreeBarsOnDoubleTriple()
                        : profile.allowDoubleTriple(),
                profile.doubleTripleToleranceTicks() == null
                        ? joe.getTte().getDoubleTripleToleranceTicks()
                        : Math.max(0, profile.doubleTripleToleranceTicks()),
                profile.rejectGapOverEntry() == null
                        ? joe.getTte().isRejectGapOverEntry()
                        : profile.rejectGapOverEntry(),
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
                entity.getMaxPoints(),
                entity.getPivotStrength(),
                entity.getMinimumTick(),
                entity.getTteMaxBars(),
                entity.getEntryBufferTicks(),
                entity.getStopBufferTicks(),
                entity.isMinimumRoomEnabled(),
                entity.getMinimumRoomType(),
                entity.getMinimumRoomValue(),
                entity.isAllowDoubleTriple(),
                entity.getDoubleTripleToleranceTicks(),
                entity.isRejectGapOverEntry());
    }

    private List<TradingStockEntity> ensureDefaultUniverse() {
        List<TradingStockEntity> existing = repository.findAllByOrderBySymbolAsc();
        Set<String> existingSymbols = existing.stream()
                .map(TradingStockEntity::getSymbol)
                .map(this::normalizeSymbol)
                .collect(java.util.stream.Collectors.toSet());

        List<ActionStockConfig> missing = DEFAULT_UNIVERSE.stream()
                .filter(instrument -> !existingSymbols.contains(instrument.symbol()))
                .map(this::defaultUniverseStock)
                .toList();
        if (missing.isEmpty()) {
            return existing;
        }

        repository.saveAll(missing.stream()
                .map(this::entity)
                .toList());
        repository.flush();
        return repository.findAllByOrderBySymbolAsc();
    }

    private ActionStockConfig defaultUniverseStock(
            DefaultUniverseInstrument instrument) {
        return new ActionStockConfig(
                UUID.randomUUID().toString(),
                instrument.symbol(),
                instrument.displayName(),
                "STK",
                "SMART",
                "USD",
                instrument.primaryExchange(),
                List.of(defaultStrategy(instrument.symbol(), instrument)));
    }

    private ActionProfileConfig defaultStrategy(String symbol) {
        return defaultStrategy(symbol, null);
    }

    private ActionProfileConfig defaultStrategy(
            String symbol,
            DefaultUniverseInstrument instrument) {
        TradingSystemProperties.StrategyProperties strategy = properties.getStrategy();
        String name = instrument == null
                ? symbol + " crossover"
                : symbol + " crossover - " + instrument.note();
        return new ActionProfileConfig(
                UUID.randomUUID().toString(),
                name,
                symbol,
                properties.getMarketData().getProvider(),
                strategy.getType(),
                strategy.getFastPeriod(),
                strategy.getSlowPeriod(),
                strategy.getQuantity(),
                10_000.0,
                0.0,
                0.0,
                StrategyTradeMode.LONG_SHORT,
                "DAILY",
                "compact",
                100,
                strategy.getJoeRoss123RhTte().getSwing().getPivotStrength(),
                strategy.getJoeRoss123RhTte().getMinimumTick(),
                strategy.getJoeRoss123RhTte().getTte().getMaxBarsAfterRossHook(),
                strategy.getJoeRoss123RhTte().getTte().getEntryBufferTicks(),
                strategy.getJoeRoss123RhTte().getTte().getStopBufferTicks(),
                strategy.getJoeRoss123RhTte().getTte().getMinimumRoom().isEnabled(),
                "R",
                strategy.getJoeRoss123RhTte().getTte().getMinimumRoom().getValue(),
                strategy.getJoeRoss123RhTte().getTte()
                        .isAllowAfterThreeBarsOnDoubleTriple(),
                strategy.getJoeRoss123RhTte().getTte()
                        .getDoubleTripleToleranceTicks(),
                strategy.getJoeRoss123RhTte().getTte().isRejectGapOverEntry());
    }

    private MarketDataProvider provider(MarketDataProvider provider) {
        return MarketDataProvider.IBKR;
    }

    private StrategyTradeMode direction(StrategyTradeMode direction) {
        return direction == null ? StrategyTradeMode.LONG_SHORT : direction;
    }

    private StrategyTradeMode direction(String value) {
        if (value == null || value.isBlank()) {
            return StrategyTradeMode.LONG_SHORT;
        }
        try {
            return StrategyTradeMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return StrategyTradeMode.LONG_SHORT;
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

    private String defaultPrimaryExchange(String symbol) {
        return switch (normalizeSymbol(symbol)) {
            case "AAPL", "AMD", "AMZN", "AVGO", "GOOG", "GOOGL", "INTC",
                    "META", "MSFT", "NFLX", "NVDA", "QQQ", "SMH",
                    "TSLA" -> "NASDAQ";
            case "BA", "BAC", "CAT", "CVX", "DIS", "GS", "JPM", "KO",
                    "NKE", "XOM" -> "NYSE";
            case "DIA", "IWM", "SPY", "XLE", "XLF", "XLI", "XLK",
                    "XLY" -> "ARCA";
            default -> "";
        };
    }

    private record DefaultUniverseInstrument(
            String symbol,
            String type,
            String sector,
            String note,
            String primaryExchange) {

        private String displayName() {
            return symbol + " - " + type + " - " + sector;
        }
    }
}
