package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.ExcelExportProperties;
import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.SameBarExecutionPolicy;
import it.andreademartini.trading.config.StrategyType;
import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.strategy.StrategyTradeMode;
import it.andreademartini.trading.strategy.TradeDirection;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BacktestExcelExportServiceTest {

    private final ExcelExportProperties properties = new ExcelExportProperties();
    private final HistoricalDataService historicalDataService =
            mock(HistoricalDataService.class);
    private final BacktestExcelExportService service = new BacktestExcelExportService(
            properties,
            new SummarySheetWriter(),
            new TradesSheetWriter(),
            new TradeSheetWriter(
                    properties,
                    new TradeHistoricalDataExporter(properties, historicalDataService),
                    List.of(new MovingAverageStrategyExportDataProvider())));

    @Test
    void createsWorkbookWithSummaryTradesTradeSheetAndHyperlinks() throws Exception {
        try (XSSFWorkbook workbook = workbook(run(List.of(longThreeTakeProfitTrade(1))))) {
            assertThat(workbook.getSheet("SUMMARY")).isNotNull();
            assertThat(workbook.getSheet("TRADES")).isNotNull();
            assertThat(workbook.getSheet("T001")).isNotNull();

            Sheet trades = workbook.getSheet("TRADES");
            assertThat(trades.getRow(1).getCell(0).getHyperlink().getAddress())
                    .isEqualTo("'T001'!A1");
            assertThat(trades.getRow(1).getCell(34).getHyperlink().getAddress())
                    .isEqualTo("'T001'!A1");
            assertThat(formattedCells(trades)).contains(
                    "Delta", "Delta Formula", "Point 3 High", "Point 3 Low",
                    "Point 3 Range Risk Amount", "Actual Order Risk Amount",
                    "TP1 %", "TP2 %", "TP3 %", "Progressive Stop Events");
            assertThat(workbook.getSheet("T001").getRow(0).getCell(0)
                    .getHyperlink().getAddress()).isEqualTo("'TRADES'!A1");
            assertThat(workbook.getSheet("T001").getPaneInformation().isFreezePane())
                    .isTrue();
            assertThat(workbook.getSheet("T001").getPaneInformation()
                    .getHorizontalSplitPosition()).isEqualTo((short) 1);
        }
    }

    @Test
    void exportsLongTradeSectionsStopHistoryAndHistoricalBars() throws Exception {
        try (XSSFWorkbook workbook = workbook(run(List.of(longThreeTakeProfitTrade(1))))) {
            Sheet tradeSheet = workbook.getSheet("T001");
            List<String> text = formattedCells(tradeSheet);

            assertThat(text).contains("TRADE SUMMARY", "RISK MANAGEMENT",
                    "TAKE PROFITS", "STOP LOSS HISTORY", "TRADE EVENTS",
                    "HISTORICAL DATA");
            assertThat(text).contains("ENTRY", "INITIAL_STOP", "TP1",
                    "TP2", "TP3", "TP1_TO_BREAK_EVEN", "TP2_TO_TP1",
                    "TP3_FILLED", "TRADE_CLOSED");

            int historicalHeaderRow = findRow(tradeSheet, "Date", "Open");
            assertThat(tradeSheet.getLastRowNum() - historicalHeaderRow).isEqualTo(50);
            assertThat(formatted(tradeSheet.getRow(historicalHeaderRow + 1).getCell(0)))
                    .isEqualTo("2026-04-01");
            assertThat(formatted(tradeSheet.getRow(historicalHeaderRow + 50).getCell(0)))
                    .isEqualTo("2026-05-20");

            int eventColumn = findColumn(tradeSheet.getRow(historicalHeaderRow), "Trade Event");
            int quantityColumn = findColumn(tradeSheet.getRow(historicalHeaderRow), "Position Quantity");
            int stopColumn = findColumn(tradeSheet.getRow(historicalHeaderRow), "Current Stop");

            Row tp1Row = tradeSheet.getRow(historicalHeaderRow + 1 + 32);
            assertThat(formatted(tp1Row.getCell(eventColumn)))
                    .isEqualTo("TP1_FILLED | STOP_MOVED_TO_ENTRY");
            assertThat(tp1Row.getCell(quantityColumn).getNumericCellValue()).isEqualTo(50.0);
            assertThat(tp1Row.getCell(stopColumn).getNumericCellValue()).isEqualTo(100.0);

            Row tp2Row = tradeSheet.getRow(historicalHeaderRow + 1 + 35);
            assertThat(formatted(tp2Row.getCell(eventColumn)))
                    .isEqualTo("TP2_FILLED | STOP_MOVED_TO_TP1");
            assertThat(tp2Row.getCell(quantityColumn).getNumericCellValue()).isEqualTo(20.0);
            assertThat(tp2Row.getCell(stopColumn).getNumericCellValue()).isEqualTo(102.0);

            Row closedRow = tradeSheet.getRow(historicalHeaderRow + 1 + 39);
            assertThat(formatted(closedRow.getCell(eventColumn)))
                    .isEqualTo("TP3_FILLED | TRADE_CLOSED");
            assertThat(closedRow.getCell(quantityColumn).getNumericCellValue()).isZero();
            assertThat(formatted(closedRow.getCell(stopColumn))).isBlank();

            assertThat(text).contains("MA5", "MA20");
        }
    }

    @Test
    void exportsShortTradeAndStopLossTrade() throws Exception {
        BacktestExportRun run = run(List.of(
                shortThreeTakeProfitTrade(1),
                longStopLossTrade(2)));

        try (XSSFWorkbook workbook = workbook(run)) {
            Sheet trades = workbook.getSheet("TRADES");
            assertThat(formatted(trades.getRow(1).getCell(3))).isEqualTo("SHORT");

            List<String> shortText = formattedCells(workbook.getSheet("T001"));
            assertThat(shortText).contains("SHORT", "TP1_TO_BREAK_EVEN",
                    "TP2_TO_TP1", "TP3_FILLED", "TRADE_CLOSED");

            List<String> stopLossText = formattedCells(workbook.getSheet("T002"));
            assertThat(stopLossText).contains("STOP_FILLED", "STOP_LOSS");
        }
    }

    @Test
    void exportsPartialFillWithoutMovingStopPrice() throws Exception {
        try (XSSFWorkbook workbook = workbook(run(List.of(partialFillTrade(1))))) {
            Sheet tradeSheet = workbook.getSheet("T001");
            List<String> text = formattedCells(tradeSheet);
            assertThat(text).contains("PARTIALLY_FILLED",
                    "TP1_PARTIAL_FILL",
                    "QUANTITY_UPDATE_AFTER_PARTIAL_FILL");

            int historicalHeaderRow = findRow(tradeSheet, "Date", "Open");
            int eventColumn = findColumn(tradeSheet.getRow(historicalHeaderRow), "Trade Event");
            int quantityColumn = findColumn(tradeSheet.getRow(historicalHeaderRow), "Position Quantity");
            int stopColumn = findColumn(tradeSheet.getRow(historicalHeaderRow), "Current Stop");
            Row partialRow = tradeSheet.getRow(historicalHeaderRow + 1 + 32);

            assertThat(formatted(partialRow.getCell(eventColumn)))
                    .isEqualTo("TP1_PARTIAL_FILL | STOP_QUANTITY_UPDATED");
            assertThat(partialRow.getCell(quantityColumn).getNumericCellValue()).isEqualTo(70.0);
            assertThat(partialRow.getCell(stopColumn).getNumericCellValue()).isEqualTo(95.0);
        }
    }

    @Test
    void missingHistoricalDataDoesNotFailTheExport() {
        BacktestExportRun run = run(List.of(longThreeTakeProfitTrade(1)));

        byte[] workbook = service.export(run);

        assertThat(workbook).isNotEmpty();
    }

    private XSSFWorkbook workbook(BacktestExportRun run) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(service.export(run)));
    }

    private BacktestExportRun run(List<BacktestExportTrade> trades) {
        return new BacktestExportRun(
                "BT-001",
                "AAPL",
                StrategyType.MOVING_AVERAGE_CROSSOVER.name(),
                trades.getFirst().direction().name(),
                BarInterval.DAILY,
                bars().getFirst().timestamp(),
                bars().getLast().timestamp(),
                bars(),
                List.of(),
                trades,
                new BacktestExportMetrics(
                        10_000.0,
                        10_380.0,
                        380.0,
                        3.8,
                        1.2,
                        trades.size(),
                        1,
                        0,
                        100.0,
                        380.0,
                        0.0,
                        0.0,
                        380.0,
                        40.0,
                        0,
                        0.0,
                        2.1),
                new BacktestExportParameters(
                        MarketDataProvider.IBKR,
                        StrategyType.MOVING_AVERAGE_CROSSOVER,
                        StrategyTradeMode.LONG_ONLY,
                        5,
                        20,
                        100,
                        10_000.0,
                        0.0,
                        0.0,
                        SameBarExecutionPolicy.STOP_FIRST,
                        "test"),
                Instant.parse("2026-09-02T12:00:00Z"));
    }

    private List<BacktestExportBar> bars() {
        List<BacktestExportBar> bars = new ArrayList<>();
        Instant start = Instant.parse("2026-04-01T00:00:00Z");
        for (int index = 0; index < 50; index++) {
            double close = 100.0 + index * 0.25;
            bars.add(new BacktestExportBar(
                    index,
                    start.plusSeconds(index * 86_400L),
                    start.plusSeconds(index * 86_400L).toString().substring(0, 10),
                    close,
                    close + 1.0,
                    close - 1.0,
                    close,
                    1_000L + index,
                    BarInterval.DAILY));
        }
        return bars;
    }

    private BacktestExportTrade longThreeTakeProfitTrade(int number) {
        List<TakeProfitAllocation> takeProfits = takeProfits(102.0, 104.0, 108.0);
        return trade(
                number,
                TradeDirection.LONG,
                100.0,
                95.0,
                null,
                104.4,
                380.0,
                "TP3",
                takeProfits,
                List.of(
                        event("ENTRY", 30, 100.0, 100, 100, 95.0, 0.0, null),
                        event("INITIAL STOP", 30, 95.0, 100, 100, 95.0, 0.0, "INITIAL_STOP"),
                        event("EXIT - TP1", 32, 102.0, 50, 50, 95.0, 100.0, "TP1"),
                        event("STOP MOVED", 32, 100.0, 50, 50, 100.0, 0.0, "95.00 -> 100.00 | TP1_TO_BREAK_EVEN"),
                        event("EXIT - TP2", 35, 104.0, 30, 20, 100.0, 120.0, "TP2"),
                        event("STOP MOVED", 35, 102.0, 20, 20, 102.0, 0.0, "100.00 -> 102.00 | TP2_TO_TP1"),
                        event("EXIT - TP3", 39, 108.0, 20, 0, null, 160.0, "TP3"),
                        event("STOP CANCELLED", 39, 102.0, 0, 0, null, 0.0, "remainingQuantity = 0 | POSITION_CLOSED")));
    }

    private BacktestExportTrade shortThreeTakeProfitTrade(int number) {
        List<TakeProfitAllocation> takeProfits = takeProfits(98.0, 96.0, 92.0);
        return trade(
                number,
                TradeDirection.SHORT,
                100.0,
                105.0,
                null,
                95.6,
                380.0,
                "TP3",
                takeProfits,
                List.of(
                        event("ENTRY", 30, 100.0, 100, 100, 105.0, 0.0, null),
                        event("INITIAL STOP", 30, 105.0, 100, 100, 105.0, 0.0, "INITIAL_STOP"),
                        event("EXIT - TP1", 32, 98.0, 50, 50, 105.0, 100.0, "TP1"),
                        event("STOP MOVED", 32, 100.0, 50, 50, 100.0, 0.0, "105.00 -> 100.00 | TP1_TO_BREAK_EVEN"),
                        event("EXIT - TP2", 35, 96.0, 30, 20, 100.0, 120.0, "TP2"),
                        event("STOP MOVED", 35, 98.0, 20, 20, 98.0, 0.0, "100.00 -> 98.00 | TP2_TO_TP1"),
                        event("EXIT - TP3", 39, 92.0, 20, 0, null, 160.0, "TP3"),
                        event("STOP CANCELLED", 39, 98.0, 0, 0, null, 0.0, "remainingQuantity = 0 | POSITION_CLOSED")));
    }

    private BacktestExportTrade longStopLossTrade(int number) {
        return trade(
                number,
                TradeDirection.LONG,
                100.0,
                95.0,
                null,
                95.0,
                -500.0,
                "STOP_LOSS",
                takeProfits(102.0, 104.0, 108.0),
                List.of(
                        event("ENTRY", 30, 100.0, 100, 100, 95.0, 0.0, null),
                        event("INITIAL STOP", 30, 95.0, 100, 100, 95.0, 0.0, "INITIAL_STOP"),
                        event("EXIT - STOP_LOSS", 34, 95.0, 100, 0, null, -500.0, "STOP_LOSS"),
                        event("STOP CANCELLED", 34, 95.0, 0, 0, null, 0.0, "remainingQuantity = 0 | POSITION_CLOSED")));
    }

    private BacktestExportTrade partialFillTrade(int number) {
        return trade(
                number,
                TradeDirection.LONG,
                100.0,
                95.0,
                95.0,
                102.0,
                60.0,
                "OPEN",
                takeProfits(102.0, 104.0, 108.0),
                List.of(
                        event("ENTRY", 30, 100.0, 100, 100, 95.0, 0.0, null),
                        event("INITIAL STOP", 30, 95.0, 100, 100, 95.0, 0.0, "INITIAL_STOP"),
                        event("EXIT - TP1", 32, 102.0, 30, 70, 95.0, 60.0, "TP1 PARTIAL_FILL"),
                        event("STOP QUANTITY UPDATED", 32, 95.0, 70, 70, 95.0, 0.0, "95.00 -> 95.00 | QUANTITY_UPDATE_AFTER_PARTIAL_FILL")));
    }

    private BacktestExportTrade trade(
            int number,
            TradeDirection direction,
            double entryPrice,
            double initialStop,
            Double finalStop,
            double exitPrice,
            double netPnl,
            String exitReason,
            List<TakeProfitAllocation> takeProfits,
            List<BacktestExportTradeEvent> events) {
        return new BacktestExportTrade(
                number,
                "T" + String.format(Locale.ROOT, "%03d", number),
                "AAPL",
                StrategyType.MOVING_AVERAGE_CROSSOVER.name(),
                direction,
                bars().get(29).timestamp(),
                entryPrice,
                "TEST",
                "fixture",
                30,
                39,
                bars().get(30).timestamp(),
                exitReason.equals("OPEN") ? null : bars().get(39).timestamp(),
                100,
                finalStop == null ? 0 : 70,
                entryPrice,
                exitPrice,
                initialStop,
                finalStop,
                10_000.0,
                2.0,
                200.0,
                Math.abs(entryPrice - initialStop),
                500.0,
                entryPrice * 100,
                netPnl,
                0.0,
                null,
                netPnl,
                netPnl / (entryPrice * 100.0) * 100.0,
                netPnl / 500.0,
                exitReason,
                exitReason.equals("OPEN") ? "OPEN" : "CLOSED",
                10,
                takeProfits,
                events);
    }

    private List<TakeProfitAllocation> takeProfits(
            double tp1,
            double tp2,
            double tp3) {
        return List.of(
                new TakeProfitAllocation(1, BigDecimal.valueOf(2.0), BigDecimal.valueOf(50.0), 50, BigDecimal.valueOf(tp1)),
                new TakeProfitAllocation(2, BigDecimal.valueOf(4.0), BigDecimal.valueOf(30.0), 30, BigDecimal.valueOf(tp2)),
                new TakeProfitAllocation(3, BigDecimal.valueOf(8.0), BigDecimal.valueOf(20.0), 20, BigDecimal.valueOf(tp3)));
    }

    private BacktestExportTradeEvent event(
            String type,
            int index,
            double price,
            long quantity,
            long remainingQuantity,
            Double currentStop,
            double pnl,
            String notes) {
        return new BacktestExportTradeEvent(
                type,
                index,
                bars().get(index).timestamp(),
                price,
                quantity,
                remainingQuantity,
                currentStop,
                pnl,
                pnl,
                0.0,
                notes);
    }

    private List<String> formattedCells(Sheet sheet) {
        List<String> values = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Row row : sheet) {
            for (Cell cell : row) {
                String value = formatter.formatCellValue(cell);
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private String formatted(Cell cell) {
        return new DataFormatter(Locale.ROOT).formatCellValue(cell);
    }

    private int findRow(Sheet sheet, String firstCell, String secondCell) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Row row : sheet) {
            if (formatter.formatCellValue(row.getCell(0)).equals(firstCell)
                    && formatter.formatCellValue(row.getCell(1)).equals(secondCell)) {
                return row.getRowNum();
            }
        }
        throw new AssertionError("Header row not found: " + firstCell);
    }

    private int findColumn(Row row, String header) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Cell cell : row) {
            if (formatter.formatCellValue(cell).equals(header)) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Column not found: " + header);
    }
}
