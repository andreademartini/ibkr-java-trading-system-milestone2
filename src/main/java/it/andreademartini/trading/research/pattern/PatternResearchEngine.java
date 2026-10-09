package it.andreademartini.trading.research.pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PatternResearchEngine {

    private final HistoricalDataService historicalDataService;
    private final Map<PatternType, PatternDetector> detectors;
    private final PatternOutcomeAnalyzer outcomeAnalyzer;
    private final JoeRossBenchmarkAdapter joeRossBenchmarkAdapter;
    private final PatternResearchPersistenceService persistence;
    private final PatternStatisticsService statisticsService;
    private final ObjectMapper objectMapper;
    private final TemporalDatasetSplitter datasetSplitter;

    public PatternResearchEngine(
            HistoricalDataService historicalDataService,
            List<PatternDetector> detectors,
            PatternOutcomeAnalyzer outcomeAnalyzer,
            JoeRossBenchmarkAdapter joeRossBenchmarkAdapter,
            PatternResearchPersistenceService persistence,
            PatternStatisticsService statisticsService,
            TemporalDatasetSplitter datasetSplitter) {
        this.historicalDataService = historicalDataService;
        this.detectors = new EnumMap<>(PatternType.class);
        detectors.forEach(detector -> this.detectors.put(
                detector.patternType(), detector));
        this.outcomeAnalyzer = outcomeAnalyzer;
        this.joeRossBenchmarkAdapter = joeRossBenchmarkAdapter;
        this.persistence = persistence;
        this.statisticsService = statisticsService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.datasetSplitter = datasetSplitter;
    }

    public PatternResearchRunView run(PatternResearchRequest request) {
        PatternResearchConfiguration configuration = PatternResearchConfiguration.from(request);
        String id = UUID.randomUUID().toString();
        persistence.createRun(id, configuration);
        List<String> warnings = new ArrayList<>();
        try {
            for (String symbol : configuration.symbols()) {
                processSymbol(id, symbol, configuration, warnings);
            }
            persistence.complete(id, warnings);
        } catch (RuntimeException exception) {
            warnings.add("RUN_FAILED: " + exception.getMessage());
            persistence.fail(id, warnings);
            throw exception;
        }
        return getRun(id);
    }

    private void processSymbol(
            String id,
            String symbol,
            PatternResearchConfiguration configuration,
            List<String> warnings) {
        List<HistoricalBar> bars;
        try {
            bars = historicalDataService.load(symbol, configuration.startDate(),
                    configuration.endDate(), configuration.timeframe());
        } catch (RuntimeException exception) {
            warnings.add(symbol + ": DATA_LOAD_FAILED: " + exception.getMessage());
            return;
        }
        if (bars.size() < 2) {
            warnings.add(symbol + ": INSUFFICIENT_HISTORICAL_DATA");
            return;
        }
        int splitIndex = datasetSplitter.splitIndex(
                bars.size(), configuration.inSamplePercent());
        List<PatternOccurrence> symbolOccurrences = new ArrayList<>();
        for (PatternType type : configuration.patterns()) {
            if (type == PatternType.JOE_ROSS_123_TTE) continue;
            PatternDetector detector = detectors.get(type);
            if (detector == null) {
                warnings.add(symbol + ": DETECTOR_NOT_AVAILABLE: " + type);
                continue;
            }
            if (!configuration.direction().includes(detector.direction())) continue;
            for (int index = 0; index < bars.size() - 1; index++) {
                detector.detect(bars, index, configuration.detectorParameters())
                        .ifPresent(detection -> symbolOccurrences.addAll(
                                outcomeAnalyzer.analyze(id, symbol, bars, detection,
                                        datasetSplitter.segment(
                                                detection.signalBarIndex(), splitIndex),
                                        configuration)));
            }
        }
        if (configuration.patterns().contains(PatternType.JOE_ROSS_123_TTE)) {
            symbolOccurrences.addAll(joeRossBenchmarkAdapter.analyze(
                    id, symbol, bars, splitIndex, configuration));
        }
        persistence.saveOccurrences(symbolOccurrences);
    }

    public PatternResearchRunView getRun(String id) {
        PatternResearchRunEntity run = persistence.requireRun(id);
        return PatternResearchRunView.from(run, persistence.warnings(run),
                persistence.occurrenceCount(id));
    }

    public List<PatternOccurrence> trades(String id) {
        return persistence.occurrences(id);
    }

    public List<PatternSummaryRow> summary(String id) {
        PatternResearchRunEntity run = persistence.requireRun(id);
        PatternResearchConfiguration configuration;
        try {
            configuration = objectMapper.readValue(
                    run.getConfigurationJson(), PatternResearchConfiguration.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Configurazione research non leggibile", exception);
        }
        return statisticsService.summarize(
                persistence.occurrences(id), configuration.minimumOccurrences());
    }
}
