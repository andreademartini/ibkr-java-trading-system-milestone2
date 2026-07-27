package it.andreademartini.trading.strategy;

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
    private final TradingStrategy strategy;
    private final SignalService signalService;

    public StrategyEngine(
            TradingSystemProperties properties,
            SignalService signalService) {
        this.properties = properties;
        this.signalService = signalService;
        this.strategy = new MovingAverageCrossoverStrategy(properties.getStrategy());
    }

    @EventListener
    public void onMarketPrice(MarketPriceEvent event) {
        if (!properties.getStrategy().isEnabled()) {
            return;
        }

        if (event.priceType() != MarketPriceType.LAST) {
            return;
        }

        strategy.onPrice(event.symbol(), event.price(), event.occurredAt())
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
}
