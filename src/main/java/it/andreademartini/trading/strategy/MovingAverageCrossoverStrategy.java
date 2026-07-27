package it.andreademartini.trading.strategy;

import it.andreademartini.trading.config.TradingSystemProperties;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MovingAverageCrossoverStrategy implements TradingStrategy {

    private final TradingSystemProperties.StrategyProperties properties;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public MovingAverageCrossoverStrategy(
            TradingSystemProperties.StrategyProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<TradingSignal> onPrice(
            String symbol,
            double price,
            Instant timestamp) {
        if (price <= 0) {
            return Optional.empty();
        }

        State state = states.computeIfAbsent(symbol, ignored -> new State());
        synchronized (state) {
            state.prices.addLast(price);
            while (state.prices.size() > properties.getSlowPeriod()) {
                state.prices.removeFirst();
            }

            if (state.prices.size() < properties.getSlowPeriod()) {
                return Optional.empty();
            }

            double slowAverage = averageLast(state.prices, properties.getSlowPeriod());
            double fastAverage = averageLast(state.prices, properties.getFastPeriod());
            int relation = Double.compare(fastAverage, slowAverage);

            if (relation == 0) {
                return Optional.empty();
            }

            if (state.previousRelation == 0) {
                state.previousRelation = relation;
                return Optional.empty();
            }

            if (state.previousRelation == relation) {
                return Optional.empty();
            }

            if (state.lastSignalAt != null
                    && state.lastSignalAt.plus(properties.getSignalCooldown())
                    .isAfter(timestamp)) {
                state.previousRelation = relation;
                return Optional.empty();
            }

            SignalAction action = relation > 0
                    ? SignalAction.BUY
                    : SignalAction.SELL;

            state.previousRelation = relation;
            state.lastSignalAt = timestamp;

            String reason = "Incrocio medie mobili: fast="
                    + round(fastAverage)
                    + ", slow="
                    + round(slowAverage);

            return Optional.of(new TradingSignal(
                    java.util.UUID.randomUUID(),
                    symbol,
                    action,
                    price,
                    properties.getQuantity(),
                    "MOVING_AVERAGE_CROSSOVER",
                    reason,
                    timestamp
            ));
        }
    }

    private double averageLast(Deque<Double> prices, int count) {
        double sum = 0.0;
        int skipped = prices.size() - count;
        int index = 0;
        int included = 0;

        Iterator<Double> iterator = prices.iterator();
        while (iterator.hasNext()) {
            double value = iterator.next();
            if (index++ < skipped) {
                continue;
            }
            sum += value;
            included++;
        }

        return included == 0 ? 0.0 : sum / included;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static final class State {
        private final Deque<Double> prices = new ArrayDeque<>();
        private int previousRelation;
        private Instant lastSignalAt;
    }
}
