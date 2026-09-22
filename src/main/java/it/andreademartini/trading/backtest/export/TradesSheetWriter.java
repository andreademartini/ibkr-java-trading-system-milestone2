package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.orders.TakeProfitAllocation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Component
public class TradesSheetWriter {

    private static final String[] HEADERS = {
            "Trade ID",
            "Symbol",
            "Strategy",
            "Direction",
            "Signal Date",
            "Entry Date",
            "Exit Date",
            "Entry Price",
            "Initial Stop",
            "Final Stop",
            "Initial Quantity",
            "Final Quantity",
            "Capital Invested",
            "Maximum Risk",
            "Effective Risk",
            "Risk %",
            "TP1 Price",
            "TP1 Quantity",
            "TP1 Status",
            "TP2 Price",
            "TP2 Quantity",
            "TP2 Status",
            "TP3 Price",
            "TP3 Quantity",
            "TP3 Status",
            "Gross PnL",
            "Commission",
            "Slippage",
            "Net PnL",
            "Return %",
            "R Multiple",
            "Exit Reason",
            "Trade Status",
            "Duration Bars",
            "Detail Sheet",
            "MFE",
            "MAE",
            "MFE R",
            "MAE R",
            "Point 1",
            "Point 2",
            "Point 3",
            "Point 3 Date",
            "TTE Bar Number",
            "TTE Bar Open",
            "TTE Bar High",
            "TTE Bar Low",
            "TTE Bar Close",
            "TTE Candidate",
            "TTE Entry",
            "TTE Initial Stop",
            "Room To Point2",
            "TP1 Source",
            "Target Profit %",
            "Target Profit Amount",
            "Distance Entry To Point2",
            "Calculated Quantity",
            "Expected Profit At Point2",
            "Actual Stop Risk Amount",
            "Actual Stop Risk %",
            "Point 2 Date",
            "Point 3 High",
            "Point 3 Low",
            "Delta",
            "Delta Formula",
            "Actual Entry Price",
            "Entry Buffer Ticks",
            "Point 3 Range Risk Per Share",
            "Point 3 Range Risk Amount",
            "Actual Order Risk Per Share",
            "Actual Order Risk Amount",
            "TP1 %",
            "TP2 %",
            "TP3 %",
            "Progressive Stop Events"
    };

