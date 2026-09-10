package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.TradingSignal;
import it.andreademartini.trading.strategy.TradingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JoeRoss123RossHookTteStrategy implements TradingStrategy {

    private static final Logger log = LoggerFactory.getLogger(
            JoeRoss123RossHookTteStrategy.class);

    private final SwingPointDetector swingDetector;
    private final Map<TradeDirection, DirectionMachine> machines =
            new EnumMap<>(TradeDirection.class);
    private final List<JoeRossEvent> eventHistory = new ArrayList<>();

    public JoeRoss123RossHookTteStrategy(
            JoeRossParameters parameters,
            StrategyTradeMode tradeMode) {
        this.swingDetector = new SwingPointDetector(parameters.pivotStrength());
        StrategyTradeMode resolved = tradeMode == null
                ? StrategyTradeMode.LONG_SHORT : tradeMode;
        if (resolved != StrategyTradeMode.SHORT_ONLY) {
            machines.put(TradeDirection.LONG,
                    new DirectionMachine(TradeDirection.LONG, parameters));
        }
        if (resolved != StrategyTradeMode.LONG_ONLY) {
            machines.put(TradeDirection.SHORT,
                    new DirectionMachine(TradeDirection.SHORT, parameters));
        }
    }

    /** Joe Ross requires closed OHLC bars; LAST ticks are intentionally ignored. */
    @Override
    public Optional<TradingSignal> onPrice(
            String symbol,
            double price,
            Instant timestamp) {
        return Optional.empty();
    }

    public JoeRossBarResult onBar(HistoricalBar bar) {
        List<SwingPoint> swings = swingDetector.onBar(bar);
        List<JoeRossEvent> events = new ArrayList<>();
        List<JoeRossOrderAction> actions = new ArrayList<>();
        JoeRossSetupState aggregate = JoeRossSetupState.SEARCHING_POINT_1;
        for (DirectionMachine machine : machines.values()) {
            JoeRossBarResult result = machine.onBar(bar, swings);
            events.addAll(result.events());
            actions.addAll(result.orderActions());
            if (result.state().ordinal() > aggregate.ordinal()) {
                aggregate = result.state();
            }
        }
        eventHistory.addAll(events);
        return new JoeRossBarResult(aggregate, events, actions);
    }

    public List<JoeRossEvent> events() {
        return List.copyOf(eventHistory);
    }

    public JoeRossPatternStatistics statistics() {
        return new JoeRossPatternStatistics(
                count(JoeRossEventType.JOE_ROSS_123_COMPLETED),
                count(JoeRossEventType.POINT2_BREAKOUT),
                count(JoeRossEventType.ROSS_HOOK_CREATED),
                count(JoeRossEventType.TTE_ENTRY_CANDIDATE),
                count(JoeRossEventType.TTE_TRIGGERED),
                count(JoeRossEventType.TTE_EXPIRED),
                count(JoeRossEventType.TTE_REJECTED_INSUFFICIENT_ROOM),
                count(JoeRossEventType.TTE_REJECTED_GAP),
                count(JoeRossEventType.TTE_MISSED_RH_BROKEN),
                count(JoeRossEventType.SETUP_INVALIDATED),
                count(JoeRossEventType.TTE_TRIGGERED, TradeDirection.LONG),
                count(JoeRossEventType.TTE_TRIGGERED, TradeDirection.SHORT));
    }

    private long count(JoeRossEventType type) {
        return eventHistory.stream().filter(event -> event.type() == type).count();
    }

    private long count(JoeRossEventType type, TradeDirection direction) {
        return eventHistory.stream()
                .filter(event -> event.type() == type && event.direction() == direction)
                .count();
    }

    private static final class DirectionMachine {

        private final TradeDirection direction;
        private final JoeRossParameters parameters;
        private final JoeRoss123Detector patternDetector;
        private JoeRossSetupState state = JoeRossSetupState.SEARCHING_POINT_1;
        private JoeRoss123Pattern pattern;
        private int breakoutBarIndex;
        private Instant breakoutTime;
        private double breakoutPrice;
        private RossHookDetector rossHookDetector;
        private TradersTrickEntryDetector tteDetector;
        private int terminalBarIndex = -1;

        private DirectionMachine(
                TradeDirection direction,
                JoeRossParameters parameters) {
            this.direction = direction;
            this.parameters = parameters;
            this.patternDetector = new JoeRoss123Detector(direction);
        }

        private JoeRossBarResult onBar(
                HistoricalBar bar,
                List<SwingPoint> swings) {
            if (terminalBarIndex >= 0 && bar.index() > terminalBarIndex) {
                reset();
            }
            List<JoeRossEvent> events = new ArrayList<>();
            List<JoeRossOrderAction> actions = new ArrayList<>();

            if (isSearchingPattern()) {
                for (SwingPoint swing : swings) {
                    events.addAll(patternDetector.onSwing(swing));
                }
                state = patternDetector.state();
                patternDetector.pattern().ifPresent(found -> {
                    pattern = found;
                    state = JoeRossSetupState.WAITING_POINT_2_BREAKOUT;
                });

                if (pattern != null && invalidatesPoint1(bar)) {
                    invalidate(bar, events, actions, "Point 1 violated before breakout");
                    return result(events, actions);
                }
                // P3 becomes knowable only when its right-hand confirmation
                // window has closed.  The OHLC of that confirmation bar may
                // already contain a P2 violation, but its intrabar ordering is
                // unknowable; treating it as a breakout would be look-ahead.
                // Therefore the breakout stop becomes active on the next bar.
                if (pattern != null && point2BreakoutIsActive(bar)
                        && point2Broken(bar)) {
                    breakoutBarIndex = bar.index();
                    breakoutTime = bar.timestamp();
                    breakoutPrice = point2BreakoutLevel();
                    state = JoeRossSetupState.WAITING_ROSS_HOOK;
                    rossHookDetector = new RossHookDetector(direction, bar);
                    events.add(event(
                            JoeRossEventType.POINT2_BREAKOUT,
                            bar,
                            breakoutPrice,
                            "barExtreme=" + trendExtreme(bar)));
                }
                return result(events, actions);
            }

            if (state == JoeRossSetupState.WAITING_ROSS_HOOK) {
                if (invalidatesPostBreakoutStructure(bar)) {
                    invalidate(bar, events, actions, "Point 3 violated before Ross Hook");
                    return result(events, actions);
                }
                if (bar.index() <= breakoutBarIndex) {
                    return result(events, actions);
                }
                rossHookDetector.onBar(bar).ifPresent(hook -> {
                    state = JoeRossSetupState.ROSS_HOOK_FOUND;
                    events.add(new JoeRossEvent(
                            JoeRossEventType.ROSS_HOOK_CREATED,
                            direction,
                            hook.barIndex(),
                            hook.timestamp(),
                            hook.price(),
                            "firstCorrectionBar=" + bar.index()));
                    tteDetector = new TradersTrickEntryDetector(
                            direction,
                            parameters,
                            pattern,
                            breakoutBarIndex,
                            breakoutTime,
                            breakoutPrice,
                            hook);
                    TradersTrickEntryDetector.TteResult tte = tteDetector.start(bar);
                    events.addAll(tte.events());
                    actions.addAll(tte.actions());
                    state = tte.state();
                    logCandidate(pattern, hook, tte.actions());
                });
                return result(events, actions);
            }

            if (state == JoeRossSetupState.WAITING_TTE_TRIGGER) {
                if (invalidatesPostBreakoutStructure(bar)) {
                    invalidate(bar, events, actions, "Point 3 violated before TTE entry");
                    return result(events, actions);
                }
                TradersTrickEntryDetector.TteResult tte = tteDetector.onBar(bar);
                events.addAll(tte.events());
                actions.addAll(tte.actions());
                state = tte.state();
                if (tte.terminal()) {
                    terminalBarIndex = bar.index();
                }
                return result(events, actions);
            }

            return result(events, actions);
        }

        private boolean isSearchingPattern() {
            return state == JoeRossSetupState.SEARCHING_POINT_1
                    || state == JoeRossSetupState.POINT_1_FOUND
                    || state == JoeRossSetupState.POINT_2_FOUND
                    || state == JoeRossSetupState.POINT_3_FOUND
                    || state == JoeRossSetupState.WAITING_POINT_2_BREAKOUT;
        }

        private boolean point2Broken(HistoricalBar bar) {
            double level = point2BreakoutLevel();
            if (parameters.point2BreakoutBufferTicks() == 0) {
                return direction == TradeDirection.LONG
                        ? bar.high() > level
                        : bar.low() < level;
            }
            return direction == TradeDirection.LONG
                    ? bar.high() >= level
                    : bar.low() <= level;
        }

        private double point2BreakoutLevel() {
            BigDecimal point2 = BigDecimal.valueOf(pattern.point2().price());
            BigDecimal buffer = BigDecimal.valueOf(parameters.minimumTick())
                    .multiply(BigDecimal.valueOf(
                            parameters.point2BreakoutBufferTicks()));
            return direction == TradeDirection.LONG
                    ? point2.add(buffer).doubleValue()
                    : point2.subtract(buffer).doubleValue();
        }

        private boolean point2BreakoutIsActive(HistoricalBar bar) {
            return bar.index() > pattern.point3().confirmationBarIndex();
        }

        private boolean invalidatesPoint1(HistoricalBar bar) {
            return direction == TradeDirection.LONG
                    ? bar.low() < pattern.point1().price()
                    : bar.high() > pattern.point1().price();
        }

        private boolean invalidatesPostBreakoutStructure(HistoricalBar bar) {
            return direction == TradeDirection.LONG
                    ? bar.low() < pattern.point3().price()
                    : bar.high() > pattern.point3().price();
        }

        private double trendExtreme(HistoricalBar bar) {
            return direction == TradeDirection.LONG ? bar.high() : bar.low();
        }

        private void invalidate(
                HistoricalBar bar,
                List<JoeRossEvent> events,
                List<JoeRossOrderAction> actions,
                String reason) {
            state = JoeRossSetupState.SETUP_INVALIDATED;
            terminalBarIndex = bar.index();
            events.add(event(
                    JoeRossEventType.SETUP_INVALIDATED,
                    bar,
                    trendExtreme(bar),
                    reason));
            actions.add(JoeRossOrderAction.cancel("SETUP_INVALIDATED"));
        }

        private JoeRossEvent event(
                JoeRossEventType type,
                HistoricalBar bar,
                double price,
                String details) {
            return new JoeRossEvent(
                    type,
                    direction,
                    bar.index(),
                    bar.timestamp(),
                    price,
                    details);
        }

        private JoeRossBarResult result(
                List<JoeRossEvent> events,
                List<JoeRossOrderAction> actions) {
            return new JoeRossBarResult(state, events, actions);
        }

        private void reset() {
            patternDetector.reset();
            pattern = null;
            breakoutBarIndex = 0;
            breakoutTime = null;
            breakoutPrice = 0;
            rossHookDetector = null;
            tteDetector = null;
            terminalBarIndex = -1;
            state = JoeRossSetupState.SEARCHING_POINT_1;
        }

        private void logCandidate(
                JoeRoss123Pattern setupPattern,
                RossHook hook,
                List<JoeRossOrderAction> actions) {
            actions.stream()
                    .filter(action -> action.type()
                            == JoeRossOrderAction.Type.UPSERT_CANDIDATE)
                    .map(JoeRossOrderAction::candidate)
                    .findFirst()
                    .ifPresent(candidate -> log.info("""
                            ========== JOE ROSS SETUP ==========
                            Direction: {}
                            P1: {}
                            P2: {}
                            P3: {}
                            Point2 breakout: {}
                            Ross Hook: {}
                            TTE Bar: {}
                            TTE Bar High: {}
                            TTE Bar Low: {}
                            TTE Candidate: {}
                            Initial Stop: {}
                            Room to RH: {}
                            Status: WAITING_TTE_TRIGGER
                            ====================================
                            """,
                            direction,
                            setupPattern.point1().price(),
                            setupPattern.point2().price(),
                            setupPattern.point3().price(),
                            breakoutPrice,
                            hook.price(),
                            candidate.tteBarNumber(),
                            candidate.tteBar().high(),
                            candidate.tteBar().low(),
                            candidate.entryPrice(),
                            candidate.initialStopPrice(),
                            candidate.roomToRossHook()));
        }
    }
}
