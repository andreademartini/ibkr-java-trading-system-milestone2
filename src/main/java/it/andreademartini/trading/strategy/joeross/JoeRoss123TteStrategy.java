package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.TradingSignal;
import it.andreademartini.trading.strategy.TradingStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Causal Joe Ross 1-2-3 strategy whose TTE is built during the correction
 * after Point 2. It never waits for a Point-2 breakout and has no Ross Hook
 * dependency.
 */
public final class JoeRoss123TteStrategy implements TradingStrategy {

    private static final Logger log = LoggerFactory.getLogger(
            JoeRoss123TteStrategy.class);

    private final SwingPointDetector swingDetector;
    private final BollingerPoint1FilterParameters bollingerParameters;
    private final BollingerBandsCalculator bollingerCalculator;
    private final Map<Integer, BollingerBandsValue> bollingerByBar = new HashMap<>();
    private final List<BollingerPoint1Check> bollingerPoint1Checks = new ArrayList<>();
    private final Map<TradeDirection, DirectionMachine> machines =
            new EnumMap<>(TradeDirection.class);
    private final List<JoeRossEvent> eventHistory = new ArrayList<>();

    public JoeRoss123TteStrategy(
            JoeRoss123TteParameters parameters,
            StrategyTradeMode tradeMode) {
        swingDetector = new SwingPointDetector(parameters.pivotStrength());
        bollingerParameters = parameters.bollingerPoint1Filter();
        bollingerCalculator = new BollingerBandsCalculator(
                parameters.bollingerPoint1Filter());
        StrategyTradeMode resolved = tradeMode == null
                ? StrategyTradeMode.LONG_SHORT : tradeMode;
        if (resolved.allowsLong()) {
            machines.put(TradeDirection.LONG,
                    new DirectionMachine(TradeDirection.LONG, parameters));
        }
        if (resolved.allowsShort()) {
            machines.put(TradeDirection.SHORT,
                    new DirectionMachine(TradeDirection.SHORT, parameters));
        }
    }

    @Override
    public Optional<TradingSignal> onPrice(
            String symbol,
            double price,
            Instant timestamp) {
        return Optional.empty();
    }

    public JoeRoss123TteBarResult onBar(HistoricalBar bar) {
        bollingerCalculator.onBar(bar).ifPresent(
                value -> bollingerByBar.put(value.barIndex(), value));
        List<SwingPoint> swings = swingDetector.onBar(bar);
        List<JoeRossEvent> events = new ArrayList<>();
        List<JoeRoss123TteOrderAction> actions = new ArrayList<>();
        JoeRoss123TteState aggregate = JoeRoss123TteState.SEARCHING_POINT_1;
        for (DirectionMachine machine : machines.values()) {
            JoeRoss123TteBarResult result = machine.onBar(
                    bar, swings, bollingerByBar, bollingerPoint1Checks);
            events.addAll(result.events());
            actions.addAll(result.orderActions());
            if (result.state().ordinal() > aggregate.ordinal()) {
                aggregate = result.state();
            }
        }
        eventHistory.addAll(events);
        return new JoeRoss123TteBarResult(aggregate, events, actions);
    }

    public List<JoeRossEvent> events() {
        return List.copyOf(eventHistory);
    }

    public List<BollingerBandsValue> bollingerBands() {
        return bollingerCalculator.values();
    }

    public List<BollingerPoint1Check> bollingerPoint1Checks() {
        return List.copyOf(bollingerPoint1Checks);
    }

    public BollingerPoint1FilterParameters bollingerPoint1FilterParameters() {
        return bollingerParameters;
    }

