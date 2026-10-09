package it.andreademartini.trading.research.pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pattern_research_run")
public class PatternResearchRunEntity {

    @Id
    @Column(length = 36)
    private String researchRunId;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant completedAt;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Lob @Column(nullable = false)
    private String symbols;
    @Lob @Column(nullable = false)
    private String patterns;
    @Column(nullable = false, length = 16)
    private String timeframe;
    @Column(nullable = false, length = 32)
    private String entryMethod;
    @Column(nullable = false, length = 32)
    private String stopMethod;
    @Lob @Column(nullable = false)
    private String targetRs;
    @Column(nullable = false)
    private int maxHoldingBars;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SameBarResolution sameBarResolution;
    @Column(nullable = false)
    private int inSamplePercent;
    @Column(nullable = false, length = 64)
    private String dataSource;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResearchRunStatus status;
    @Lob @Column(nullable = false)
    private String configurationJson;
    @Lob
    private String warningsJson;

    protected PatternResearchRunEntity() {
    }

    public PatternResearchRunEntity(
            String researchRunId,
            PatternResearchConfiguration configuration,
            String symbols,
            String patterns,
            String targetRs,
            String configurationJson) {
        this.researchRunId = researchRunId;
        this.createdAt = Instant.now();
        this.startDate = configuration.startDate();
        this.endDate = configuration.endDate();
        this.symbols = symbols;
        this.patterns = patterns;
        this.timeframe = configuration.timeframe().name();
        this.entryMethod = configuration.entryMethod().name();
        this.stopMethod = configuration.stopMethod().name();
        this.targetRs = targetRs;
        this.maxHoldingBars = configuration.maxHoldingBars();
        this.sameBarResolution = configuration.sameBarResolution();
        this.inSamplePercent = configuration.inSamplePercent();
        this.dataSource = "HISTORICAL_DATA_SERVICE";
        this.status = ResearchRunStatus.RUNNING;
        this.configurationJson = configurationJson;
        this.warningsJson = "[]";
    }

    public void complete(ResearchRunStatus finalStatus, String warningsJson) {
        this.status = finalStatus;
        this.warningsJson = warningsJson;
        this.completedAt = Instant.now();
    }

    public void fail(String warningsJson) {
        complete(ResearchRunStatus.FAILED, warningsJson);
    }

    public String getResearchRunId() { return researchRunId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getSymbols() { return symbols; }
    public String getPatterns() { return patterns; }
    public String getTimeframe() { return timeframe; }
    public String getEntryMethod() { return entryMethod; }
    public String getStopMethod() { return stopMethod; }
    public String getTargetRs() { return targetRs; }
    public int getMaxHoldingBars() { return maxHoldingBars; }
    public SameBarResolution getSameBarResolution() { return sameBarResolution; }
    public int getInSamplePercent() { return inSamplePercent; }
    public String getDataSource() { return dataSource; }
    public ResearchRunStatus getStatus() { return status; }
    public String getConfigurationJson() { return configurationJson; }
    public String getWarningsJson() { return warningsJson; }
}
