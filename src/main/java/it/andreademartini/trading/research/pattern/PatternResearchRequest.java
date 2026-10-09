package it.andreademartini.trading.research.pattern;

import it.andreademartini.trading.marketdata.BarInterval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PatternResearchRequest(
        LocalDate startDate,
        LocalDate endDate,
        List<String> symbols,
        List<PatternType> patterns,
        ResearchDirection direction,
        List<BigDecimal> targetRs,
        List<Integer> forwardBars,
        Integer maxHoldingBars,
        SameBarResolution sameBarResolution,
        Integer inSamplePercent,
        Integer minimumOccurrences,
        EntryMethod entryMethod,
        StopMethod stopMethod,
        BarInterval timeframe,
        BigDecimal tickSize,
        Integer stopBufferTicks,
        Boolean closeTimeoutAtLastClose,
        PatternDetectorParameters detectorParameters) {
}
