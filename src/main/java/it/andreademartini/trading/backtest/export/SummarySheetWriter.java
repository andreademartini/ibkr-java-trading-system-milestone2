package it.andreademartini.trading.backtest.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import it.andreademartini.trading.strategy.joeross.JoeRossEventType;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.config.StrategyType;

@Component
public class SummarySheetWriter {

    public void write(
            XSSFWorkbook workbook,
            BacktestExportRun run,
            ExcelStyleFactory styles) {
        Sheet sheet = workbook.createSheet("SUMMARY");
        ExcelSheetSupport.sectionTitle(sheet, 0, "SUMMARY", 2, styles.title());

        int rowIndex = 2;
        rowIndex = writeMetric(sheet, rowIndex, "Backtest ID", run.backtestId(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Symbol", run.symbol(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Strategy", run.strategy(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Direction", run.direction(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Start Date", run.startAt(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "End Date", run.endAt(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Bar Interval", run.barInterval(), styles, false);
        rowIndex++;

        BacktestExportMetrics metrics = run.metrics();
        List<BacktestExportTrade> trades = run.trades();
        rowIndex = writeMetric(sheet, rowIndex, "Initial Capital", metrics.initialCapital(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Final Equity", metrics.finalEquity(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Net Profit", metrics.netProfit(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Return %", percent(metrics.totalReturnPercent()), styles, true);
        rowIndex = writeMetric(sheet, rowIndex, "CAGR", null, styles, true);
        rowIndex = writeMetric(sheet, rowIndex, "Max Drawdown", percent(metrics.maxDrawdownPercent()), styles, true);
        rowIndex = writeMetric(sheet, rowIndex, "Sharpe Ratio", null, styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Profit Factor", metrics.profitFactor(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Win Rate", percent(metrics.winRatePercent()), styles, true);
        rowIndex = writeMetric(sheet, rowIndex, "Number of Trades", metrics.tradeCount(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Winning Trades", metrics.winningTrades(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Losing Trades", metrics.losingTrades(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Average Trade", metrics.averageTrade(), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Average Winner", averageWinner(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Average Loser", averageLoser(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Largest Winner", largestWinner(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Largest Loser", largestLoser(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Expectancy", null, styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Average R", averageR(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Average MFE R", averageMfeR(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Average MAE R", averageMaeR(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Average Bars in Trade", averageBarsInTrade(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Exposure", percent(metrics.exposurePercent()), styles, true);
        rowIndex = writeMetric(sheet, rowIndex, "Buy & Hold Return", percent(metrics.buyAndHoldReturnPercent()), styles, true);
        rowIndex = writeMetric(sheet, rowIndex, "Commissioni totali", totalCommission(trades), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Slippage totale", totalSlippage(trades), styles, false);

        if (!run.strategyEvents().isEmpty()) {
            rowIndex++;
            ExcelSheetSupport.sectionTitle(
                    sheet, rowIndex++, "JOE ROSS PATTERN STATISTICS", 2, styles.section());
            if (run.parameters().strategy() == StrategyType.JOE_ROSS_123_TTE) {
                rowIndex = writePoint3TteStatistics(sheet, rowIndex, run, styles);
            } else {
                rowIndex = writeLegacyJoeRossStatistics(sheet, rowIndex, run, styles);
            }
        }

        ExcelSheetSupport.autoSize(sheet, 3);
    }

    private int writePoint3TteStatistics(
            Sheet sheet,
            int rowIndex,
            BacktestExportRun run,
            ExcelStyleFactory styles) {
        rowIndex = point1CandidateMetric(sheet, rowIndex, "Point1 candidates", run, styles);
        rowIndex = patternMetric(sheet, rowIndex, "Point1 accepted by Bollinger", run,
                JoeRossEventType.POINT1_BOLLINGER_VALID, styles);
        rowIndex = patternMetric(sheet, rowIndex, "Point1 rejected by Bollinger", run,
                JoeRossEventType.POINT1_BOLLINGER_REJECTED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "1-2-3 setups found", run,
                JoeRossEventType.JOE_ROSS_123_COMPLETED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "Point3 corrections started", run,
                JoeRossEventType.JOE_ROSS_POINT3_CORRECTION_STARTED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE Bar1 candidates", run,
                JoeRossEventType.TTE_BAR_1_CREATED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE Bar2 candidates", run,
                JoeRossEventType.TTE_BAR_2_CREATED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE Bar3 candidates", run,
                JoeRossEventType.TTE_BAR_3_CREATED, styles);
        rowIndex = triggeredBarMetric(sheet, rowIndex, "TTE triggered Bar1", run, 1, styles);
        rowIndex = triggeredBarMetric(sheet, rowIndex, "TTE triggered Bar2", run, 2, styles);
        rowIndex = triggeredBarMetric(sheet, rowIndex, "TTE triggered Bar3", run, 3, styles);
        rowIndex = writeMetric(sheet, rowIndex, "TTE expired",
                run.strategyEvents().stream().filter(event ->
                        event.type() == JoeRossEventType.TTE_EXPIRED
                                || event.type() == JoeRossEventType.SETUP_EXPIRED).count(),
                styles, false);
        rowIndex = patternMetric(sheet, rowIndex,
                "TTE missed - Point2 breakout", run,
                JoeRossEventType.TTE_MISSED_POINT2_BREAKOUT, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE rejected room", run,
                JoeRossEventType.TTE_REJECTED_INSUFFICIENT_ROOM, styles);
        rowIndex = patternMetric(sheet, rowIndex, "Setups invalidated", run,
                JoeRossEventType.SETUP_INVALIDATED, styles);
        rowIndex = directionalPatternMetric(sheet, rowIndex, "Long trades triggered", run,
                JoeRossEventType.TTE_TRIGGERED, TradeDirection.LONG, styles);
        rowIndex = directionalPatternMetric(sheet, rowIndex, "Short trades triggered", run,
                JoeRossEventType.TTE_TRIGGERED, TradeDirection.SHORT, styles);
        rowIndex = writeMetric(sheet, rowIndex, "Average Target Profit %",
                percentValue(averageStrategyValue(run.trades(), "targetProfitPercent")),
                styles, true);
        rowIndex = writeMetric(sheet, rowIndex, "Average Expected Profit at P2",
                averageStrategyValue(run.trades(), "expectedProfitAtPoint2"), styles, false);
        rowIndex = writeMetric(sheet, rowIndex, "Average Actual Risk %",
                percentValue(averageStrategyValue(run.trades(), "actualStopRiskPercent")),
                styles, true);
        rowIndex = writeMetric(sheet, rowIndex, "Average Quantity",
                run.trades().stream().mapToLong(BacktestExportTrade::initialQuantity)
                        .average().stream().boxed().findFirst().orElse(null), styles, false);
        long point2Hits = run.trades().stream().filter(this::point2Hit).count();
        rowIndex = writeMetric(sheet, rowIndex, "Number of trades reaching Point2",
                point2Hits, styles, false);
        Double hitRate = run.trades().isEmpty() ? null
                : (double) point2Hits / run.trades().size();
        return writeMetric(sheet, rowIndex, "Point2 Hit Rate", hitRate, styles, true);
    }

    private Object averageStrategyValue(
            List<BacktestExportTrade> trades, String key) {
        return trades.stream().map(trade -> trade.strategyData().get(key))
                .filter(Objects::nonNull)
                .mapToDouble(value -> Double.parseDouble(value))
                .average().stream().boxed().findFirst().orElse(null);
    }

    private Object percentValue(Object value) {
        return value instanceof Double number ? number / 100.0 : null;
    }

    private boolean point2Hit(BacktestExportTrade trade) {
        return trade.events().stream().anyMatch(event ->
                event.eventType().equals("TP1_FILLED")
                        || event.eventType().equals("EXIT - TP1"));
    }

    private int point1CandidateMetric(
            Sheet sheet,
            int rowIndex,
            String label,
            BacktestExportRun run,
            ExcelStyleFactory styles) {
        long count = run.strategyEvents().stream()
                .filter(event -> event.type() == JoeRossEventType.POINT1_CANDIDATE)
                .count();
        return writeMetric(sheet, rowIndex, label, count, styles, false);
    }

    private int writeLegacyJoeRossStatistics(
            Sheet sheet,
            int rowIndex,
            BacktestExportRun run,
            ExcelStyleFactory styles) {
        rowIndex = patternMetric(sheet, rowIndex, "1-2-3 found", run,
                JoeRossEventType.JOE_ROSS_123_COMPLETED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "Point2 breakouts", run,
                JoeRossEventType.POINT2_BREAKOUT, styles);
        rowIndex = patternMetric(sheet, rowIndex, "Ross Hooks", run,
                JoeRossEventType.ROSS_HOOK_CREATED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "Valid TTE candidates", run,
                JoeRossEventType.TTE_ENTRY_CANDIDATE, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE triggered", run,
                JoeRossEventType.TTE_TRIGGERED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE expired", run,
                JoeRossEventType.TTE_EXPIRED, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE rejected room", run,
                JoeRossEventType.TTE_REJECTED_INSUFFICIENT_ROOM, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE rejected gap", run,
                JoeRossEventType.TTE_REJECTED_GAP, styles);
        rowIndex = patternMetric(sheet, rowIndex, "TTE missed RH broken", run,
                JoeRossEventType.TTE_MISSED_RH_BROKEN, styles);
        rowIndex = patternMetric(sheet, rowIndex, "Setups invalidated", run,
                JoeRossEventType.SETUP_INVALIDATED, styles);
        rowIndex = directionalPatternMetric(sheet, rowIndex, "Long trades triggered", run,
                JoeRossEventType.TTE_TRIGGERED, TradeDirection.LONG, styles);
        return directionalPatternMetric(sheet, rowIndex, "Short trades triggered", run,
                JoeRossEventType.TTE_TRIGGERED, TradeDirection.SHORT, styles);
    }

    private int triggeredBarMetric(
            Sheet sheet,
            int rowIndex,
            String label,
            BacktestExportRun run,
            int barNumber,
            ExcelStyleFactory styles) {
        String marker = "tteBar=" + barNumber;
        long count = run.strategyEvents().stream()
                .filter(event -> event.type() == JoeRossEventType.TTE_TRIGGERED)
                .filter(event -> event.details() != null
                        && event.details().contains(marker))
                .count();
        return writeMetric(sheet, rowIndex, label, count, styles, false);
    }

    private int patternMetric(
            Sheet sheet,
            int rowIndex,
            String label,
            BacktestExportRun run,
            JoeRossEventType type,
            ExcelStyleFactory styles) {
        long count = run.strategyEvents().stream()
                .filter(event -> event.type() == type)
                .count();
        return writeMetric(sheet, rowIndex, label, count, styles, false);
    }

    private int directionalPatternMetric(
            Sheet sheet,
            int rowIndex,
            String label,
            BacktestExportRun run,
            JoeRossEventType type,
            TradeDirection direction,
            ExcelStyleFactory styles) {
        long count = run.strategyEvents().stream()
                .filter(event -> event.type() == type
                        && event.direction() == direction)
                .count();
        return writeMetric(sheet, rowIndex, label, count, styles, false);
    }

    private int writeMetric(
            Sheet sheet,
            int rowIndex,
            String label,
            Object value,
            ExcelStyleFactory styles,
            boolean percentage) {
        Row row = sheet.createRow(rowIndex);
        ExcelSheetSupport.cell(row, 0, label, styles.label());
        if (value == null) {
            ExcelSheetSupport.cell(row, 1, "N/A", styles.notAvailable());
        } else {
            ExcelSheetSupport.cell(
                    row,
                    1,
                    value,
                    percentage ? styles.percent() : styleFor(value, styles));
        }
        return rowIndex + 1;
    }

    private Object averageWinner(List<BacktestExportTrade> trades) {
        return trades.stream()
                .mapToDouble(BacktestExportTrade::netPnl)
                .filter(value -> value > 0)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private Object averageLoser(List<BacktestExportTrade> trades) {
        return trades.stream()
                .mapToDouble(BacktestExportTrade::netPnl)
                .filter(value -> value < 0)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private Object largestWinner(List<BacktestExportTrade> trades) {
        return trades.stream()
                .map(BacktestExportTrade::netPnl)
                .filter(value -> value > 0)
                .max(Double::compareTo)
                .orElse(null);
    }

    private Object largestLoser(List<BacktestExportTrade> trades) {
        return trades.stream()
                .map(BacktestExportTrade::netPnl)
                .filter(value -> value < 0)
                .min(Double::compareTo)
                .orElse(null);
    }

    private Object averageR(List<BacktestExportTrade> trades) {
        return trades.stream()
                .map(BacktestExportTrade::rMultiple)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private Object averageMfeR(List<BacktestExportTrade> trades) {
        return averageNullable(trades.stream()
                .map(BacktestExportTrade::mfeR)
                .toList());
    }

    private Object averageMaeR(List<BacktestExportTrade> trades) {
        return averageNullable(trades.stream()
                .map(BacktestExportTrade::maeR)
                .toList());
    }

    private Object averageBarsInTrade(List<BacktestExportTrade> trades) {
        return trades.stream()
                .mapToInt(BacktestExportTrade::durationBars)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private Object averageNullable(List<Double> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private double totalCommission(List<BacktestExportTrade> trades) {
        return trades.stream()
                .mapToDouble(BacktestExportTrade::commission)
                .sum();
    }

    private Object totalSlippage(List<BacktestExportTrade> trades) {
        double total = trades.stream()
                .map(BacktestExportTrade::slippage)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        return trades.stream().anyMatch(trade -> trade.slippage() != null) ? total : null;
    }

    private static double percent(double value) {
        return value / 100.0;
    }

    private static org.apache.poi.ss.usermodel.CellStyle styleFor(
            Object value,
            ExcelStyleFactory styles) {
        if (value instanceof Number) {
            return styles.money();
        }
        if (value instanceof java.time.Instant) {
            return styles.dateTime();
        }
        return styles.text();
    }
}
