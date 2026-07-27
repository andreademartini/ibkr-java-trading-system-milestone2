package it.andreademartini.trading.strategy;

import java.time.Instant;
import java.util.Optional;

public interface TradingStrategy {

    Optional<TradingSignal> onPrice(
            String symbol,
            double price,
            Instant timestamp);
}
