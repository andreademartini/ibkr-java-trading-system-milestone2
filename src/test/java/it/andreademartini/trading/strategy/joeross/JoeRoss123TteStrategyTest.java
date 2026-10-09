package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JoeRoss123TteStrategyTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void longFindsP1P2AndStartsPoint3CorrectionCausally() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.TTE_BAR);

        List<JoeRossEvent> events = process(strategy, longBase());

        assertThat(types(events)).containsSubsequence(
                JoeRossEventType.JOE_ROSS_POINT1_FOUND,
                JoeRossEventType.JOE_ROSS_POINT2_FOUND,
                JoeRossEventType.JOE_ROSS_POINT3_CORRECTION_STARTED,
                JoeRossEventType.TTE_BAR_1_CREATED,
                JoeRossEventType.TTE_ENTRY_CANDIDATE);
        assertThat(events).noneMatch(event -> event.type().name().contains("ROSS_HOOK"));
    }

    @Test
    void longTriggersBar1OnlyOnFollowingBar() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.TTE_BAR);
        List<HistoricalBar> bars = longBase();
        JoeRoss123TteBarResult candidate = processResult(strategy, bars);

        assertThat(candidate.events()).noneMatch(e -> e.type() == JoeRossEventType.TTE_TRIGGERED);
        assertThat(candidate.events()).noneMatch(
                e -> e.type() == JoeRossEventType.POINT3_CONFIRMED);
        assertThat(candidate.events()).anyMatch(
                e -> e.type() == JoeRossEventType.TTE_ENTRY_CANDIDATE);
        JoeRoss123TteBarResult trigger = strategy.onBar(bar(5, 104, 106.02, 99, 105));

        assertTriggered(trigger, 1, 106.01, 99.99, 100.0);
        assertThat(types(trigger.events())).containsSubsequence(
                JoeRossEventType.POINT3_CONFIRMED,
                JoeRossEventType.TTE_TRIGGERED);
        assertThat(trigger.events()).filteredOn(
                e -> e.type() == JoeRossEventType.POINT3_CONFIRMED)
                .singleElement().extracting(JoeRossEvent::barIndex).isEqualTo(4);
    }

    @Test
    void longUpdatesAndTriggersBar2() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.TTE_BAR);
        process(strategy, longBase());
        JoeRoss123TteBarResult second = strategy.onBar(bar(5, 103, 105, 98, 100));

        assertThat(types(second.events())).containsSubsequence(
                JoeRossEventType.TTE_ORDER_CANCELLED,
                JoeRossEventType.TTE_BAR_2_CREATED,
                JoeRossEventType.TTE_CANDIDATE_UPDATED);
        assertThat(second.events()).anyMatch(e -> e.type() == JoeRossEventType.TTE_CANDIDATE_UPDATED
                && e.price() == 105.01);
        JoeRoss123TteBarResult trigger = strategy.onBar(bar(6, 103, 105.02, 99, 104));
        assertTriggered(trigger, 2, 105.01, 97.99, 98.0);
    }

    @Test
    void longUpdatesAndTriggersBar3() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.TTE_BAR);
        process(strategy, longBase());
        strategy.onBar(bar(5, 103, 105, 98, 100));
        strategy.onBar(bar(6, 102, 104, 97, 99));

        JoeRoss123TteBarResult trigger = strategy.onBar(bar(7, 102, 104.02, 98, 103));
        assertTriggered(trigger, 3, 104.01, 96.99, 97.0);
    }

    @Test
    void longExpiresAfterThirdCandidateHadOneFollowingBarToTrigger() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.TTE_BAR);
        process(strategy, longBase());
        strategy.onBar(bar(5, 103, 105, 98, 100));
        strategy.onBar(bar(6, 102, 104, 97, 99));

        JoeRoss123TteBarResult expired = strategy.onBar(bar(7, 101, 103, 96, 98));
        assertThat(expired.state()).isEqualTo(JoeRoss123TteState.TTE_EXPIRED);
        assertThat(types(expired.events())).containsSubsequence(
                JoeRossEventType.TTE_ORDER_CANCELLED,
                JoeRossEventType.SETUP_EXPIRED);
    }

    @Test
    void longInvalidatesAtOrBelowPoint1() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.TTE_BAR);
        process(strategy, longBase().subList(0, 4));

        JoeRoss123TteBarResult result = strategy.onBar(bar(4, 100, 105, 90, 92));
        assertThat(result.state()).isEqualTo(JoeRoss123TteState.SETUP_INVALIDATED);
    }

    @Test
    void longPoint2BreakoutTriggersTheExistingTteFirst() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.TTE_BAR);
        process(strategy, longBase());

        JoeRoss123TteBarResult result = strategy.onBar(bar(5, 108, 111, 105, 110));
        assertThat(result.state()).isEqualTo(JoeRoss123TteState.TTE_TRIGGERED);
        assertThat(result.events()).anyMatch(
                e -> e.type() == JoeRossEventType.TTE_TRIGGERED);
        assertThat(result.orderActions()).anyMatch(
                a -> a.type() == JoeRoss123TteOrderAction.Type.TRIGGERED);
    }

    @Test
    void point3StopUsesTheCandidateBarLow() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.POINT_3);
        process(strategy, longBase());
        JoeRoss123TteBarResult second = strategy.onBar(bar(6, 103, 105, 100, 102));
        JoeRoss123TteCandidate candidate = upsert(second);

        assertThat(candidate.initialStopPrice()).isEqualTo(99.99);
        assertThat(candidate.point3AtCandidate().price()).isEqualTo(100.0);
        assertThat(candidate.point3AtCandidate().barIndex()).isEqualTo(6);
    }

    @Test
    void minimumTickIsUsedForEntryAndStop() {
        JoeRoss123TteParameters parameters = parameters(0.25, InitialStopMode.TTE_BAR);
        JoeRoss123TteStrategy strategy = new JoeRoss123TteStrategy(
                parameters, StrategyTradeMode.LONG_ONLY);
        JoeRoss123TteBarResult result = processResult(strategy, longBase());

        JoeRoss123TteCandidate candidate = upsert(result);
        assertThat(candidate.entryPrice()).isEqualTo(106.25);
        assertThat(candidate.initialStopPrice()).isEqualTo(99.75);
    }

    @Test
    void shortFindsP1P2AndTriggersBar1OnFollowingBar() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.SHORT, InitialStopMode.TTE_BAR);
        JoeRoss123TteBarResult candidateResult = processResult(strategy, shortBase());

        assertThat(types(strategy.events())).contains(
                JoeRossEventType.JOE_ROSS_POINT1_FOUND,
                JoeRossEventType.JOE_ROSS_POINT2_FOUND,
                JoeRossEventType.JOE_ROSS_POINT3_CORRECTION_STARTED);
        assertThat(candidateResult.events()).noneMatch(e -> e.type() == JoeRossEventType.TTE_TRIGGERED);
        JoeRoss123TteBarResult trigger = strategy.onBar(bar(5, 96, 101, 93.98, 95));
        assertTriggered(trigger, 1, 93.99, 100.01, 100.0);
    }

    @Test
    void shortUpdatesAndTriggersBar2AndBar3() {
        JoeRoss123TteStrategy bar2 = strategy(TradeDirection.SHORT, InitialStopMode.TTE_BAR);
        process(bar2, shortBase());
        bar2.onBar(bar(5, 97, 102, 95, 100));
        assertTriggered(bar2.onBar(bar(6, 98, 101, 94.98, 96)),
                2, 94.99, 102.01, 102.0);

        JoeRoss123TteStrategy bar3 = strategy(TradeDirection.SHORT, InitialStopMode.TTE_BAR);
        process(bar3, shortBase());
        bar3.onBar(bar(5, 97, 102, 95, 100));
        bar3.onBar(bar(6, 98, 103, 96, 101));
        assertTriggered(bar3.onBar(bar(7, 99, 102, 95.98, 97)),
                3, 95.99, 103.01, 103.0);
    }

    @Test
    void shortRealExampleCancelsBar1AndUsesBar2AsPoint3WithoutLookAhead() {
        JoeRoss123TteStrategy strategy = strategy(
                TradeDirection.SHORT, InitialStopMode.TTE_BAR);
        List<HistoricalBar> bars = List.of(
                bar(0, 28.5, 29.0, 28.0, 28.5),
                bar(1, 29.5, 31.0, 28.0, 30.0),
                bar(2, 29.0, 30.0, 28.0, 28.5),
                bar(3, 28.0, 28.5, 27.70, 27.8),
                bar(4, 28.0, 28.2, 27.86, 28.1));
        JoeRoss123TteBarResult firstCandidate = processResult(strategy, bars);
        JoeRoss123TteCandidate bar1 = upsert(firstCandidate);

        assertThat(bar1.entryPrice()).isEqualTo(27.85);
        assertThat(firstCandidate.events()).noneMatch(
                event -> event.type() == JoeRossEventType.POINT3_CONFIRMED);

        JoeRoss123TteBarResult replacement = strategy.onBar(
                bar(5, 28.6, 29.03, 28.52, 28.8));
        JoeRoss123TteCandidate bar2 = upsert(replacement);
        assertThat(types(replacement.events())).containsSubsequence(
                JoeRossEventType.TTE_ORDER_CANCELLED,
                JoeRossEventType.TTE_BAR_2_CREATED,
                JoeRossEventType.TTE_CANDIDATE_UPDATED);
        assertThat(bar2.entryPrice()).isEqualTo(28.51);
        assertThat(bar2.point2Delta()).isEqualTo(0.82);
        assertThat(bar2.initialStopPrice()).isEqualTo(29.04);

        JoeRoss123TteBarResult triggered = strategy.onBar(
                bar(6, 28.7, 28.8, 28.50, 28.6));
        JoeRoss123TteTradeSetup setup = triggered.orderActions().stream()
                .filter(action -> action.type()
                        == JoeRoss123TteOrderAction.Type.TRIGGERED)
                .map(JoeRoss123TteOrderAction::triggeredSetup)
                .findFirst().orElseThrow();
        assertThat(setup.point3AtEntry().barIndex()).isEqualTo(5);
        assertThat(setup.tte().tteBar().high()).isEqualTo(29.03);
        assertThat(setup.tte().tteBar().low()).isEqualTo(28.52);
        assertThat(types(triggered.events())).containsSubsequence(
                JoeRossEventType.POINT3_CONFIRMED,
                JoeRossEventType.TTE_TRIGGERED);
    }

    @Test
    void shortExpiresInvalidatesAndMissesDirectPoint2Breakout() {
        JoeRoss123TteStrategy expired = strategy(TradeDirection.SHORT, InitialStopMode.TTE_BAR);
        process(expired, shortBase());
        expired.onBar(bar(5, 97, 102, 95, 100));
        expired.onBar(bar(6, 98, 103, 96, 101));
        assertThat(expired.onBar(bar(7, 99, 104, 97, 102)).state())
                .isEqualTo(JoeRoss123TteState.TTE_EXPIRED);

        JoeRoss123TteStrategy invalid = strategy(TradeDirection.SHORT, InitialStopMode.TTE_BAR);
        process(invalid, shortBase().subList(0, 4));
        assertThat(invalid.onBar(bar(4, 105, 110, 95, 108)).state())
                .isEqualTo(JoeRoss123TteState.SETUP_INVALIDATED);

        JoeRoss123TteStrategy missed = strategy(TradeDirection.SHORT, InitialStopMode.TTE_BAR);
        process(missed, shortBase());
        assertThat(missed.onBar(bar(5, 92, 100, 89, 90)).state())
                .isEqualTo(JoeRoss123TteState.TTE_TRIGGERED);
    }

    @Test
    void shortPoint3StopUsesTheCandidateBarHigh() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.SHORT, InitialStopMode.POINT_3);
        process(strategy, shortBase());
        JoeRoss123TteCandidate candidate = upsert(
                strategy.onBar(bar(6, 98, 100, 95, 99)));

        assertThat(candidate.initialStopPrice()).isEqualTo(100.01);
        assertThat(candidate.point3AtCandidate().price()).isEqualTo(100.0);
        assertThat(candidate.point3AtCandidate().barIndex()).isEqualTo(6);
    }

    @Test
    void futureBarsNeverCreateOrModifyPastTteResults() {
        JoeRoss123TteStrategy strategy = strategy(TradeDirection.LONG, InitialStopMode.TTE_BAR);
        process(strategy, longBase());
        List<JoeRossEvent> prefix = strategy.events();

        strategy.onBar(bar(6, 103, 105, 98, 100));
        strategy.onBar(bar(7, 103, 105.02, 99, 104));

        assertThat(strategy.events().subList(0, prefix.size())).containsExactlyElementsOf(prefix);
        assertThat(prefix).noneMatch(event -> event.type() == JoeRossEventType.TTE_TRIGGERED);
        assertThat(strategy.events()).noneMatch(event -> event.type().name().contains("ROSS_HOOK"));
    }

    @Test
    void enabledBollingerFilterAcceptsLongPoint1AndAllowsSetup() {
        JoeRoss123TteStrategy strategy = new JoeRoss123TteStrategy(
                filteredParameters(true, 2, Point1PriceMode.EXTREME),
                StrategyTradeMode.LONG_ONLY);

        process(strategy, longBase());

        assertThat(types(strategy.events())).contains(
                JoeRossEventType.POINT1_BOLLINGER_VALID,
                JoeRossEventType.JOE_ROSS_POINT1_FOUND,
                JoeRossEventType.JOE_ROSS_123_COMPLETED);
        assertThat(strategy.statistics().point1AcceptedByBollinger()).isPositive();
        assertThat(strategy.bollingerPoint1Checks().getFirst().bollingerDistance())
                .isLessThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void enabledBollingerFilterRejectsInsideLongPoint1AndDoesNotStartSetup() {
        JoeRoss123TteStrategy strategy = new JoeRoss123TteStrategy(
                filteredParameters(true, 2, Point1PriceMode.EXTREME),
                StrategyTradeMode.LONG_ONLY);
        List<HistoricalBar> bars = List.of(
                bar(0, 98, 102, 95, 100),
                bar(1, 96, 99, 92, 94),
                bar(2, 95, 100, 94, 98));

        process(strategy, bars);

        assertThat(types(strategy.events())).contains(
                JoeRossEventType.POINT1_BOLLINGER_REJECTED);
        assertThat(types(strategy.events())).doesNotContain(
                JoeRossEventType.JOE_ROSS_POINT1_FOUND);
        assertThat(strategy.bollingerPoint1Checks().getFirst().reason())
                .isEqualTo(BollingerPoint1Filter.INSIDE_REASON);
    }

    @Test
    void insufficientBollingerHistoryRejectsPoint1() {
        JoeRoss123TteStrategy strategy = new JoeRoss123TteStrategy(
                filteredParameters(true, 20, Point1PriceMode.EXTREME),
                StrategyTradeMode.LONG_ONLY);

        process(strategy, longBase().subList(0, 3));

        assertThat(strategy.bollingerPoint1Checks()).singleElement()
                .extracting(BollingerPoint1Check::reason)
                .isEqualTo(BollingerPoint1Filter.HISTORY_REASON);
        assertThat(strategy.statistics().point1RejectedByBollinger()).isEqualTo(1);
    }

    @Test
    void closeModeRejectsCandleWhoseLowCrossesButCloseIsInside() {
        JoeRoss123TteStrategy strategy = new JoeRoss123TteStrategy(
                filteredParameters(true, 2, Point1PriceMode.CLOSE),
                StrategyTradeMode.LONG_ONLY);

        process(strategy, longBase().subList(0, 3));

        assertThat(strategy.bollingerPoint1Checks()).singleElement()
                .satisfies(check -> {
                    assertThat(check.point1Price()).isEqualByComparingTo("94");
                    assertThat(check.bollingerPoint1Valid()).isFalse();
                });
    }

    private JoeRoss123TteStrategy strategy(
            TradeDirection direction,
            InitialStopMode stopMode) {
        StrategyTradeMode mode = direction == TradeDirection.LONG
                ? StrategyTradeMode.LONG_ONLY : StrategyTradeMode.SHORT_ONLY;
        return new JoeRoss123TteStrategy(parameters(0.01, stopMode), mode);
    }

    private JoeRoss123TteParameters parameters(double tick, InitialStopMode stopMode) {
        return new JoeRoss123TteParameters(
                1, tick, 3, 1, 1, stopMode,
                false, "R", 0.5, false, 1, false,
                BollingerPoint1FilterParameters.disabledDefaults());
    }

    private JoeRoss123TteParameters filteredParameters(
            boolean enabled,
            int period,
            Point1PriceMode mode) {
        return new JoeRoss123TteParameters(
                1, 0.01, 3, 1, 1, InitialStopMode.TTE_BAR,
                false, "R", 0.5, false, 1, false,
                new BollingerPoint1FilterParameters(
                        enabled, period, new BigDecimal("2.0"),
                        BollingerSource.CLOSE, mode));
    }

    private List<HistoricalBar> longBase() {
        return List.of(
                bar(0, 98, 102, 95, 100),
                bar(1, 96, 99, 90, 94),
                bar(2, 95, 100, 94, 98),
                bar(3, 100, 110, 99, 108),
                bar(4, 104, 106, 100, 102));
    }

    private List<HistoricalBar> shortBase() {
        return List.of(
                bar(0, 102, 105, 98, 101),
                bar(1, 108, 110, 100, 109),
                bar(2, 106, 106, 99, 102),
                bar(3, 94, 101, 90, 92),
                bar(4, 96, 100, 94, 98));
    }

    private List<JoeRossEvent> process(
            JoeRoss123TteStrategy strategy,
            List<HistoricalBar> bars) {
        bars.forEach(strategy::onBar);
        return strategy.events();
    }

    private JoeRoss123TteBarResult processResult(
            JoeRoss123TteStrategy strategy,
            List<HistoricalBar> bars) {
        JoeRoss123TteBarResult result = null;
        for (HistoricalBar bar : bars) {
            result = strategy.onBar(bar);
        }
        return result;
    }

    private List<JoeRossEventType> types(List<JoeRossEvent> events) {
        return events.stream().map(JoeRossEvent::type).toList();
    }

    private JoeRoss123TteCandidate upsert(JoeRoss123TteBarResult result) {
        return result.orderActions().stream()
                .filter(action -> action.type()
                        == JoeRoss123TteOrderAction.Type.UPSERT_CANDIDATE)
                .map(JoeRoss123TteOrderAction::candidate)
                .findFirst()
                .orElseThrow();
    }

    private void assertTriggered(
            JoeRoss123TteBarResult result,
            int tteBar,
            double entry,
            double stop,
            double point3) {
        JoeRoss123TteTradeSetup setup = result.orderActions().stream()
                .filter(action -> action.type() == JoeRoss123TteOrderAction.Type.TRIGGERED)
                .map(JoeRoss123TteOrderAction::triggeredSetup)
                .findFirst()
                .orElseThrow();
        assertThat(setup.tte().tteBarNumber()).isEqualTo(tteBar);
        assertThat(setup.triggerPrice()).isEqualTo(entry);
        assertThat(setup.tte().initialStopPrice()).isEqualTo(stop);
        assertThat(setup.point3AtEntry().price()).isEqualTo(point3);
    }

    private HistoricalBar bar(
            int index,
            double open,
            double high,
            double low,
            double close) {
        return new HistoricalBar(index, START.plus(index, ChronoUnit.DAYS),
                String.valueOf(index), open, high, low, close, 0);
    }
}
