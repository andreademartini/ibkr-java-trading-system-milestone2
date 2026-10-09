package it.andreademartini.trading.research.pattern;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PatternResearchExcelExporterTest {

    @Test
    void exportsAllRequiredSheetsAndRawTrade() throws Exception {
        PatternMetrics metrics = new PatternMetrics(1, 1, 1, 0, 0,
                BigDecimal.ONE, null, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, null, 1, 0,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0);
        PatternSummaryRow summary = new PatternSummaryRow(
                PatternType.HAMMER, PatternDirection.LONG, "ALL_SYMBOLS",
                BigDecimal.ONE, metrics, metrics, metrics,
                StabilityStatus.STABLE, List.of());
        PatternOccurrence trade = new PatternOccurrence(1L, "run", "AAPL",
                PatternType.HAMMER, PatternDirection.LONG,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2),
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("9"),
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("11"),
                LocalDate.of(2024, 1, 3), new BigDecimal("11"),
                PatternExitReason.TARGET, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, false, DatasetSegment.OUT_OF_SAMPLE, List.of());
        PatternResearchRunView run = new PatternResearchRunView(
                "run", Instant.now(), Instant.now(), LocalDate.of(2010, 1, 1),
                LocalDate.of(2024, 1, 1), "[\"AAPL\"]", "[\"HAMMER\"]",
                "DAILY", "NEXT_BAR_OPEN", "PATTERN_EXTREME", "[1]", 20,
                SameBarResolution.STOP_FIRST, 70, "HISTORICAL_DATA_SERVICE",
                ResearchRunStatus.COMPLETED, "{}", List.of(), 1);

        byte[] bytes = new PatternResearchExcelExporter().export(
                run, List.of(summary), List.of(trade));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(6, workbook.getNumberOfSheets());
            for (String name : List.of("PATTERN_SUMMARY", "SYMBOL_BREAKDOWN",
                    "IN_SAMPLE", "OUT_OF_SAMPLE", "TRADES", "CONFIGURATION")) {
                assertNotNull(workbook.getSheet(name));
            }
            assertEquals(1, workbook.getSheet("TRADES").getLastRowNum());
        }
    }
}