    public void write(
            XSSFWorkbook workbook,
            BacktestExportRun run,
            ExcelStyleFactory styles) {
        Sheet sheet = workbook.createSheet("TRADES");
        Row header = sheet.createRow(0);
        ExcelSheetSupport.header(header, styles.header(), HEADERS);

        int rowIndex = 1;
        for (BacktestExportTrade trade : run.trades()) {
            writeTradeRow(workbook, sheet, rowIndex++, trade, styles);
        }

        sheet.createFreezePane(0, 1);
        if (rowIndex > 1) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0,
                    rowIndex - 1,
                    0,
                    HEADERS.length - 1));
        }
        ExcelSheetSupport.autoSize(sheet, HEADERS.length);
    }

    private void writeTradeRow(
            XSSFWorkbook workbook,
            Sheet sheet,
            int rowIndex,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        Row row = sheet.createRow(rowIndex);
        int column = 0;
        ExcelSheetSupport.documentHyperlink(
                workbook,
                row.createCell(column++),
                trade.tradeId(),
                trade.tradeId(),
                "A1",
                styles.hyperlink());
        ExcelSheetSupport.cell(row, column++, trade.symbol(), styles.text());
        ExcelSheetSupport.cell(row, column++, trade.strategy(), styles.text());
        ExcelSheetSupport.cell(row, column++, trade.direction(), styles.text());
        ExcelSheetSupport.cell(row, column++, trade.signalAt(), styles.dateTime());
        ExcelSheetSupport.cell(row, column++, trade.entryAt(), styles.dateTime());
        ExcelSheetSupport.cell(row, column++, trade.exitAt(), styles.dateTime());
        ExcelSheetSupport.cell(row, column++, trade.entryPrice(), styles.price());
        ExcelSheetSupport.cell(row, column++, trade.initialStopPrice(), styles.price());
        ExcelSheetSupport.cell(row, column++, trade.finalStopPrice(), styles.price());
        ExcelSheetSupport.cell(row, column++, trade.initialQuantity(), styles.integer());
        ExcelSheetSupport.cell(row, column++, trade.remainingQuantity(), styles.integer());
        ExcelSheetSupport.cell(row, column++, trade.entryPrice() * trade.initialQuantity(), styles.money());
        ExcelSheetSupport.cell(row, column++, trade.maximumRisk(), styles.money());
        ExcelSheetSupport.cell(row, column++, trade.effectiveRisk(), styles.money());
        ExcelSheetSupport.cell(row, column++, trade.riskPercent() / 100.0, styles.percent());
        writeTakeProfitCells(row, column, trade, styles);
        column += 9;
        ExcelSheetSupport.cell(row, column++, trade.grossPnl(), styles.money());
        ExcelSheetSupport.cell(row, column++, trade.commission(), styles.money());
        if (trade.slippage() == null) {
            ExcelSheetSupport.cell(row, column++, "N/A", styles.notAvailable());
        } else {
            ExcelSheetSupport.cell(row, column++, trade.slippage(), styles.money());
        }
        ExcelSheetSupport.cell(row, column++, trade.netPnl(), styles.money());
        ExcelSheetSupport.cell(row, column++, trade.returnPercent() / 100.0, styles.percent());
        ExcelSheetSupport.cell(row, column++, trade.rMultiple(), styles.money());
        ExcelSheetSupport.cell(row, column++, trade.exitReason(), styles.text());
        ExcelSheetSupport.cell(row, column++, trade.tradeStatus(), styles.text());
        ExcelSheetSupport.cell(row, column++, trade.durationBars(), styles.integer());
        ExcelSheetSupport.documentHyperlink(
                workbook,
                row.createCell(column),
                trade.tradeId(),
                trade.tradeId(),
                "A1",
                styles.hyperlink());
        column++;
        ExcelSheetSupport.cell(row, column++, trade.mfe(), styles.price());
        ExcelSheetSupport.cell(row, column++, trade.mae(), styles.price());
        ExcelSheetSupport.cell(row, column++, trade.mfeR(), styles.money());
        ExcelSheetSupport.cell(row, column++, trade.maeR(), styles.money());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "point1Price"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "point2Price"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "point3Price"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyInstant(trade, "point3Time"), styles.dateTime());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "tteBarNumber"), styles.integer());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "tteBarOpen"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "tteBarHigh"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "tteBarLow"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "tteBarClose"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "tteEntryCandidate"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "tteEntryPrice"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "initialStopPrice"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "roomToPoint2"), styles.price());
        ExcelSheetSupport.cell(row, column++, trade.strategyData().get("tp1Source"), styles.text());
        Double targetPercent = strategyNumber(trade, "targetProfitPercent");
        ExcelSheetSupport.cell(row, column++, targetPercent == null ? null : targetPercent / 100.0,
                styles.percent());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "targetProfitAmount"), styles.money());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "distanceToPoint2"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "quantityCalculatedFromPoint2"), styles.integer());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "expectedProfitAtPoint2"), styles.money());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "actualStopRiskAmount"), styles.money());
        Double actualRiskPercent = strategyNumber(trade, "actualStopRiskPercent");
        ExcelSheetSupport.cell(row, column++, actualRiskPercent == null ? null : actualRiskPercent / 100.0,
                styles.percent());
        ExcelSheetSupport.cell(row, column++, strategyInstant(trade, "point2Date"), styles.dateTime());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "point3High"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "point3Low"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "delta"), styles.price());
        ExcelSheetSupport.cell(row, column++, trade.strategyData().get("deltaFormula"), styles.text());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "actualEntryPrice"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "entryTickBuffer"), styles.integer());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "point3RangeRiskPerShare"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "point3RangeRiskAmount"), styles.money());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "actualOrderRiskPerShare"), styles.price());
        ExcelSheetSupport.cell(row, column++, strategyNumber(trade, "actualOrderRiskAmount"), styles.money());
        column = writeStrategyPercent(row, column, trade, "tp1Percent", styles);
        column = writeStrategyPercent(row, column, trade, "tp2Percent", styles);
        column = writeStrategyPercent(row, column, trade, "tp3Percent", styles);
        ExcelSheetSupport.cell(row, column, progressiveStopEvents(trade), styles.text());
    }

    private int writeStrategyPercent(
            Row row,
            int column,
            BacktestExportTrade trade,
            String key,
            ExcelStyleFactory styles) {
        Double value = strategyNumber(trade, key);
        ExcelSheetSupport.cell(row, column, value == null ? null : value / 100.0,
                styles.percent());
        return column + 1;
    }

    private String progressiveStopEvents(BacktestExportTrade trade) {
        return trade.events().stream()
                .filter(event -> event.eventType() != null
                        && (event.eventType().contains("STOP_MOVED")
                        || event.eventType().contains("STOP_CANCEL")))
                .map(event -> event.eventType() + " @ " + event.price())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private Instant strategyInstant(BacktestExportTrade trade, String key) {
        String value = trade.strategyData().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Double strategyNumber(BacktestExportTrade trade, String key) {
        String value = trade.strategyData().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void writeTakeProfitCells(
            Row row,
            int startColumn,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        int column = startColumn;
        for (int level = 1; level <= 3; level++) {
            Optional<TakeProfitAllocation> allocation = takeProfit(trade, level);
            ExcelSheetSupport.cell(
                    row,
                    column++,
                    allocation.map(target -> target.price().doubleValue()).orElse(null),
                    styles.price());
            ExcelSheetSupport.cell(
                    row,
                    column++,
                    allocation.map(TakeProfitAllocation::quantity).orElse(null),
                    styles.integer());
            ExcelSheetSupport.cell(
                    row,
                    column++,
                    allocation.map(target -> takeProfitStatus(trade, target)).orElse("N/A"),
                    styles.text());
        }
    }

    private Optional<TakeProfitAllocation> takeProfit(
            BacktestExportTrade trade,
            int level) {
        return trade.takeProfits().stream()
                .filter(target -> target.targetNumber() == level)
                .findFirst();
    }

    private String takeProfitStatus(
            BacktestExportTrade trade,
            TakeProfitAllocation target) {
        long filled = trade.events().stream()
                .filter(event -> event.eventType().equals("EXIT - TP" + target.targetNumber())
                        || event.eventType().equals(
                        "TP" + target.targetNumber() + "_FILLED"))
                .mapToLong(BacktestExportTradeEvent::quantity)
                .sum();
        if (filled <= 0) {
            return "NOT_FILLED";
        }
        return filled >= target.quantity() ? "FILLED" : "PARTIALLY_FILLED";
    }

    public List<BacktestExportTrade> sortedTrades(BacktestExportRun run) {
        return run.trades().stream()
                .sorted(Comparator.comparingInt(BacktestExportTrade::number))
                .toList();
    }
}
