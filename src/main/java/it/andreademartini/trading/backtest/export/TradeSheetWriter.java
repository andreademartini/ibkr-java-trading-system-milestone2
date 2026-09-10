package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.ExcelExportProperties;
import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.strategy.TradeDirection;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class TradeSheetWriter {

    private final ExcelExportProperties properties;
    private final TradeHistoricalDataExporter historicalDataExporter;
    private final List<StrategyExportDataProvider> strategyProviders;

    public TradeSheetWriter(
            ExcelExportProperties properties,
            TradeHistoricalDataExporter historicalDataExporter,
            List<StrategyExportDataProvider> strategyProviders) {
        this.properties = properties;
        this.historicalDataExporter = historicalDataExporter;
        this.strategyProviders = strategyProviders == null
                ? List.of()
                : List.copyOf(strategyProviders);
    }

    public void write(
            XSSFWorkbook workbook,
            BacktestExportRun run,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        Sheet sheet = workbook.createSheet(trade.tradeId());
        ExcelSheetSupport.documentHyperlink(
                workbook,
                sheet.createRow(0).createCell(0),
                "BACK TO TRADES",
                "TRADES",
                "A1",
                styles.hyperlink());

        int rowIndex = 2;
        rowIndex = writeTradeSummary(sheet, rowIndex, trade, styles);
        rowIndex = writeRiskManagement(sheet, rowIndex + 1, trade, styles);
        rowIndex = writeTakeProfits(sheet, rowIndex + 1, trade, styles);
        rowIndex = writeStopHistory(sheet, rowIndex + 1, trade, styles);
        rowIndex = writeTradeEvents(sheet, rowIndex + 1, trade, styles);
        int historicalHeaderRow = rowIndex + 2;
        writeHistoricalData(sheet, historicalHeaderRow, run, trade, styles);

        sheet.createFreezePane(0, 1);
    }

    private int writeTradeSummary(
            Sheet sheet,
            int rowIndex,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        ExcelSheetSupport.sectionTitle(sheet, rowIndex++, "TRADE SUMMARY", 3, styles.section());
        rowIndex = keyValue(sheet, rowIndex, "Trade ID", trade.tradeId(), styles.text(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Symbol", trade.symbol(), styles.text(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Strategy", trade.strategy(), styles.text(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Direction", trade.direction(), styles.text(), styles);
        rowIndex++;
        rowIndex = keyValue(sheet, rowIndex, "Signal Date", trade.signalAt(), styles.dateTime(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Entry Date", trade.entryAt(), styles.dateTime(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Exit Date", trade.exitAt(), styles.dateTime(), styles);
        rowIndex++;
        rowIndex = keyValue(sheet, rowIndex, "Entry Price", trade.entryPrice(), styles.price(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Average Exit Price", trade.exitPrice(), styles.price(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Initial Quantity", trade.initialQuantity(), styles.integer(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Remaining Quantity finale", trade.remainingQuantity(), styles.integer(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Capital Invested", trade.entryPrice() * trade.initialQuantity(), styles.money(), styles);
        rowIndex++;
        rowIndex = keyValue(sheet, rowIndex, "Gross PnL", trade.grossPnl(), styles.money(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Net PnL", trade.netPnl(), styles.money(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Return %", trade.returnPercent() / 100.0, styles.percent(), styles);
        rowIndex = keyValue(sheet, rowIndex, "R Multiple", trade.rMultiple(), styles.money(), styles);
        rowIndex = keyValue(sheet, rowIndex, "MFE", trade.mfe(), styles.price(), styles);
        rowIndex = keyValue(sheet, rowIndex, "MAE", trade.mae(), styles.price(), styles);
        rowIndex = keyValue(sheet, rowIndex, "MFE R", trade.mfeR(), styles.money(), styles);
        rowIndex = keyValue(sheet, rowIndex, "MAE R", trade.maeR(), styles.money(), styles);
        if (!trade.strategyData().isEmpty()) {
            ExcelSheetSupport.sectionTitle(
                    sheet, rowIndex++, "JOE ROSS 1-2-3 / RH / TTE", 3, styles.section());
            for (java.util.Map.Entry<String, String> item
                    : trade.strategyData().entrySet()) {
                rowIndex = keyValue(
                        sheet, rowIndex, item.getKey(), item.getValue(), styles.text(), styles);
            }
        }
        rowIndex++;
        rowIndex = keyValue(sheet, rowIndex, "Trade Status", trade.tradeStatus(), styles.text(), styles);
        return keyValue(sheet, rowIndex, "Exit Reason", trade.exitReason(), styles.text(), styles);
    }

    private int writeRiskManagement(
            Sheet sheet,
            int rowIndex,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        ExcelSheetSupport.sectionTitle(sheet, rowIndex++, "RISK MANAGEMENT", 3, styles.section());
        rowIndex = keyValue(sheet, rowIndex, "Trading Capital", trade.tradingCapital(), styles.money(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Risk Percent", trade.riskPercent() / 100.0, styles.percent(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Maximum Risk", trade.maximumRisk(), styles.money(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Risk Per Share", trade.riskPerShare(), styles.money(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Initial Stop Price", trade.initialStopPrice(), styles.price(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Initial Stop Distance", Math.abs(trade.entryPrice() - trade.initialStopPrice()), styles.price(), styles);
        rowIndex = keyValue(sheet, rowIndex, "Effective Risk", trade.effectiveRisk(), styles.money(), styles);
        return keyValue(sheet, rowIndex, "Capital Required", trade.capitalRequired(), styles.money(), styles);
    }

    private int writeTakeProfits(
            Sheet sheet,
            int rowIndex,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        ExcelSheetSupport.sectionTitle(sheet, rowIndex++, "TAKE PROFITS", 9, styles.section());
        Row header = sheet.createRow(rowIndex++);
        ExcelSheetSupport.header(
                header,
                styles.header(),
                "Level",
                "Target %",
                "Target Price",
                "Original Quantity",
                "Filled Quantity",
                "Remaining Quantity",
                "Status",
                "Execution Date",
                "Execution Price",
                "Realized PnL");

        long runningRemaining = trade.initialQuantity();
        for (int level = 1; level <= 3; level++) {
            Optional<TakeProfitAllocation> allocation = takeProfit(trade, level);
            if (allocation.isEmpty()) {
                Row row = sheet.createRow(rowIndex++);
                ExcelSheetSupport.cell(row, 0, "TP" + level, styles.text());
                ExcelSheetSupport.cell(row, 6, "N/A", styles.notAvailable());
                continue;
            }

            TakeProfitAllocation target = allocation.get();
            TakeProfitExecution execution = takeProfitExecution(trade, level);
            runningRemaining = Math.max(0L, runningRemaining - execution.filledQuantity());

            Row row = sheet.createRow(rowIndex++);
            ExcelSheetSupport.cell(row, 0, "TP" + level, styles.text());
            ExcelSheetSupport.cell(row, 1, target.profitPercent().doubleValue() / 100.0, styles.percent());
            ExcelSheetSupport.cell(row, 2, target.price().doubleValue(), styles.price());
            ExcelSheetSupport.cell(row, 3, target.quantity(), styles.integer());
            ExcelSheetSupport.cell(row, 4, execution.filledQuantity(), styles.integer());
            ExcelSheetSupport.cell(row, 5, runningRemaining, styles.integer());
            ExcelSheetSupport.cell(row, 6, takeProfitStatus(target, execution), styles.text());
            ExcelSheetSupport.cell(row, 7, execution.executedAt(), styles.dateTime());
            ExcelSheetSupport.cell(row, 8, execution.executionPrice(), styles.price());
            ExcelSheetSupport.cell(row, 9, execution.realizedPnl(), styles.money());
        }
        return rowIndex;
    }

    private int writeStopHistory(
            Sheet sheet,
            int rowIndex,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        ExcelSheetSupport.sectionTitle(sheet, rowIndex++, "STOP LOSS HISTORY", 6, styles.section());
        Row header = sheet.createRow(rowIndex++);
        ExcelSheetSupport.header(
                header,
                styles.header(),
                "Date/Time",
                "Reason",
                "Old Stop",
                "New Stop",
                "Old Quantity",
                "New Quantity",
                "Trigger Event");

        for (StopHistoryRow stopRow : stopHistory(trade)) {
            Row row = sheet.createRow(rowIndex++);
            ExcelSheetSupport.cell(row, 0, stopRow.occurredAt(), styles.dateTime());
            ExcelSheetSupport.cell(row, 1, stopRow.reason(), styles.text());
            ExcelSheetSupport.cell(row, 2, stopRow.oldStop(), styles.price());
            ExcelSheetSupport.cell(row, 3, stopRow.newStop(), styles.price());
            ExcelSheetSupport.cell(row, 4, stopRow.oldQuantity(), styles.integer());
            ExcelSheetSupport.cell(row, 5, stopRow.newQuantity(), styles.integer());
            ExcelSheetSupport.cell(row, 6, stopRow.triggerEvent(), styles.text());
        }
        return rowIndex;
    }

    private int writeTradeEvents(
            Sheet sheet,
            int rowIndex,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        ExcelSheetSupport.sectionTitle(sheet, rowIndex++, "TRADE EVENTS", 6, styles.section());
        Row header = sheet.createRow(rowIndex++);
        ExcelSheetSupport.header(
                header,
                styles.header(),
                "Date/Time",
                "Event Type",
                "Price",
                "Quantity",
                "Remaining Quantity",
                "PnL",
                "Notes");

        for (BacktestExportTradeEvent event : orderedEventsWithSignal(trade)) {
            String eventType = displayEventType(event);
            Row row = sheet.createRow(rowIndex++);
            ExcelSheetSupport.cell(row, 0, event.occurredAt(), styles.dateTime());
            ExcelSheetSupport.cell(row, 1, eventType, styles.event(eventType));
            ExcelSheetSupport.cell(row, 2, event.price(), styles.price());
            ExcelSheetSupport.cell(row, 3, event.quantity(), styles.integer());
            ExcelSheetSupport.cell(row, 4, event.remainingQuantity(), styles.integer());
            ExcelSheetSupport.cell(row, 5, event.netPnl(), styles.money());
            ExcelSheetSupport.cell(row, 6, event.notes(), styles.text());
        }
        return rowIndex;
    }

    private void writeHistoricalData(
            Sheet sheet,
            int rowIndex,
            BacktestExportRun run,
            BacktestExportTrade trade,
            ExcelStyleFactory styles) {
        ExcelSheetSupport.sectionTitle(sheet, rowIndex++, "HISTORICAL DATA", 15, styles.section());

        List<StrategyExportDataProvider> providers = activeProviders(run);
        List<String> strategyColumns = providers.stream()
                .flatMap(provider -> provider.columns(run).stream())
                .toList();
        List<String> headers = new ArrayList<>(List.of(
                "Date",
                "Open",
                "High",
                "Low",
                "Close",
                "Volume",
                "Trade Phase",
                "Trade Event",
                "Position Quantity",
                "Entry Price",
                "Current Stop",
                "TP1",
                "TP2",
                "TP3",
                "Realized PnL",
                "Unrealized PnL"));
        headers.addAll(strategyColumns);

        int headerRowIndex = rowIndex;
        Row header = sheet.createRow(rowIndex++);
        ExcelSheetSupport.header(header, styles.header(), headers.toArray(String[]::new));

        List<BacktestExportBar> bars = historicalDataExporter.tradeWindow(run, trade);
        Map<Integer, List<BacktestExportTradeEvent>> eventsByIndex =
                eventsByIndex(orderedEventsWithSignal(trade));
        HistoricalState state = new HistoricalState();
        for (BacktestExportBar bar : bars) {
            List<BacktestExportTradeEvent> events =
                    eventsByIndex.getOrDefault(bar.index(), List.of());
            for (BacktestExportTradeEvent event : events) {
                applyHistoricalEventState(state, trade, event);
            }

            String phase = tradePhase(trade, bar);
            String eventText = events.stream()
                    .map(this::displayEventType)
                    .collect(java.util.stream.Collectors.joining(" | "));
            Row row = sheet.createRow(rowIndex++);
            int column = 0;
            ExcelSheetSupport.cell(row, column++, bar.timestamp(), styles.date());
            ExcelSheetSupport.cell(row, column++, bar.open(), styles.price());
            ExcelSheetSupport.cell(row, column++, bar.high(), styles.price());
            ExcelSheetSupport.cell(row, column++, bar.low(), styles.price());
            ExcelSheetSupport.cell(row, column++, bar.close(), styles.price());
            ExcelSheetSupport.cell(row, column++, bar.volume(), styles.integer());
            ExcelSheetSupport.cell(row, column++, phase, styles.phase(phase));
            ExcelSheetSupport.cell(row, column++, eventText, styles.event(eventText));
            ExcelSheetSupport.cell(row, column++, state.positionQuantityValue(), styles.integer());
            ExcelSheetSupport.cell(row, column++, inOrAfterTrade(trade, bar) ? trade.entryPrice() : null, styles.price());
            ExcelSheetSupport.cell(row, column++, state.currentStop(), styles.price());
            ExcelSheetSupport.cell(row, column++, takeProfitPrice(trade, 1), styles.price());
            ExcelSheetSupport.cell(row, column++, takeProfitPrice(trade, 2), styles.price());
            ExcelSheetSupport.cell(row, column++, takeProfitPrice(trade, 3), styles.price());
            ExcelSheetSupport.cell(row, column++, state.realizedPnl(), styles.money());
            ExcelSheetSupport.cell(row, column++, unrealizedPnl(trade, bar, state.positionQuantity()), styles.money());

            for (StrategyExportDataProvider provider : providers) {
                for (Object value : provider.values(run, bar)) {
                    ExcelSheetSupport.cell(row, column++, value, styles.price());
                }
            }
        }

        if (rowIndex > headerRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(
                    headerRowIndex,
                    rowIndex - 1,
                    0,
                    headers.size() - 1));
        }
        ExcelSheetSupport.autoSize(sheet, headers.size());
    }

    private int keyValue(
            Sheet sheet,
            int rowIndex,
            String label,
            Object value,
            org.apache.poi.ss.usermodel.CellStyle valueStyle,
            ExcelStyleFactory styles) {
        Row row = sheet.createRow(rowIndex);
        ExcelSheetSupport.cell(row, 0, label, styles.label());
        if (value == null) {
            ExcelSheetSupport.cell(row, 1, "N/A", styles.notAvailable());
        } else {
            ExcelSheetSupport.cell(row, 1, value, valueStyle);
        }
        return rowIndex + 1;
    }

    private List<StrategyExportDataProvider> activeProviders(BacktestExportRun run) {
        if (!properties.isIncludeStrategyIndicators()) {
            return List.of();
        }
        return strategyProviders.stream()
                .filter(provider -> provider.supports(run))
                .toList();
    }

    private List<BacktestExportTradeEvent> orderedEventsWithSignal(
            BacktestExportTrade trade) {
        List<BacktestExportTradeEvent> events = new ArrayList<>();
        if (trade.signalAt() != null) {
            events.add(new BacktestExportTradeEvent(
                    "SIGNAL",
                    Math.max(0, trade.entryIndex() - 1),
                    trade.signalAt(),
                    trade.signalPrice(),
                    0,
                    0,
                    null,
                    0.0,
                    0.0,
                    0.0,
                    signalNotes(trade)));
        }
        events.addAll(trade.events());
        events.sort(Comparator
                .comparingInt(BacktestExportTradeEvent::index)
                .thenComparing(event -> event.occurredAt() == null
                        ? Instant.EPOCH
                        : event.occurredAt()));
        return events;
    }

    private String signalNotes(BacktestExportTrade trade) {
        List<String> notes = new ArrayList<>();
        if (trade.signalSource() != null && !trade.signalSource().isBlank()) {
            notes.add(trade.signalSource());
        }
        if (trade.signalReason() != null && !trade.signalReason().isBlank()) {
            notes.add(trade.signalReason());
        }
        return String.join(" | ", notes);
    }

    private Map<Integer, List<BacktestExportTradeEvent>> eventsByIndex(
            List<BacktestExportTradeEvent> events) {
        Map<Integer, List<BacktestExportTradeEvent>> grouped = new LinkedHashMap<>();
        for (BacktestExportTradeEvent event : events) {
            grouped.computeIfAbsent(event.index(), ignored -> new ArrayList<>())
                    .add(event);
        }
        return grouped;
    }

    private void applyHistoricalEventState(
            HistoricalState state,
            BacktestExportTrade trade,
            BacktestExportTradeEvent event) {
        String eventType = displayEventType(event);
        if (eventType.equals("SIGNAL")) {
            return;
        }
        if (eventType.equals("ENTRY")) {
            state.positionQuantity(event.remainingQuantity() > 0
                    ? event.remainingQuantity()
                    : trade.initialQuantity());
            state.currentStop(event.currentStopPrice());
            state.addRealizedPnl(event.netPnl());
            return;
        }
        if (eventType.equals("INITIAL_STOP")) {
            state.currentStop(event.currentStopPrice() == null
                    ? event.price()
                    : event.currentStopPrice());
            if (state.positionQuantity() == null && event.remainingQuantity() > 0) {
                state.positionQuantity(event.remainingQuantity());
            }
            return;
        }
        if (eventType.startsWith("TP")
                || eventType.equals("STOP_FILLED")
                || eventType.equals("SIGNAL_EXIT")
                || eventType.equals("MANUAL_EXIT")) {
            state.positionQuantity(event.remainingQuantity());
            state.currentStop(event.currentStopPrice());
            state.addRealizedPnl(event.netPnl());
            return;
        }
        if (eventType.startsWith("STOP_MOVED")
                || eventType.equals("STOP_QUANTITY_UPDATED")
                || eventType.equals("STOP_UPDATE_REJECTED")) {
            state.positionQuantity(event.remainingQuantity());
            state.currentStop(event.currentStopPrice());
            return;
        }
        if (eventType.equals("TRADE_CLOSED")) {
            state.positionQuantity(0L);
            state.currentStop(null);
        }
    }

    private String tradePhase(
            BacktestExportTrade trade,
            BacktestExportBar bar) {
        if (bar.index() < trade.entryIndex()) {
            return "PRE_TRADE";
        }
        if (trade.exitAt() != null && bar.index() > trade.exitIndex()) {
            return "POST_TRADE";
        }
        return "IN_TRADE";
    }

    private boolean inOrAfterTrade(
            BacktestExportTrade trade,
            BacktestExportBar bar) {
        return bar.index() >= trade.entryIndex();
    }

    private Double unrealizedPnl(
            BacktestExportTrade trade,
            BacktestExportBar bar,
            Long positionQuantity) {
        if (positionQuantity == null || positionQuantity <= 0) {
            return null;
        }
        if (trade.direction() == TradeDirection.LONG) {
            return (bar.close() - trade.entryPrice()) * positionQuantity;
        }
        return (trade.entryPrice() - bar.close()) * positionQuantity;
    }

    private Optional<TakeProfitAllocation> takeProfit(
            BacktestExportTrade trade,
            int level) {
        return trade.takeProfits().stream()
                .filter(target -> target.targetNumber() == level)
                .findFirst();
    }

    private Double takeProfitPrice(BacktestExportTrade trade, int level) {
        return takeProfit(trade, level)
                .map(target -> target.price().doubleValue())
                .orElse(null);
    }

    private TakeProfitExecution takeProfitExecution(
            BacktestExportTrade trade,
            int level) {
        List<BacktestExportTradeEvent> events = trade.events().stream()
                .filter(event -> event.eventType().equals("EXIT - TP" + level))
                .toList();
        long filledQuantity = events.stream()
                .mapToLong(BacktestExportTradeEvent::quantity)
                .sum();
        double realizedPnl = events.stream()
                .mapToDouble(BacktestExportTradeEvent::netPnl)
                .sum();
        BacktestExportTradeEvent lastEvent = events.stream()
                .max(Comparator
                        .comparingInt(BacktestExportTradeEvent::index)
                        .thenComparing(event -> event.occurredAt() == null
                                ? Instant.EPOCH
                                : event.occurredAt()))
                .orElse(null);
        return new TakeProfitExecution(
                filledQuantity,
                lastEvent == null ? null : lastEvent.occurredAt(),
                lastEvent == null ? null : lastEvent.price(),
                realizedPnl);
    }

    private String takeProfitStatus(
            TakeProfitAllocation target,
            TakeProfitExecution execution) {
        if (execution.filledQuantity() <= 0) {
            return "NOT_FILLED";
        }
        return execution.filledQuantity() >= target.quantity()
                ? "FILLED"
                : "PARTIALLY_FILLED";
    }

    private List<StopHistoryRow> stopHistory(BacktestExportTrade trade) {
        List<StopHistoryRow> rows = new ArrayList<>();
        Double lastStop = null;
        Long lastQuantity = null;
        for (BacktestExportTradeEvent event : trade.events()) {
            String eventType = event.eventType();
            if (eventType.equals("INITIAL STOP")) {
                rows.add(new StopHistoryRow(
                        event.occurredAt(),
                        "INITIAL_STOP",
                        null,
                        event.price(),
                        null,
                        event.quantity(),
                        displayEventType(event)));
                lastStop = event.price();
                lastQuantity = event.quantity();
                continue;
            }
            if (eventType.equals("STOP MOVED")
                    || eventType.equals("STOP QUANTITY UPDATED")
                    || eventType.equals("STOP UPDATE REJECTED")) {
                StopChange change = stopChange(event, lastStop);
                String reason = stopReason(event);
                rows.add(new StopHistoryRow(
                        event.occurredAt(),
                        reason,
                        change.oldStop(),
                        change.newStop(),
                        lastQuantity,
                        event.quantity(),
                        displayEventType(event)));
                lastStop = change.newStop();
                lastQuantity = event.quantity();
                continue;
            }
            if (eventType.equals("STOP CANCELLED")) {
                rows.add(new StopHistoryRow(
                        event.occurredAt(),
                        stopReason(event),
                        lastStop == null ? event.price() : lastStop,
                        null,
                        lastQuantity,
                        0L,
                        displayEventType(event)));
                lastStop = null;
                lastQuantity = 0L;
            }
        }
        return rows;
    }

    private StopChange stopChange(
            BacktestExportTradeEvent event,
            Double fallbackOldStop) {
        String details = event.notes();
        Double oldStop = null;
        Double newStop = null;
        if (details != null && details.contains("->")) {
            String pricePart = details.split("\\|", 2)[0];
            String[] pieces = pricePart.split("->", 2);
            if (pieces.length == 2) {
                oldStop = parseDouble(pieces[0]);
                newStop = parseDouble(pieces[1]);
            }
        }
        if (oldStop == null) {
            oldStop = fallbackOldStop;
        }
        if (newStop == null) {
            newStop = event.currentStopPrice() == null
                    ? event.price()
                    : event.currentStopPrice();
        }
        return new StopChange(oldStop, newStop);
    }

    private String stopReason(BacktestExportTradeEvent event) {
        if (event.notes() != null && event.notes().contains("|")) {
            String[] pieces = event.notes().split("\\|");
            return pieces[pieces.length - 1].trim();
        }
        if (event.eventType().equals("STOP CANCELLED")) {
            return "POSITION_CLOSED";
        }
        if (event.eventType().equals("STOP QUANTITY UPDATED")) {
            return "QUANTITY_UPDATE_AFTER_PARTIAL_FILL";
        }
        return event.eventType().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String displayEventType(BacktestExportTradeEvent event) {
        String eventType = event.eventType() == null
                ? ""
                : event.eventType().trim().toUpperCase(Locale.ROOT);
        if (eventType.equals("SIGNAL")) {
            return "SIGNAL";
        }
        if (eventType.equals("ENTRY")) {
            return "ENTRY";
        }
        if (eventType.equals("INITIAL STOP")) {
            return "INITIAL_STOP";
        }
        if (eventType.startsWith("EXIT - TP")) {
            String level = eventType.substring("EXIT - ".length());
            if (event.notes() != null
                    && event.notes().toUpperCase(Locale.ROOT).contains("PARTIAL")) {
                return level + "_PARTIAL_FILL";
            }
            return level + "_FILLED";
        }
        if (eventType.equals("EXIT - STOP_LOSS")) {
            return "STOP_FILLED";
        }
        if (eventType.equals("EXIT - SIGNAL")) {
            return "SIGNAL_EXIT";
        }
        if (eventType.equals("EXIT - MANUAL")) {
            return "MANUAL_EXIT";
        }
        if (eventType.equals("STOP MOVED")) {
            return switch (stopReason(event)) {
                case "TP1_TO_BREAK_EVEN" -> "STOP_MOVED_TO_ENTRY";
                case "TP2_TO_TP1" -> "STOP_MOVED_TO_TP1";
                case "TP3_TO_TP2" -> "STOP_MOVED_TO_TP2";
                default -> "STOP_MOVED";
            };
        }
        if (eventType.equals("STOP QUANTITY UPDATED")) {
            return "STOP_QUANTITY_UPDATED";
        }
        if (eventType.equals("STOP UPDATE REJECTED")) {
            return "STOP_UPDATE_REJECTED";
        }
        if (eventType.equals("STOP CANCELLED")) {
            return "TRADE_CLOSED";
        }
        return eventType.replace(' ', '_');
    }

    private record TakeProfitExecution(
            long filledQuantity,
            Instant executedAt,
            Double executionPrice,
            double realizedPnl) {
    }

    private record StopHistoryRow(
            Instant occurredAt,
            String reason,
            Double oldStop,
            Double newStop,
            Long oldQuantity,
            Long newQuantity,
            String triggerEvent) {
    }

    private record StopChange(Double oldStop, Double newStop) {
    }

    private static final class HistoricalState {

        private Long positionQuantity;
        private Double currentStop;
        private double realizedPnl;

        Long positionQuantity() {
            return positionQuantity;
        }

        Object positionQuantityValue() {
            return positionQuantity;
        }

        void positionQuantity(Long positionQuantity) {
            this.positionQuantity = positionQuantity;
            if (positionQuantity != null && positionQuantity == 0L) {
                currentStop = null;
            }
        }

        Double currentStop() {
            return currentStop;
        }

        void currentStop(Double currentStop) {
            this.currentStop = currentStop;
        }

        double realizedPnl() {
            return realizedPnl;
        }

        void addRealizedPnl(double realizedPnl) {
            this.realizedPnl += realizedPnl;
        }
    }
}
