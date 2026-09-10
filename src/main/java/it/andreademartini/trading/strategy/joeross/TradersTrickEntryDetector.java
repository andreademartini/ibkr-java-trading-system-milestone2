package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.TradeDirection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Causal TTE detector: a candidate is created from a closed bar and can only
 * trigger while processing a later bar.
 */
public final class TradersTrickEntryDetector {

    private final TradeDirection direction;
    private final JoeRossParameters parameters;
    private final JoeRoss123Pattern pattern;
    private final int breakoutBarIndex;
    private final java.time.Instant breakoutTime;
    private final double breakoutPrice;
    private final RossHook rossHook;

    private JoeRossOrderCandidate candidate;
    private int barsSeen;
    private boolean terminal;

    public TradersTrickEntryDetector(
            TradeDirection direction,
            JoeRossParameters parameters,
            JoeRoss123Pattern pattern,
            int breakoutBarIndex,
            java.time.Instant breakoutTime,
            double breakoutPrice,
            RossHook rossHook) {
        this.direction = direction;
        this.parameters = parameters;
        this.pattern = pattern;
        this.breakoutBarIndex = breakoutBarIndex;
        this.breakoutTime = breakoutTime;
        this.breakoutPrice = breakoutPrice;
        this.rossHook = rossHook;
    }

    public TteResult start(HistoricalBar firstCorrectionBar) {
        if (barsSeen != 0 || terminal) {
            throw new IllegalStateException("TTE gia' avviata");
        }
        return registerCandidate(firstCorrectionBar);
    }

    public TteResult onBar(HistoricalBar bar) {
        List<JoeRossEvent> events = new ArrayList<>();
        List<JoeRossOrderAction> actions = new ArrayList<>();
        if (terminal) {
            return new TteResult(events, actions, null, true,
                    JoeRossSetupState.SETUP_EXPIRED);
        }

        if (candidate != null) {
            if (gapOverEntry(bar)) {
                if (parameters.rejectGapOverEntry()) {
                    terminal = true;
                    events.add(event(
                            JoeRossEventType.TTE_REJECTED_GAP,
                            bar,
                            candidate.entryPrice(),
                            "open=" + bar.open() + ", candidate=" + candidate.entryPrice()));
                    actions.add(JoeRossOrderAction.cancel("TTE_REJECTED_GAP"));
                    return new TteResult(events, actions, null, true,
                            JoeRossSetupState.SETUP_INVALIDATED);
                }
            }

            if (candidateTriggered(bar)) {
                terminal = true;
                JoeRossTradeSetup setup = new JoeRossTradeSetup(
                        direction,
                        pattern,
                        breakoutBarIndex,
                        breakoutTime,
                        breakoutPrice,
                        rossHook,
                        candidate,
                        bar.index(),
                        bar.timestamp(),
                        candidate.entryPrice(),
                        JoeRossSetupState.TTE_TRIGGERED);
                events.add(event(
                        JoeRossEventType.TTE_TRIGGERED,
                        bar,
                        candidate.entryPrice(),
                        "tteBar=" + candidate.tteBarNumber()));
                actions.add(JoeRossOrderAction.triggered(setup));
                return new TteResult(events, actions, setup, true,
                        JoeRossSetupState.TTE_TRIGGERED);
            }
        }

        if (rossHookBroken(bar)) {
            terminal = true;
            JoeRossEventType type = parameters.fallbackToRossHookBreakout()
                    ? JoeRossEventType.TTE_MISSED_RH_BROKEN
                    : JoeRossEventType.TTE_MISSED_RH_BROKEN;
            events.add(event(type, bar, rossHook.price(), "fallback disabled"));
            actions.add(JoeRossOrderAction.cancel("TTE_MISSED_RH_BROKEN"));
            return new TteResult(events, actions, null, true,
                    JoeRossSetupState.TTE_MISSED_RH_BROKEN);
        }

        if (barsSeen >= parameters.maxTteBars()) {
            if (canExtendForDoubleTriple(bar)) {
                return registerCandidate(bar);
            }
            terminal = true;
            events.add(event(
                    JoeRossEventType.TTE_EXPIRED,
                    bar,
                    bar.close(),
                    "maxBars=" + parameters.maxTteBars()));
            actions.add(JoeRossOrderAction.cancel("TTE_EXPIRED"));
            return new TteResult(events, actions, null, true,
                    JoeRossSetupState.SETUP_EXPIRED);
        }

        return registerCandidate(bar);
    }

    public boolean isTerminal() {
        return terminal;
    }

