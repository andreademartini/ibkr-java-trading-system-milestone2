package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.TradeDirection;

import java.util.Optional;

/** Detects only the first Ross Hook after the supplied Point-2 breakout. */
public final class RossHookDetector {

    private final TradeDirection direction;
    private HistoricalBar extremeBar;
    private HistoricalBar previousBar;
    private boolean completed;

    public RossHookDetector(TradeDirection direction, HistoricalBar breakoutBar) {
        this.direction = direction;
        this.extremeBar = breakoutBar;
        this.previousBar = breakoutBar;
    }

    public Optional<RossHook> onBar(HistoricalBar bar) {
        if (completed) {
            return Optional.empty();
        }

        if (extendsTrend(bar)) {
            extremeBar = bar;
            previousBar = bar;
            return Optional.empty();
        }

        boolean correctionStarted = direction == TradeDirection.LONG
                ? bar.low() < previousBar.low()
                : bar.high() > previousBar.high();
        previousBar = bar;
        if (!correctionStarted) {
            return Optional.empty();
        }

        completed = true;
        return Optional.of(new RossHook(
                direction == TradeDirection.LONG
                        ? extremeBar.high() : extremeBar.low(),
                extremeBar.index(),
                extremeBar.timestamp()));
    }

    private boolean extendsTrend(HistoricalBar bar) {
        return direction == TradeDirection.LONG
                ? bar.high() > extremeBar.high()
                : bar.low() < extremeBar.low();
    }
}
