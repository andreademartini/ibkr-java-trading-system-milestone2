package it.andreademartini.trading.research.pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PatternResearchRunView(
        String researchRunId,
        Instant createdAt,
        Instant completedAt,
        LocalDate startDate,
        LocalDate endDate,
        String symbolsJson,
        String patternsJson,
        String timeframe,
        String entryMethod,
        String stopMethod,
        String targetRsJson,
        int maxHoldingBars,
        SameBarResolution sameBarResolution,
        int inSamplePercent,
        String dataSource,
        ResearchRunStatus status,
        String configurationJson,
        List<String> warnings,
        long occurrenceRecords) {

    static PatternResearchRunView from(
            PatternResearchRunEntity entity,
            List<String> warnings,
            long occurrenceRecords) {
        return new PatternResearchRunView(entity.getResearchRunId(),
                entity.getCreatedAt(), entity.getCompletedAt(), entity.getStartDate(),
                entity.getEndDate(), entity.getSymbols(), entity.getPatterns(),
                entity.getTimeframe(), entity.getEntryMethod(), entity.getStopMethod(),
                entity.getTargetRs(), entity.getMaxHoldingBars(),
                entity.getSameBarResolution(), entity.getInSamplePercent(),
                entity.getDataSource(), entity.getStatus(),
                entity.getConfigurationJson(), warnings, occurrenceRecords);
    }
}
