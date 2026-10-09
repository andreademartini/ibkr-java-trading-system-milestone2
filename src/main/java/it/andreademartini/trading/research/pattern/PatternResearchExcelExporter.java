package it.andreademartini.trading.research.pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Service
public class PatternResearchExcelExporter {

    private static final String[] SUMMARY_HEADERS = {
            "Pattern", "Direction", "Symbol", "Target R", "Occurrences",
            "Valid Trades", "Wins", "Losses", "Timeouts", "Win Rate",
            "Profit Factor", "Expectancy R", "Average R", "Median R",
            "Average Winner R", "Average Loser R", "MFE Average R",
            "MFE Median R", "MAE Average R", "MAE Median R",
            "Maximum Drawdown R", "Max Consecutive Wins",
            "Max Consecutive Losses", "Same Bar Ambiguities",
            "IS Win Rate", "OOS Win Rate", "IS PF", "OOS PF",
            "IS Expectancy", "OOS Expectancy", "Stability", "Warnings"
    };

    public byte[] export(
            PatternResearchRunView run,
            List<PatternSummaryRow> summary,
            List<PatternOccurrence> trades) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            writeSummary(workbook.createSheet("PATTERN_SUMMARY"),
                    summary.stream().filter(row -> "ALL_SYMBOLS".equals(row.symbol())).toList(),
                    header, PatternSummaryRow::overall);
            writeSummary(workbook.createSheet("SYMBOL_BREAKDOWN"),
                    summary.stream().filter(row -> !"ALL_SYMBOLS".equals(row.symbol())).toList(),
                    header, PatternSummaryRow::overall);
            writeSummary(workbook.createSheet("IN_SAMPLE"), summary, header,
                    PatternSummaryRow::inSample);
            writeSummary(workbook.createSheet("OUT_OF_SAMPLE"), summary, header,
                    PatternSummaryRow::outOfSample);
            writeTrades(workbook.createSheet("TRADES"), trades, header);
            writeConfiguration(workbook.createSheet("CONFIGURATION"), run, header);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Impossibile creare Excel pattern research", exception);
        } finally {
            workbook.dispose();
        }
    }

    public String fileName(PatternResearchRunView run) {
        return "pattern-research-" + run.researchRunId() + ".xlsx";
    }

    private void writeSummary(
            Sheet sheet, List<PatternSummaryRow> rows, CellStyle header,
            Function<PatternSummaryRow, PatternMetrics> selector) {
        writeHeader(sheet, header, SUMMARY_HEADERS);
        int rowIndex = 1;
        for (PatternSummaryRow value : rows) {
            PatternMetrics metrics = selector.apply(value);
            Row row = sheet.createRow(rowIndex++);
            Object[] cells = {value.pattern(), value.direction(), value.symbol(), value.targetR(),
                    metrics.occurrences(), metrics.validTrades(), metrics.wins(), metrics.losses(),
                    metrics.timeouts(), metrics.winRate(), metrics.profitFactor(),
                    metrics.expectancyR(), metrics.averageR(), metrics.medianR(),
                    metrics.averageWinnerR(), metrics.averageLoserR(), metrics.mfeAverage(),
                    metrics.mfeMedian(), metrics.maeAverage(), metrics.maeMedian(),
                    metrics.maximumDrawdown(), metrics.maximumConsecutiveWins(),
                    metrics.maximumConsecutiveLosses(), metrics.sameBarAmbiguities(),
                    value.inSample().winRate(), value.outOfSample().winRate(),
                    value.inSample().profitFactor(), value.outOfSample().profitFactor(),
                    value.inSample().expectancyR(), value.outOfSample().expectancyR(),
                    value.stabilityStatus(), String.join(", ", value.warnings())};
            writeRow(row, cells);
        }
        finish(sheet, SUMMARY_HEADERS.length);
    }

    private void writeTrades(
            Sheet sheet, List<PatternOccurrence> trades, CellStyle header) {
        String[] headers = {"Symbol", "Pattern", "Direction", "Signal Date",
                "Entry Date", "Pattern Close", "Entry", "Stop", "Risk Per Share",
                "Target R", "Target", "Exit Date", "Exit", "Exit Reason",
                "Result R", "MFE R", "MAE R", "Same Bar Ambiguous", "IS/OOS"};
        writeHeader(sheet, header, headers);
        int rowIndex = 1;
        for (PatternOccurrence value : trades) {
            writeRow(sheet.createRow(rowIndex++), new Object[]{value.symbol(), value.pattern(),
                    value.direction(), value.signalDate(), value.entryDate(), value.patternClose(),
                    value.entryPrice(), value.stopPrice(), value.riskPerShare(), value.targetR(),
                    value.targetPrice(), value.exitDate(), value.exitPrice(), value.exitReason(),
                    value.resultR(), value.mfe(), value.mae(), value.sameBarAmbiguous(),
                    value.datasetSegment()});
        }
        finish(sheet, headers.length);
    }

    private void writeConfiguration(
            Sheet sheet, PatternResearchRunView run, CellStyle header) {
        writeHeader(sheet, header, "Field", "Value");
        Object[][] values = {{"researchRunId", run.researchRunId()},
                {"createdAt", run.createdAt()}, {"completedAt", run.completedAt()},
                {"startDate", run.startDate()}, {"endDate", run.endDate()},
                {"symbols", run.symbolsJson()}, {"patterns", run.patternsJson()},
                {"timeframe", run.timeframe()}, {"entryMethod", run.entryMethod()},
                {"stopMethod", run.stopMethod()}, {"targetRs", run.targetRsJson()},
                {"maxHoldingBars", run.maxHoldingBars()},
                {"sameBarResolution", run.sameBarResolution()},
                {"inSamplePercent", run.inSamplePercent()},
                {"dataSource", run.dataSource()}, {"status", run.status()},
                {"warnings", run.warnings().toString()},
                {"configurationJson", run.configurationJson()}};
        for (int index = 0; index < values.length; index++) {
            writeRow(sheet.createRow(index + 1), values[index]);
        }
        finish(sheet, 2);
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle style, String... headers) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
    }

    private void writeRow(Row row, Object[] values) {
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            Object value = values[index];
            if (value == null) cell.setBlank();
            else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else if (value instanceof BigDecimal decimal) cell.setCellValue(decimal.doubleValue());
            else if (value instanceof LocalDate date) cell.setCellValue(date.toString());
            else cell.setCellValue(String.valueOf(value));
        }
    }

    private void finish(Sheet sheet, int columns) {
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(0, sheet.getLastRowNum()), 0, columns - 1));
        for (int index = 0; index < columns; index++) {
            sheet.setColumnWidth(index, index == 0 ? 6200 : 4200);
        }
    }
}
