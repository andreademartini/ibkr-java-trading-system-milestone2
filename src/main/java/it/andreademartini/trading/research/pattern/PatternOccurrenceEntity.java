package it.andreademartini.trading.research.pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pattern_research_occurrence", indexes = {
        @Index(name = "idx_pattern_occurrence_run", columnList = "research_run_id"),
        @Index(name = "idx_pattern_occurrence_group",
                columnList = "research_run_id,pattern,direction,symbol,target_r")
})
public class PatternOccurrenceEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "research_run_id", nullable = false, length = 36)
    private String researchRunId;
    @Column(nullable = false, length = 16)
    private String symbol;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private PatternType pattern;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8)
    private PatternDirection direction;
    @Column(nullable = false)
    private LocalDate signalDate;
    private LocalDate entryDate;
    @Column(precision = 24, scale = 10)
    private BigDecimal patternClose;
    @Column(precision = 24, scale = 10)
    private BigDecimal entryPrice;
    @Column(precision = 24, scale = 10)
    private BigDecimal stopPrice;
    @Column(precision = 24, scale = 10)
    private BigDecimal riskPerShare;
    @Column(name = "target_r", precision = 12, scale = 4)
    private BigDecimal targetR;
    @Column(precision = 24, scale = 10)
    private BigDecimal targetPrice;
    private LocalDate exitDate;
    @Column(precision = 24, scale = 10)
    private BigDecimal exitPrice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private PatternExitReason exitReason;
    @Column(precision = 18, scale = 8)
    private BigDecimal resultR;
    @Column(precision = 18, scale = 8)
    private BigDecimal mfe;
    @Column(precision = 18, scale = 8)
    private BigDecimal mae;
    @Column(nullable = false)
    private boolean sameBarAmbiguous;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private DatasetSegment datasetSegment;
    @Lob
    private String forwardOutcomesJson;

    protected PatternOccurrenceEntity() {
    }

    public PatternOccurrenceEntity(PatternOccurrence value, String forwardOutcomesJson) {
        researchRunId = value.researchRunId();
        symbol = value.symbol();
        pattern = value.pattern();
        direction = value.direction();
        signalDate = value.signalDate();
        entryDate = value.entryDate();
        patternClose = value.patternClose();
        entryPrice = value.entryPrice();
        stopPrice = value.stopPrice();
        riskPerShare = value.riskPerShare();
        targetR = value.targetR();
        targetPrice = value.targetPrice();
        exitDate = value.exitDate();
        exitPrice = value.exitPrice();
        exitReason = value.exitReason();
        resultR = value.resultR();
        mfe = value.mfe();
        mae = value.mae();
        sameBarAmbiguous = value.sameBarAmbiguous();
        datasetSegment = value.datasetSegment();
        this.forwardOutcomesJson = forwardOutcomesJson;
    }

    public Long getId() { return id; }
    public String getResearchRunId() { return researchRunId; }
    public String getSymbol() { return symbol; }
    public PatternType getPattern() { return pattern; }
    public PatternDirection getDirection() { return direction; }
    public LocalDate getSignalDate() { return signalDate; }
    public LocalDate getEntryDate() { return entryDate; }
    public BigDecimal getPatternClose() { return patternClose; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public BigDecimal getStopPrice() { return stopPrice; }
    public BigDecimal getRiskPerShare() { return riskPerShare; }
    public BigDecimal getTargetR() { return targetR; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public LocalDate getExitDate() { return exitDate; }
    public BigDecimal getExitPrice() { return exitPrice; }
    public PatternExitReason getExitReason() { return exitReason; }
    public BigDecimal getResultR() { return resultR; }
    public BigDecimal getMfe() { return mfe; }
    public BigDecimal getMae() { return mae; }
    public boolean isSameBarAmbiguous() { return sameBarAmbiguous; }
    public DatasetSegment getDatasetSegment() { return datasetSegment; }
    public String getForwardOutcomesJson() { return forwardOutcomesJson; }
}
