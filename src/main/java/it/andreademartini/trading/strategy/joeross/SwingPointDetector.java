package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;

import java.util.ArrayList;
import java.util.List;

/**
 * Causal fixed-window pivot detector. A pivot at i is emitted only while
 * processing i + strength, and emitted pivots are never revised.
 */
public final class SwingPointDetector {

    private final int strength;
    private final List<HistoricalBar> bars = new ArrayList<>();

    public SwingPointDetector(int strength) {
        if (strength < 1) {
            throw new IllegalArgumentException("pivotStrength deve essere almeno 1");
        }
        this.strength = strength;
    }

    public List<SwingPoint> onBar(HistoricalBar bar) {
        bars.add(bar);
        int center = bars.size() - 1 - strength;
        if (center < strength) {
            return List.of();
        }

        HistoricalBar candidate = bars.get(center);
        boolean swingHigh = true;
        boolean swingLow = true;
        for (int index = center - strength; index <= center + strength; index++) {
            if (index == center) {
                continue;
            }
            HistoricalBar surrounding = bars.get(index);
            swingHigh &= candidate.high() > surrounding.high();
            swingLow &= candidate.low() < surrounding.low();
        }

        List<SwingPoint> result = new ArrayList<>(2);
        if (swingLow) {
            result.add(point(SwingType.LOW, candidate, bar));
        }
        if (swingHigh) {
            result.add(point(SwingType.HIGH, candidate, bar));
        }
        return List.copyOf(result);
    }

    private SwingPoint point(
            SwingType type,
            HistoricalBar pivot,
            HistoricalBar confirmationBar) {
        return new SwingPoint(
                type,
                pivot.index(),
                pivot.timestamp(),
                type == SwingType.HIGH ? pivot.high() : pivot.low(),
                confirmationBar.index(),
                confirmationBar.timestamp());
    }
}
