package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JoeRoss123RossHookTteStrategyTest {

    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void recognizesLong123BreakoutRossHookAndTteBar1Trigger() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.LONG_ONLY, false);

        feed(strategy, longBars(1));

        assertThat(types(strategy)).containsSubsequence(
                JoeRossEventType.JOE_ROSS_POINT1_FOUND,
                JoeRossEventType.JOE_ROSS_POINT2_FOUND,
                JoeRossEventType.JOE_ROSS_POINT3_FOUND,
                JoeRossEventType.JOE_ROSS_123_COMPLETED,
                JoeRossEventType.POINT2_BREAKOUT,
                JoeRossEventType.ROSS_HOOK_CREATED,
                JoeRossEventType.TTE_BAR_1_CREATED,
                JoeRossEventType.TTE_ENTRY_CANDIDATE,
                JoeRossEventType.TTE_TRIGGERED);
        JoeRossEvent trigger = event(strategy, JoeRossEventType.TTE_TRIGGERED);
        assertThat(trigger.barIndex()).isEqualTo(9);
        assertThat(trigger.price()).isEqualTo(112.01);
    }

    @Test
    void triggersLongFromTteBar2AndReplacesBar1Candidate() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.LONG_ONLY, false);

        feed(strategy, longBars(2));

        assertThat(event(strategy, JoeRossEventType.TTE_TRIGGERED).details())
                .contains("tteBar=2");
        assertThat(strategy.events().stream()
                .filter(event -> event.type() == JoeRossEventType.TTE_ENTRY_CANDIDATE))
                .hasSize(2);
    }

    @Test
    void triggersLongFromTteBar3() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.LONG_ONLY, false);

        feed(strategy, longBars(3));

        assertThat(event(strategy, JoeRossEventType.TTE_TRIGGERED).details())
                .contains("tteBar=3");
    }

    @Test
    void expiresLongOnlyAfterBar3CandidateHadOneCausalTriggerBar() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.LONG_ONLY, false);

        feed(strategy, longBars(0));

        assertThat(types(strategy)).contains(JoeRossEventType.TTE_EXPIRED);
        assertThat(types(strategy)).doesNotContain(JoeRossEventType.TTE_TRIGGERED);
    }

    @Test
    void longInitialStopIsBelowTheTteBarByConfiguredTick() {
        TradersTrickEntryDetector detector = tte(TradeDirection.LONG, false, true);
        HistoricalBar tteBar = bar(10, 110, 112, 107, 108);

        JoeRossOrderCandidate candidate = detector.start(tteBar)
                .actions().getFirst().candidate();

        assertThat(candidate.entryPrice()).isEqualTo(112.01);
        assertThat(candidate.initialStopPrice()).isEqualTo(106.99);
    }

    @Test
    void recognizesShort123BreakoutRossHookAndTteBar1Trigger() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.SHORT_ONLY, false);

        feed(strategy, shortBars(1));

        assertThat(types(strategy)).contains(
                JoeRossEventType.JOE_ROSS_123_COMPLETED,
                JoeRossEventType.POINT2_BREAKOUT,
                JoeRossEventType.ROSS_HOOK_CREATED,
                JoeRossEventType.TTE_BAR_1_CREATED,
                JoeRossEventType.TTE_TRIGGERED);
        assertThat(event(strategy, JoeRossEventType.TTE_TRIGGERED).price())
                .isEqualTo(87.99);
    }

    @Test
    void triggersShortFromTteBar2() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.SHORT_ONLY, false);
        feed(strategy, shortBars(2));
        assertThat(event(strategy, JoeRossEventType.TTE_TRIGGERED).details())
                .contains("tteBar=2");
    }

    @Test
    void triggersShortFromTteBar3() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.SHORT_ONLY, false);
        feed(strategy, shortBars(3));
        assertThat(event(strategy, JoeRossEventType.TTE_TRIGGERED).details())
                .contains("tteBar=3");
    }

    @Test
    void shortInitialStopIsAboveTheTteBarByConfiguredTick() {
        TradersTrickEntryDetector detector = tte(TradeDirection.SHORT, false, true);
        HistoricalBar tteBar = bar(10, 90, 93, 88, 92);
        JoeRossOrderCandidate candidate = detector.start(tteBar)
                .actions().getFirst().candidate();
        assertThat(candidate.entryPrice()).isEqualTo(87.99);
        assertThat(candidate.initialStopPrice()).isEqualTo(93.01);
    }

    @Test
    void expiresShortAfterTheThirdCandidateTriggerWindow() {
        TradersTrickEntryDetector detector = tte(TradeDirection.SHORT, false, true);
        detector.start(bar(10, 90, 93, 88, 92));
        detector.onBar(bar(11, 91, 94, 89, 93));
        detector.onBar(bar(12, 92, 95, 90, 94));

        TradersTrickEntryDetector.TteResult result = detector.onBar(
                bar(13, 93, 96, 91, 95));

        assertThat(result.events()).extracting(JoeRossEvent::type)
                .containsExactly(JoeRossEventType.TTE_EXPIRED);
    }

    @Test
    void rejectsShortGapBelowSellStop() {
        TradersTrickEntryDetector detector = tte(TradeDirection.SHORT, false, true);
        detector.start(bar(10, 90, 93, 88, 92));

        TradersTrickEntryDetector.TteResult result = detector.onBar(
                bar(11, 87, 88, 85, 86));

        assertThat(result.events()).extracting(JoeRossEvent::type)
                .containsExactly(JoeRossEventType.TTE_REJECTED_GAP);
    }

    @Test
    void neverTriggersFromTheTteBarUsedToCreateTheCandidate() {
        TradersTrickEntryDetector detector = tte(TradeDirection.LONG, false, true);
        HistoricalBar tteBar = bar(10, 108, 112, 107, 111);

        TradersTrickEntryDetector.TteResult result = detector.start(tteBar);

        assertThat(result.triggeredSetup()).isNull();
        assertThat(result.events()).extracting(JoeRossEvent::type)
                .doesNotContain(JoeRossEventType.TTE_TRIGGERED);
    }

    @Test
    void rejectsGapInsteadOfGivingPerfectStopFill() {
        TradersTrickEntryDetector detector = tte(TradeDirection.LONG, false, true);
        detector.start(bar(10, 108, 112, 107, 111));

        TradersTrickEntryDetector.TteResult result = detector.onBar(
                bar(11, 113, 114, 112.5, 113.5));

        assertThat(result.events()).extracting(JoeRossEvent::type)
                .containsExactly(JoeRossEventType.TTE_REJECTED_GAP);
        assertThat(result.triggeredSetup()).isNull();
    }

    @Test
    void rejectsCandidateWithInsufficientRoomToRossHook() {
        TradersTrickEntryDetector detector = tte(TradeDirection.LONG, true, true);

        TradersTrickEntryDetector.TteResult result = detector.start(
                bar(10, 108, 112, 107, 111));

        assertThat(result.events()).extracting(JoeRossEvent::type)
                .contains(JoeRossEventType.TTE_REJECTED_INSUFFICIENT_ROOM);
        assertThat(result.actions()).extracting(JoeRossOrderAction::type)
                .containsExactly(JoeRossOrderAction.Type.CANCEL_CANDIDATE);
    }

    @Test
    void recordsMissedSetupWhenRossHookBreaksWithoutAValidTteCandidate() {
        TradersTrickEntryDetector detector = tte(TradeDirection.LONG, true, true);
        detector.start(bar(10, 108, 112, 107, 111));

        TradersTrickEntryDetector.TteResult result = detector.onBar(
                bar(11, 113, 115, 112, 114));

        assertThat(result.events()).extracting(JoeRossEvent::type)
                .containsExactly(JoeRossEventType.TTE_MISSED_RH_BROKEN);
    }

    @Test
    void invalidates123WhenPoint1IsViolatedBeforePoint2Breakout() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.LONG_ONLY, false);
        List<HistoricalBar> bars = new ArrayList<>(longPrefixWithoutBreakout());
        bars.add(bar(7, 96, 105, 89, 92));

        feed(strategy, bars);

        assertThat(types(strategy)).contains(JoeRossEventType.SETUP_INVALIDATED);
        assertThat(types(strategy)).doesNotContain(JoeRossEventType.POINT2_BREAKOUT);
    }

    @Test
    void invalidatesShort123WhenPoint1IsViolatedBeforePoint2Breakdown() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.SHORT_ONLY, false);
        List<HistoricalBar> bars = new ArrayList<>(shortBars(1).subList(0, 7));
        bars.set(6, bar(6, 96, 98, 91, 92));
        bars.add(bar(7, 105, 111, 94, 109));

        feed(strategy, bars);

        assertThat(types(strategy)).contains(JoeRossEventType.SETUP_INVALIDATED);
        assertThat(types(strategy)).doesNotContain(JoeRossEventType.POINT2_BREAKOUT);
    }

    @Test
    void doesNotCreateRossHookWithoutPrior123AndPoint2Breakout() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.LONG_SHORT, false);
        feed(strategy, List.of(
                bar(0, 100, 102, 99, 101),
                bar(1, 101, 103, 100, 102),
                bar(2, 102, 104, 101, 103),
                bar(3, 103, 105, 102, 104),
                bar(4, 104, 106, 103, 105)));

        assertThat(types(strategy)).doesNotContain(
                JoeRossEventType.ROSS_HOOK_CREATED,
                JoeRossEventType.TTE_ENTRY_CANDIDATE,
                JoeRossEventType.TTE_TRIGGERED);
    }

    @Test
    void doesNotEnterOnPoint2OrRossHookBreakout() {
        JoeRoss123RossHookTteStrategy strategy = strategy(StrategyTradeMode.LONG_ONLY, false);
        List<HistoricalBar> bars = longBars(1);
        feed(strategy, bars.subList(0, 9));

        assertThat(types(strategy)).contains(
                JoeRossEventType.POINT2_BREAKOUT,
                JoeRossEventType.ROSS_HOOK_CREATED);
        assertThat(types(strategy)).doesNotContain(JoeRossEventType.TTE_TRIGGERED);
    }

    @Test
    void confirmedHistoryIsPrefixInvariantAndHasNoLookAheadRewrite() {
        List<HistoricalBar> allBars = longBars(0);
        int prefixLength = 9;
        JoeRoss123RossHookTteStrategy prefix = strategy(StrategyTradeMode.LONG_ONLY, false);
        JoeRoss123RossHookTteStrategy complete = strategy(StrategyTradeMode.LONG_ONLY, false);

        feed(prefix, allBars.subList(0, prefixLength));
        feed(complete, allBars.subList(0, prefixLength));
        List<JoeRossEvent> snapshotAtN = complete.events();

        assertThat(prefix.events()).containsExactlyElementsOf(snapshotAtN);

        feed(complete, allBars.subList(prefixLength, allBars.size()));

        assertThat(complete.events().subList(0, snapshotAtN.size()))
                .containsExactlyElementsOf(snapshotAtN);
    }

    @Test
    void doesNotUseThePoint3ConfirmationBarAsARetroactivePoint2Breakout() {
        JoeRoss123RossHookTteStrategy strategy =
                strategy(StrategyTradeMode.LONG_ONLY, false);
        List<HistoricalBar> bars = longBars(1);

        // Bar 6 confirms the P3 pivot at bar 5 and also has High > P2.
        // That high cannot be used because P3 is knowable only at bar 6 close.
        feed(strategy, bars.subList(0, 7));

        assertThat(types(strategy)).contains(
                JoeRossEventType.JOE_ROSS_POINT3_FOUND,
                JoeRossEventType.JOE_ROSS_123_COMPLETED);
        assertThat(types(strategy)).doesNotContain(JoeRossEventType.POINT2_BREAKOUT);

        strategy.onBar(bars.get(7));

        JoeRossEvent breakout = event(strategy, JoeRossEventType.POINT2_BREAKOUT);
        assertThat(breakout.barIndex()).isEqualTo(7);
    }

    @Test
    void appliesConfiguredPoint2BreakoutBufferInMinimumTicks() {
        JoeRossParameters buffered = new JoeRossParameters(
                1, 0.01, 1, 3, 1, 1, false, 0.5,
                false, 1, true, false);
        JoeRoss123RossHookTteStrategy strategy =
                new JoeRoss123RossHookTteStrategy(
                        buffered, StrategyTradeMode.LONG_ONLY);

        feed(strategy, longPrefixWithoutBreakout());
        strategy.onBar(bar(7, 109, 110.00, 103, 109.9));

        assertThat(types(strategy)).doesNotContain(JoeRossEventType.POINT2_BREAKOUT);

        strategy.onBar(bar(8, 110, 110.01, 104, 110.01));

        JoeRossEvent breakout = event(strategy, JoeRossEventType.POINT2_BREAKOUT);
        assertThat(breakout.price()).isEqualTo(110.01);
        assertThat(breakout.barIndex()).isEqualTo(8);
    }

    private static TradersTrickEntryDetector tte(
            TradeDirection direction,
            boolean roomEnabled,
            boolean rejectGap) {
        SwingPoint p1 = swing(direction == TradeDirection.LONG ? SwingType.LOW : SwingType.HIGH,
                1, direction == TradeDirection.LONG ? 90 : 110);
        SwingPoint p2 = swing(direction == TradeDirection.LONG ? SwingType.HIGH : SwingType.LOW,
                3, direction == TradeDirection.LONG ? 110 : 90);
        SwingPoint p3 = swing(direction == TradeDirection.LONG ? SwingType.LOW : SwingType.HIGH,
                5, 100);
        JoeRoss123Pattern pattern = new JoeRoss123Pattern(direction, p1, p2, p3);
        RossHook hook = new RossHook(direction == TradeDirection.LONG ? 114 : 86,
                9, START.plus(9, ChronoUnit.DAYS));
        return new TradersTrickEntryDetector(
                direction,
                params(roomEnabled, rejectGap),
                pattern,
                7,
                START.plus(7, ChronoUnit.DAYS),
                p2.price(),
                hook);
    }

    private static SwingPoint swing(SwingType type, int index, double price) {
        Instant time = START.plus(index, ChronoUnit.DAYS);
        return new SwingPoint(type, index, time, price, index + 1,
                time.plus(1, ChronoUnit.DAYS));
    }

    private static JoeRoss123RossHookTteStrategy strategy(
            StrategyTradeMode mode,
            boolean roomEnabled) {
        return new JoeRoss123RossHookTteStrategy(params(roomEnabled, true), mode);
    }

    private static JoeRossParameters params(boolean roomEnabled, boolean rejectGap) {
        return new JoeRossParameters(
                1, 0.01, 0, 3, 1, 1, roomEnabled, 0.5,
                false, 1, rejectGap, false);
    }

    private static List<HistoricalBar> longPrefixWithoutBreakout() {
        return List.of(
                bar(0, 98, 102, 95, 100),
                bar(1, 96, 99, 90, 94),
                bar(2, 95, 100, 94, 98),
                bar(3, 100, 110, 99, 108),
                bar(4, 106, 107, 103, 104),
                bar(5, 104, 106, 100, 102),
                bar(6, 103, 109, 102, 108));
    }

    private static List<HistoricalBar> longBars(int triggerTteBar) {
        List<HistoricalBar> bars = new ArrayList<>(longPrefixWithoutBreakout());
        bars.set(6, bar(6, 105, 111, 102, 110));
        bars.add(bar(7, 110, 114, 108, 113));
        bars.add(bar(8, 112, 112, 107, 108));
        if (triggerTteBar == 1) {
            bars.add(bar(9, 110, 112.02, 109, 111));
            return bars;
        }
        bars.add(bar(9, 108, 111, 106, 107));
        if (triggerTteBar == 2) {
            bars.add(bar(10, 107, 111.02, 106.5, 110));
            return bars;
        }
        bars.add(bar(10, 107, 110, 105, 106));
        if (triggerTteBar == 3) {
            bars.add(bar(11, 106, 110.02, 105.5, 109));
        } else {
            bars.add(bar(11, 106, 109, 104, 105));
        }
        return bars;
    }

    private static List<HistoricalBar> shortBars(int triggerTteBar) {
        List<HistoricalBar> bars = new ArrayList<>(List.of(
                bar(0, 102, 105, 98, 100),
                bar(1, 104, 110, 101, 108),
                bar(2, 105, 106, 100, 102),
                bar(3, 100, 101, 90, 92),
                bar(4, 94, 97, 93, 96),
                bar(5, 96, 100, 94, 98),
                bar(6, 96, 98, 89, 90),
                bar(7, 90, 92, 86, 87),
                bar(8, 88, 93, 88, 92)));
        if (triggerTteBar == 1) {
            bars.add(bar(9, 90, 92, 87.98, 89));
            return bars;
        }
        bars.add(bar(9, 92, 94, 89, 93));
        if (triggerTteBar == 2) {
            bars.add(bar(10, 92, 93.5, 88.98, 90));
            return bars;
        }
        bars.add(bar(10, 93, 95, 90, 94));
        bars.add(bar(11, 93, 94, 89.98, 91));
        return bars;
    }

    private static HistoricalBar bar(
            int index, double open, double high, double low, double close) {
        return new HistoricalBar(index, START.plus(index, ChronoUnit.DAYS),
                String.valueOf(index), open, high, low, close, 0);
    }

    private static void feed(
            JoeRoss123RossHookTteStrategy strategy,
            List<HistoricalBar> bars) {
        bars.forEach(strategy::onBar);
    }

    private static List<JoeRossEventType> types(
            JoeRoss123RossHookTteStrategy strategy) {
        return strategy.events().stream().map(JoeRossEvent::type).toList();
    }

    private static JoeRossEvent event(
            JoeRoss123RossHookTteStrategy strategy,
            JoeRossEventType type) {
        return strategy.events().stream()
                .filter(event -> event.type() == type)
                .findFirst()
                .orElseThrow();
    }
}