    private TteResult registerCandidate(HistoricalBar bar) {
        barsSeen++;
        double entry = price(direction == TradeDirection.LONG
                ? bar.high() + parameters.entryBufferTicks() * parameters.minimumTick()
                : bar.low() - parameters.entryBufferTicks() * parameters.minimumTick());
        double stop = price(direction == TradeDirection.LONG
                ? bar.low() - parameters.stopBufferTicks() * parameters.minimumTick()
                : bar.high() + parameters.stopBufferTicks() * parameters.minimumTick());
        double room = direction == TradeDirection.LONG
                ? difference(rossHook.price(), entry)
                : difference(entry, rossHook.price());
        double risk = Math.abs(difference(entry, stop));
        JoeRossOrderCandidate next = new JoeRossOrderCandidate(
                direction,
                barsSeen,
                bar,
                entry,
                stop,
                rossHook.price(),
                room,
                risk,
                pattern,
                breakoutBarIndex,
                breakoutTime,
                breakoutPrice,
                rossHook,
                parameters.rejectGapOverEntry());

        List<JoeRossEvent> events = new ArrayList<>();
        events.add(event(tteBarEvent(barsSeen), bar, entry,
                "high=" + bar.high() + ", low=" + bar.low()));
        List<JoeRossOrderAction> actions = new ArrayList<>();
        if (room <= 0 || (parameters.minimumRoomEnabled()
                && room < parameters.minimumRoomR() * risk)) {
            candidate = null;
            events.add(event(
                    JoeRossEventType.TTE_REJECTED_INSUFFICIENT_ROOM,
                    bar,
                    entry,
                    "room=" + room + ", risk=" + risk
                            + ", minimumR=" + parameters.minimumRoomR()));
            actions.add(JoeRossOrderAction.cancel("TTE_REJECTED_INSUFFICIENT_ROOM"));
        } else {
            candidate = next;
            events.add(event(
                    JoeRossEventType.TTE_ENTRY_CANDIDATE,
                    bar,
                    entry,
                    "tteBar=" + barsSeen + ", stop=" + stop + ", room=" + room));
            actions.add(JoeRossOrderAction.upsert(next));
        }
        return new TteResult(events, actions, null, false,
                JoeRossSetupState.WAITING_TTE_TRIGGER);
    }

    private JoeRossEventType tteBarEvent(int number) {
        return switch (Math.min(number, 3)) {
            case 1 -> JoeRossEventType.TTE_BAR_1_CREATED;
            case 2 -> JoeRossEventType.TTE_BAR_2_CREATED;
            default -> JoeRossEventType.TTE_BAR_3_CREATED;
        };
    }

    private boolean candidateTriggered(HistoricalBar bar) {
        return direction == TradeDirection.LONG
                ? bar.high() >= candidate.entryPrice()
                : bar.low() <= candidate.entryPrice();
    }

    private boolean gapOverEntry(HistoricalBar bar) {
        return direction == TradeDirection.LONG
                ? bar.open() > candidate.entryPrice()
                : bar.open() < candidate.entryPrice();
    }

    private boolean rossHookBroken(HistoricalBar bar) {
        return direction == TradeDirection.LONG
                ? bar.high() > rossHook.price()
                : bar.low() < rossHook.price();
    }

    private boolean canExtendForDoubleTriple(HistoricalBar bar) {
        if (!parameters.allowAfterThreeBarsOnDoubleTriple()
                || candidate == null
                || barsSeen >= parameters.maxTteBars() + 2) {
            return false;
        }
        double tolerance = parameters.doubleTripleToleranceTicks()
                * parameters.minimumTick();
        double comparison = direction == TradeDirection.LONG
                ? bar.high() : bar.low();
        double prior = direction == TradeDirection.LONG
                ? candidate.tteBar().high() : candidate.tteBar().low();
        return Math.abs(comparison - prior) <= tolerance;
    }

    private JoeRossEvent event(
            JoeRossEventType type,
            HistoricalBar bar,
            double eventPrice,
            String details) {
        return new JoeRossEvent(
                type,
                direction,
                bar.index(),
                bar.timestamp(),
                eventPrice,
                details);
    }

    private double price(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().doubleValue();
    }

    private double difference(double left, double right) {
        return BigDecimal.valueOf(left)
                .subtract(BigDecimal.valueOf(right))
                .stripTrailingZeros()
                .doubleValue();
    }

    public record TteResult(
            List<JoeRossEvent> events,
            List<JoeRossOrderAction> actions,
            JoeRossTradeSetup triggeredSetup,
            boolean terminal,
            JoeRossSetupState state) {

        public TteResult {
            events = List.copyOf(events);
            actions = List.copyOf(actions);
        }
    }
}