    public JoeRoss123TteStatistics statistics() {
        return new JoeRoss123TteStatistics(
                count(JoeRossEventType.POINT1_CANDIDATE),
                count(JoeRossEventType.POINT1_BOLLINGER_VALID),
                count(JoeRossEventType.POINT1_BOLLINGER_REJECTED),
                count(JoeRossEventType.JOE_ROSS_123_COMPLETED),
                count(JoeRossEventType.JOE_ROSS_POINT3_CORRECTION_STARTED),
                count(JoeRossEventType.TTE_BAR_1_CREATED),
                count(JoeRossEventType.TTE_BAR_2_CREATED),
                count(JoeRossEventType.TTE_BAR_3_CREATED),
                countTriggeredBar(1),
                countTriggeredBar(2),
                countTriggeredBar(3),
                count(JoeRossEventType.TTE_TRIGGERED),
                count(JoeRossEventType.TTE_EXPIRED)
                        + count(JoeRossEventType.SETUP_EXPIRED),
                count(JoeRossEventType.TTE_MISSED_POINT2_BREAKOUT),
                count(JoeRossEventType.TTE_REJECTED_INSUFFICIENT_ROOM),
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

    private long countTriggeredBar(int number) {
        String marker = "tteBar=" + number;
        return eventHistory.stream()
                .filter(event -> event.type() == JoeRossEventType.TTE_TRIGGERED)
                .filter(event -> event.details() != null
                        && event.details().contains(marker))
                .count();
    }

    private static final class DirectionMachine {

        private final TradeDirection direction;
        private final JoeRoss123TteParameters parameters;
        private final JoeRoss123Detector detector;
        private final BollingerPoint1Filter bollingerPoint1Filter;
        private JoeRoss123TteState state = JoeRoss123TteState.SEARCHING_POINT_1;
        private SwingPoint point1;
        private SwingPoint point2;
        private SwingPoint point3;
        private JoeRoss123TteCandidate candidate;
        private BollingerPoint1Check point1BollingerCheck;
        private int tteBarsSeen;
        private int terminalBarIndex = -1;

        private DirectionMachine(
                TradeDirection direction,
                JoeRoss123TteParameters parameters) {
            this.direction = direction;
            this.parameters = parameters;
            detector = new JoeRoss123Detector(direction);
            bollingerPoint1Filter = new BollingerPoint1Filter(
                    parameters.bollingerPoint1Filter());
        }

        private JoeRoss123TteBarResult onBar(
                HistoricalBar bar,
                List<SwingPoint> swings,
                Map<Integer, BollingerBandsValue> bollingerByBar,
                List<BollingerPoint1Check> checks) {
            if (terminalBarIndex >= 0 && bar.index() > terminalBarIndex) {
                reset();
            }
            List<JoeRossEvent> events = new ArrayList<>();
            List<JoeRoss123TteOrderAction> actions = new ArrayList<>();

            if (point2 == null) {
                for (SwingPoint swing : swings) {
                    if (isPoint1Type(swing)) {
                        BollingerPoint1Check check = bollingerPoint1Filter.evaluate(
                                direction, swing,
                                Optional.ofNullable(bollingerByBar.get(swing.barIndex())));
                        checks.add(check);
                        events.add(point1CandidateEvent(check));
                        if (check.filterEnabled()) {
                            events.add(bollingerEvent(check));
                        }
                        logBollingerCheck(bar.symbol(), check);
                        if (!check.bollingerPoint1Valid()) {
                            if (rejectedCandidateSupersedesPoint1(swing)) {
                                detector.reset();
                                point1BollingerCheck = null;
                            }
                            continue;
                        }
                        SwingPoint previous = detector.point1().orElse(null);
                        events.addAll(detector.onSwing(swing));
                        SwingPoint accepted = detector.point1().orElse(null);
                        if (accepted != null && accepted != previous
                                && accepted.barIndex() == swing.barIndex()) {
                            point1BollingerCheck = check;
                        }
                        continue;
                    }
                    events.addAll(detector.onSwing(swing));
                }
                point1 = detector.point1().orElse(null);
                point2 = detector.point2().orElse(null);
                if (point2 != null) {
                    state = JoeRoss123TteState.POINT_2_FOUND;
                    // The bar that causally confirms P2 is already a completed
                    // correction bar. It may create an order for the next bar,
                    // but can never trigger an entry on itself.
                    if (bar.index() > point2.barIndex()) {
                        if (invalidatesPoint1(bar)) {
                            terminate(bar, events, actions,
                                    JoeRossEventType.SETUP_INVALIDATED,
                                    JoeRoss123TteState.SETUP_INVALIDATED,
                                    "Point 1 violated while confirming Point 2");
                            return result(events, actions);
                        }
                        registerCandidate(bar, events, actions);
                    }
                } else if (point1 != null) {
                    state = JoeRoss123TteState.POINT_1_FOUND;
                }
                return result(events, actions);
            }

            if (invalidatesPoint1(bar)) {
                terminate(bar, events, actions,
                        JoeRossEventType.SETUP_INVALIDATED,
                        JoeRoss123TteState.SETUP_INVALIDATED,
                        "Point 1 violated during Point 3 correction");
                return result(events, actions);
            }

            // A level built from a closed TTE bar is evaluated only on a later bar.
            if (candidate != null && bar.index() > candidate.tteBar().index()
                    && candidateTriggered(bar)) {
                JoeRoss123TteTradeSetup setup = new JoeRoss123TteTradeSetup(
                        direction,
                        point1,
                        point2,
                        candidate.point3AtCandidate(),
                        candidate,
                        bar.index(),
                        bar.timestamp(),
                        candidate.entryPrice(),
                        JoeRoss123TteState.TTE_TRIGGERED);
                point3 = candidate.point3AtCandidate();
                events.add(point3Event(JoeRossEventType.POINT3_CONFIRMED,
                        point3, "triggeredOnBar=" + bar.index()));
                events.add(event(JoeRossEventType.TTE_TRIGGERED, bar,
                        candidate.entryPrice(), "tteBar=" + candidate.tteBarNumber()));
                actions.add(JoeRoss123TteOrderAction.triggered(setup));
                state = JoeRoss123TteState.TTE_TRIGGERED;
                terminalBarIndex = bar.index();
                return result(events, actions);
            }

            if (candidate != null && bar.index() > candidate.tteBar().index()) {
                events.add(event(JoeRossEventType.TTE_ORDER_CANCELLED, bar,
                        candidate.entryPrice(), "tteBar="
                                + candidate.tteBarNumber()
                                + ", validOnlyForBar=" + bar.index()));
                actions.add(JoeRoss123TteOrderAction.cancel(
                        "TTE_ORDER_CANCELLED"));
                candidate = null;
            }

            if (point2Broken(bar)) {
                terminate(bar, events, actions,
                        JoeRossEventType.TTE_MISSED_POINT2_BREAKOUT,
                        JoeRoss123TteState.TTE_MISSED,
                        "Point 2 broken without a valid TTE; fallback="
                                + parameters.fallbackToPoint2Breakout());
                return result(events, actions);
            }

            if (tteBarsSeen >= parameters.maxTteBars()) {
                terminate(bar, events, actions,
                        JoeRossEventType.SETUP_EXPIRED,
                        JoeRoss123TteState.TTE_EXPIRED,
                        "maxBars=" + parameters.maxTteBars());
                return result(events, actions);
            }

            registerCandidate(bar, events, actions);
            return result(events, actions);
        }

        private void registerCandidate(
                HistoricalBar bar,
                List<JoeRossEvent> events,
                List<JoeRoss123TteOrderAction> actions) {
            SwingPoint confirmedPoint3 = confirmPoint3(bar);
            tteBarsSeen++;
            if (tteBarsSeen == 1) {
                events.add(event(
                        JoeRossEventType.JOE_ROSS_POINT3_CORRECTION_STARTED,
                        bar,
                        point3.price(),
                        "point2Bar=" + point2.barIndex()));
                events.add(event(
                        JoeRossEventType.JOE_ROSS_123_COMPLETED,
                        bar,
                        point3.price(),
                        "Point 3 correction observed causally"));
            }
            double entry = bufferedPrice(
                    direction == TradeDirection.LONG ? bar.high() : bar.low(),
                    parameters.entryBufferTicks(),
                    direction == TradeDirection.LONG);
            double stopReference = direction == TradeDirection.LONG
                    ? bar.low() : bar.high();
            double stop = bufferedPrice(
                    stopReference,
                    parameters.stopBufferTicks(),
                    direction == TradeDirection.SHORT);
            double room = direction == TradeDirection.LONG
                    ? difference(point2.price(), entry)
                    : difference(entry, point2.price());
            double point2Delta = direction == TradeDirection.LONG
                    ? difference(point2.price(), bar.high())
                    : difference(bar.low(), point2.price());
            double risk = Math.abs(difference(entry, stop));

            state = switch (Math.min(tteBarsSeen, 3)) {
                case 1 -> JoeRoss123TteState.TTE_BAR_1;
                case 2 -> JoeRoss123TteState.TTE_BAR_2;
                default -> JoeRoss123TteState.TTE_BAR_3;
            };
            events.add(event(tteBarEvent(tteBarsSeen), bar, entry,
                    "open=" + bar.open() + ", high=" + bar.high()
                            + ", low=" + bar.low() + ", close=" + bar.close()));

            double requiredRoom = parameters.minimumRoomType().equals("R")
                    ? parameters.minimumRoomValue() * risk
                    : parameters.minimumRoomValue();
            if (point2Delta <= parameters.point2TargetSizing()
                    .minimumPoint2Distance()) {
                candidate = null;
                events.add(event(JoeRossEventType.POINT2_DISTANCE_TOO_SMALL,
                        bar, entry, "point2Delta=" + point2Delta + ", minimum="
                                + parameters.point2TargetSizing().minimumPoint2Distance()));
                actions.add(JoeRoss123TteOrderAction.cancel(
                        "POINT2_DISTANCE_TOO_SMALL"));
                state = JoeRoss123TteState.WAITING_TTE_TRIGGER;
                return;
            }
            if (risk <= 0
                    || (parameters.minimumRoomEnabled() && room < requiredRoom)) {
                candidate = null;
                events.add(event(JoeRossEventType.TTE_REJECTED_INSUFFICIENT_ROOM,
                        bar, entry, "roomToPoint2=" + room + ", risk=" + risk
                                + ", required=" + requiredRoom));
                actions.add(JoeRoss123TteOrderAction.cancel(
                        "TTE_REJECTED_INSUFFICIENT_ROOM"));
                state = JoeRoss123TteState.WAITING_TTE_TRIGGER;
                return;
            }

            candidate = new JoeRoss123TteCandidate(
                    direction,
                    point1,
                    point2,
                    confirmedPoint3,
                    tteBarsSeen,
                    bar,
                    entry,
                    stop,
                    parameters.entryBufferTicks(),
                    parameters.stopBufferTicks(),
                    room,
                    point2Delta,
                    risk,
                    point1BollingerCheck,
                    parameters.point2TargetSizing(),
                    parameters.takeProfit());
            JoeRossEventType candidateEvent = tteBarsSeen == 1
                    ? JoeRossEventType.TTE_ENTRY_CANDIDATE
                    : JoeRossEventType.TTE_CANDIDATE_UPDATED;
            events.add(event(candidateEvent, bar, entry,
                    "tteBar=" + tteBarsSeen + ", stop=" + stop
                            + ", roomToPoint2=" + room
                            + ", point2Delta=" + point2Delta));
            if (candidateEvent != JoeRossEventType.TTE_ENTRY_CANDIDATE) {
                events.add(event(JoeRossEventType.TTE_ENTRY_CANDIDATE, bar, entry,
                        "tteBar=" + tteBarsSeen + ", validForBar="
                                + (bar.index() + 1)));
            }
            events.add(event(JoeRossEventType.INITIAL_STOP_CREATED, bar, stop,
                    "mode=" + parameters.initialStopMode()));
            actions.add(JoeRoss123TteOrderAction.upsert(candidate));
            state = JoeRoss123TteState.WAITING_TTE_TRIGGER;
        }

        private SwingPoint confirmPoint3(HistoricalBar bar) {
            double observed = direction == TradeDirection.LONG ? bar.low() : bar.high();
            SwingPoint confirmed = new SwingPoint(
                    direction == TradeDirection.LONG ? SwingType.LOW : SwingType.HIGH,
                    bar.index(), bar.timestamp(), observed,
                    bar.index(), bar.timestamp());
            point3 = confirmed;
            return confirmed;
        }

        private boolean invalidatesPoint1(HistoricalBar bar) {
            return direction == TradeDirection.LONG
                    ? bar.low() <= point1.price()
                    : bar.high() >= point1.price();
        }

        private boolean point2Broken(HistoricalBar bar) {
            return direction == TradeDirection.LONG
                    ? bar.high() >= point2.price()
                    : bar.low() <= point2.price();
        }

        private boolean candidateTriggered(HistoricalBar bar) {
            return direction == TradeDirection.LONG
                    ? bar.high() >= candidate.entryPrice()
                    : bar.low() <= candidate.entryPrice();
        }

        private void terminate(
                HistoricalBar bar,
                List<JoeRossEvent> events,
                List<JoeRoss123TteOrderAction> actions,
                JoeRossEventType eventType,
                JoeRoss123TteState terminalState,
                String details) {
            candidate = null;
            state = terminalState;
            terminalBarIndex = bar.index();
            events.add(event(eventType, bar,
                    direction == TradeDirection.LONG ? bar.low() : bar.high(), details));
            actions.add(JoeRoss123TteOrderAction.cancel(eventType.name()));
        }

        private JoeRossEventType tteBarEvent(int number) {
            return switch (Math.min(number, 3)) {
                case 1 -> JoeRossEventType.TTE_BAR_1_CREATED;
                case 2 -> JoeRossEventType.TTE_BAR_2_CREATED;
                default -> JoeRossEventType.TTE_BAR_3_CREATED;
            };
        }

        private JoeRossEvent point3Event(
                JoeRossEventType type,
                SwingPoint knownPoint3,
                String details) {
            return new JoeRossEvent(type, direction, knownPoint3.barIndex(),
                    knownPoint3.timestamp(), knownPoint3.price(), details);
        }

        private JoeRossEvent event(
                JoeRossEventType type,
                HistoricalBar bar,
                double eventPrice,
                String details) {
            return new JoeRossEvent(type, direction, bar.index(), bar.timestamp(),
                    eventPrice, details);
        }

        private JoeRoss123TteBarResult result(
                List<JoeRossEvent> events,
                List<JoeRoss123TteOrderAction> actions) {
            return new JoeRoss123TteBarResult(state, events, actions);
        }

        private void reset() {
            detector.reset();
            state = JoeRoss123TteState.SEARCHING_POINT_1;
            point1 = null;
            point2 = null;
            point3 = null;
            candidate = null;
            point1BollingerCheck = null;
            tteBarsSeen = 0;
            terminalBarIndex = -1;
        }

        private double price(double value) {
            return BigDecimal.valueOf(value).stripTrailingZeros().doubleValue();
        }

        private double bufferedPrice(double base, int ticks, boolean add) {
            BigDecimal buffer = BigDecimal.valueOf(parameters.minimumTick())
                    .multiply(BigDecimal.valueOf(ticks));
            BigDecimal value = BigDecimal.valueOf(base);
            return (add ? value.add(buffer) : value.subtract(buffer))
                    .stripTrailingZeros().doubleValue();
        }

        private double difference(double left, double right) {
            return BigDecimal.valueOf(left).subtract(BigDecimal.valueOf(right))
                    .stripTrailingZeros().doubleValue();
        }

        private boolean isPoint1Type(SwingPoint swing) {
            return swing.type() == (direction == TradeDirection.LONG
                    ? SwingType.LOW : SwingType.HIGH);
        }

        private boolean rejectedCandidateSupersedesPoint1(SwingPoint swing) {
            SwingPoint current = detector.point1().orElse(null);
            if (current == null) {
                return true;
            }
            return direction == TradeDirection.LONG
                    ? swing.price() < current.price()
                    : swing.price() > current.price();
        }

        private JoeRossEvent bollingerEvent(BollingerPoint1Check check) {
            return new JoeRossEvent(
                    check.bollingerPoint1Valid()
                            ? JoeRossEventType.POINT1_BOLLINGER_VALID
                            : JoeRossEventType.POINT1_BOLLINGER_REJECTED,
                    direction, check.barIndex(), check.timestamp(),
                    check.point1Price().doubleValue(), checkDetails(check));
        }

        private JoeRossEvent point1CandidateEvent(BollingerPoint1Check check) {
            return new JoeRossEvent(
                    JoeRossEventType.POINT1_CANDIDATE,
                    direction, check.barIndex(), check.timestamp(),
                    check.point1Price().doubleValue(), checkDetails(check));
        }

        private String checkDetails(BollingerPoint1Check check) {
            return "point1Price=" + check.point1Price()
                    + ", bollingerMiddle=" + check.bollingerMiddle()
                    + ", bollingerUpper=" + check.bollingerUpper()
                    + ", bollingerLower=" + check.bollingerLower()
                    + ", bollingerPeriod=" + check.bollingerPeriod()
                    + ", bollingerStdDevMultiplier="
                    + check.bollingerStdDevMultiplier()
                    + ", bollingerPoint1Valid=" + check.bollingerPoint1Valid()
                    + ", bollingerDistance=" + check.bollingerDistance()
                    + ", point1PriceMode=" + check.point1PriceMode()
                    + (check.reason() == null ? "" : ", reason=" + check.reason());
        }
    }

    private static void logBollingerCheck(
            String symbol,
            BollingerPoint1Check check) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String comparison = check.direction() == TradeDirection.LONG ? "<=" : ">=";
        Object band = check.direction() == TradeDirection.LONG
                ? check.bollingerLower() : check.bollingerUpper();
        log.debug("""
                ========== POINT 1 BOLLINGER CHECK ==========
                Symbol: {}
                Direction: {}
                Point1 Price: {}
                BB Period: {}
                BB StdDev: {}
                Lower BB: {}
                Middle BB: {}
                Upper BB: {}
                Condition: {} {} {}
                Result: {}
                Reason: {}
                =============================================""",
                symbol, check.direction(), check.point1Price(),
                check.bollingerPeriod(), check.bollingerStdDevMultiplier(),
                check.bollingerLower(), check.bollingerMiddle(),
                check.bollingerUpper(), check.point1Price(), comparison, band,
                check.bollingerPoint1Valid() ? "VALID" : "REJECTED",
                check.reason());
    }
}
