package it.andreademartini.trading.strategy;

import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.MarketPriceEvent;
import it.andreademartini.trading.marketdata.MarketPriceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);

    private final TradingSystemProperties properties;
    private final SignalService signalService;
    private final Object strategyLock = new Object();

    private TradingStrategy strategy;
    private StrategyType activeStrategyType;

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
        };
    }
}
