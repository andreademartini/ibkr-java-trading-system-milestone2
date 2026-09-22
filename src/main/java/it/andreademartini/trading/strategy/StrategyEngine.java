package it.andreademartini.trading.strategy;

import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.MarketPriceEvent;
import it.andreademartini.trading.marketdata.MarketPriceType;
import it.andreademartini.trading.marketdata.HistoricalBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import it.andreademartini.trading.strategy.joeross.JoeRoss123RossHookTteStrategy;
import it.andreademartini.trading.strategy.joeross.JoeRossParameters;
import it.andreademartini.trading.strategy.joeross.JoeRossBarResult;
import it.andreademartini.trading.strategy.joeross.JoeRossSignalAdapter;
import it.andreademartini.trading.strategy.joeross.JoeRoss123TteBarResult;
import it.andreademartini.trading.strategy.joeross.JoeRoss123TteParameters;
import it.andreademartini.trading.strategy.joeross.JoeRoss123TteSignalAdapter;
import it.andreademartini.trading.strategy.joeross.JoeRoss123TteStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);

    private final TradingSystemProperties properties;
    private final SignalService signalService;
    private final Object strategyLock = new Object();

    private TradingStrategy strategy;
    private StrategyType activeStrategyType;
    private final Map<String, JoeRoss123RossHookTteStrategy> joeRossBySymbol =
            new HashMap<>();
    private final Map<String, JoeRoss123TteStrategy> joeRossTteBySymbol =
            new HashMap<>();

    public StrategyEngine(
            TradingSystemProperties properties,
            SignalService signalService) {
        this.properties = properties;
        this.signalService = signalService;
        reset();
    }

    @EventListener
    public void onMarketPrice(MarketPriceEvent event) {
        if (!properties.getStrategy().isEnabled()) {
            return;
        }

        if (event.priceType() != MarketPriceType.LAST) {
            return;
        }

        currentStrategy().onPrice(event.symbol(), event.price(), event.occurredAt())
                .ifPresent(signal -> {
                    try {
                        signalService.publish(
                                signal,
                                properties.getStrategy().isAutoExecuteSignals());
                    } catch (RuntimeException exception) {
                        log.error("Segnale {} {} non eseguito: {}",
                                signal.action(),
                                signal.symbol(),
                                exception.getMessage(),
                                exception);
                    }
                });
    }

    public void reset() {
        synchronized (strategyLock) {
            activeStrategyType = properties.getStrategy().getType();
            strategy = createStrategy(activeStrategyType);
            joeRossBySymbol.clear();
            joeRossTteBySymbol.clear();
        }
    }

    /**
     * Processes one fully closed OHLC bar. A future IBKR real-time-bar adapter
     * can call this method without changing the strategy or bypassing the
     * existing signal, risk and PAPER-order safety chain.
     */
    public List<TradingSignal> onBar(String symbol, HistoricalBar bar) {
        StrategyType configuredType = properties.getStrategy().getType();
        if (!properties.getStrategy().isEnabled()
                || (configuredType != StrategyType.JOE_ROSS_123_TTE
                && configuredType != StrategyType.JOE_ROSS_123_RH_TTE)) {
            return List.of();
        }

        String normalizedSymbol = normalizeSymbol(symbol);
        synchronized (strategyLock) {
            if (activeStrategyType != configuredType) {
                activeStrategyType = configuredType;
                strategy = createStrategy(activeStrategyType);
                joeRossBySymbol.clear();
                joeRossTteBySymbol.clear();
            }
            if (configuredType == StrategyType.JOE_ROSS_123_TTE) {
                JoeRoss123TteStrategy joeRoss = joeRossTteBySymbol.computeIfAbsent(
                        normalizedSymbol, ignored -> createJoeRoss123TteStrategy());
                JoeRoss123TteBarResult result = joeRoss.onBar(bar);
                List<TradingSignal> signals = JoeRoss123TteSignalAdapter.toSignals(
                        normalizedSymbol, bar, result,
                        properties.getStrategy().getQuantity());
                publish(signals);
                return signals;
            }
            JoeRoss123RossHookTteStrategy joeRoss = joeRossBySymbol.computeIfAbsent(
                    normalizedSymbol,
                    ignored -> createJoeRossStrategy());
            JoeRossBarResult result = joeRoss.onBar(bar);
            List<TradingSignal> signals = JoeRossSignalAdapter.toSignals(
                    normalizedSymbol, bar, result,
                    properties.getStrategy().getQuantity());
            publish(signals);
            return signals;
        }
    }

    private void publish(List<TradingSignal> signals) {
        for (TradingSignal signal : signals) {
            signalService.publish(
                    signal,
                    properties.getStrategy().isAutoExecuteSignals());
        }
    }

    private TradingStrategy currentStrategy() {
        synchronized (strategyLock) {
            StrategyType configuredType = properties.getStrategy().getType();
            if (strategy == null || activeStrategyType != configuredType) {
                activeStrategyType = configuredType;
                strategy = createStrategy(configuredType);
            }
            return strategy;
        }
    }

    private TradingStrategy createStrategy(StrategyType type) {
        return switch (type) {
            case MANUAL_ONLY -> new ManualOnlyStrategy();
            case MOVING_AVERAGE_CROSSOVER ->
                    new MovingAverageCrossoverStrategy(properties.getStrategy());
            case JOE_ROSS_123_TTE -> createJoeRoss123TteStrategy();
            case JOE_ROSS_123_RH_TTE -> createJoeRossStrategy();
        };
    }

    private JoeRoss123TteStrategy createJoeRoss123TteStrategy() {
        return new JoeRoss123TteStrategy(
                JoeRoss123TteParameters.from(
                        properties.getStrategy().getJoeRoss123Tte()),
                properties.getStrategy().getJoeRoss123Tte().getDirection());
    }

    private JoeRoss123RossHookTteStrategy createJoeRossStrategy() {
        return new JoeRoss123RossHookTteStrategy(
                JoeRossParameters.from(
                        properties.getStrategy().getJoeRoss123RhTte()),
                properties.getStrategy().getJoeRoss123RhTte().getDirection());
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
