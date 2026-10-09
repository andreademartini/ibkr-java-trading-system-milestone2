package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.joeross.JoeRoss123TteOrderAction;
import it.andreademartini.trading.strategy.joeross.JoeRoss123TteParameters;
import it.andreademartini.trading.strategy.joeross.JoeRoss123TteStrategy;
import it.andreademartini.trading.strategy.joeross.JoeRoss123TteTradeSetup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class JoeRossBenchmarkAdapter {

    private final TradingSystemProperties properties;
    private final PatternOutcomeAnalyzer outcomeAnalyzer;

    public JoeRossBenchmarkAdapter(
            TradingSystemProperties properties,
            PatternOutcomeAnalyzer outcomeAnalyzer) {
        this.properties = properties;
        this.outcomeAnalyzer = outcomeAnalyzer;
    }

    public List<PatternOccurrence> analyze(
            String runId,
            String symbol,
            List<HistoricalBar> bars,
            int splitIndex,
            PatternResearchConfiguration configuration) {
        JoeRoss123TteStrategy strategy = new JoeRoss123TteStrategy(
                JoeRoss123TteParameters.from(
                        properties.getStrategy().getJoeRoss123Tte()),
                StrategyTradeMode.LONG_SHORT);
        List<PatternOccurrence> occurrences = new ArrayList<>();
        for (HistoricalBar bar : bars) {
            for (JoeRoss123TteOrderAction action : strategy.onBar(bar).orderActions()) {
                if (action.type() != JoeRoss123TteOrderAction.Type.TRIGGERED) continue;
                JoeRoss123TteTradeSetup setup = action.triggeredSetup();
                int entryIndex = setup.triggerBarIndex();
                if (entryIndex < 0 || entryIndex >= bars.size()) continue;
                PatternDirection direction = setup.direction() == TradeDirection.LONG
                        ? PatternDirection.LONG : PatternDirection.SHORT;
                if (!configuration.direction().includes(direction)) continue;
                int signalIndex = setup.tte().tteBar().index();
                PatternDetectionResult detection = new PatternDetectionResult(
                        PatternType.JOE_ROSS_123_TTE, direction,
                        setup.tte().tteBar().dateTime().toLocalDate(), signalIndex,
                        signalIndex, BigDecimal.valueOf(setup.triggerPrice()), null);
                DatasetSegment segment = signalIndex < splitIndex
                        ? DatasetSegment.IN_SAMPLE : DatasetSegment.OUT_OF_SAMPLE;
                occurrences.addAll(outcomeAnalyzer.analyzeWithPrices(
                        runId, symbol, bars, detection, segment, configuration,
                        entryIndex, BigDecimal.valueOf(setup.triggerPrice()),
                        BigDecimal.valueOf(setup.tte().initialStopPrice()),
                        setup.tte().tteBar().getClose()));
            }
        }
        return List.copyOf(occurrences);
    }
}
