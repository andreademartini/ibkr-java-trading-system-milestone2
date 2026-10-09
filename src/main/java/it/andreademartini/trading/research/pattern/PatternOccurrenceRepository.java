package it.andreademartini.trading.research.pattern;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatternOccurrenceRepository
        extends JpaRepository<PatternOccurrenceEntity, Long> {

    List<PatternOccurrenceEntity> findByResearchRunIdOrderBySignalDateAscIdAsc(
            String researchRunId);

    long countByResearchRunId(String researchRunId);
}
