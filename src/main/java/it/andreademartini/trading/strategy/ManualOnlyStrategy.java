package it.andreademartini.trading.strategy;

import java.time.Instant;
import java.util.Optional;

public class ManualOnlyStrategy implements TradingStrategy {

    @Override
    public Optional<TradingSignal> onPrice(
            String symbol,
            double price,
            Instant timestamp) {
        return Optional.empty();
    }
}
