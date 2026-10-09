package it.andreademartini.trading.research.pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatternResearchPersistenceService {

    private final PatternResearchRunRepository runRepository;
    private final PatternOccurrenceRepository occurrenceRepository;
    private final ObjectMapper objectMapper;

    public PatternResearchPersistenceService(
            PatternResearchRunRepository runRepository,
            PatternOccurrenceRepository occurrenceRepository) {
        this.runRepository = runRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Transactional
    public PatternResearchRunEntity createRun(
            String id, PatternResearchConfiguration configuration) {
        return runRepository.save(new PatternResearchRunEntity(
                id, configuration, json(configuration.symbols()),
                json(configuration.patterns()), json(configuration.targetRs()),
                json(configuration)));
    }

    @Transactional
    public void saveOccurrences(List<PatternOccurrence> occurrences) {
        if (occurrences.isEmpty()) return;
        occurrenceRepository.saveAll(occurrences.stream()
                .map(value -> new PatternOccurrenceEntity(
                        value, json(value.forwardOutcomes())))
                .toList());
    }

    @Transactional
    public void complete(String id, List<String> warnings) {
        PatternResearchRunEntity run = requireRun(id);
        run.complete(warnings.isEmpty()
                        ? ResearchRunStatus.COMPLETED
                        : ResearchRunStatus.COMPLETED_WITH_WARNINGS,
                json(warnings));
    }

    @Transactional
    public void fail(String id, List<String> warnings) {
        PatternResearchRunEntity run = requireRun(id);
        run.fail(json(warnings));
    }

    @Transactional(readOnly = true)
    public PatternResearchRunEntity requireRun(String id) {
        return runRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Research run non trovata: " + id));
    }

    @Transactional(readOnly = true)
    public List<PatternOccurrence> occurrences(String id) {
        requireRun(id);
        return occurrenceRepository
                .findByResearchRunIdOrderBySignalDateAscIdAsc(id)
                .stream().map(this::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public long occurrenceCount(String id) {
        requireRun(id);
        return occurrenceRepository.countByResearchRunId(id);
    }

    public List<String> warnings(PatternResearchRunEntity run) {
        return read(run.getWarningsJson(), new TypeReference<>() { });
    }

    private PatternOccurrence toDomain(PatternOccurrenceEntity entity) {
        List<ForwardOutcome> forward = read(entity.getForwardOutcomesJson(),
                new TypeReference<>() { });
        return new PatternOccurrence(entity.getId(), entity.getResearchRunId(),
                entity.getSymbol(), entity.getPattern(), entity.getDirection(),
                entity.getSignalDate(), entity.getEntryDate(), entity.getPatternClose(),
                entity.getEntryPrice(), entity.getStopPrice(), entity.getRiskPerShare(),
                entity.getTargetR(), entity.getTargetPrice(), entity.getExitDate(),
                entity.getExitPrice(), entity.getExitReason(), entity.getResultR(),
                entity.getMfe(), entity.getMae(), entity.isSameBarAmbiguous(),
                entity.getDatasetSegment(), forward);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossibile serializzare la research run", exception);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossibile leggere la research run", exception);
        }
    }
}
