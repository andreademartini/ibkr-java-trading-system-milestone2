package it.andreademartini.trading.research.pattern;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatternResearchRunRepository
        extends JpaRepository<PatternResearchRunEntity, String> {
}
