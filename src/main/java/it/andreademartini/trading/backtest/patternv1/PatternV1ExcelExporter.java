package it.andreademartini.trading.backtest.patternv1;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class PatternV1ExcelExporter {
    public byte[] export(PatternV1Result run) {
        if (run.schemaVersion() < 2) throw new IllegalArgumentException("Legacy run: accounting uncorrected; rerun PATTERN_V1 before exporting");
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet summary = sheet(book, "SUMMARY", "Metric", "Value");
            row(summary, "Strategy", run.strategy()); row(summary, "Start Date", run.startDate()); row(summary, "End Date", run.endDate());
            row(summary, "Effective Start Date", run.actualStartDate()); row(summary, "Effective End Date", run.actualEndDate());
            row(summary, "Initial Capital", run.initialCapital()); row(summary, "Final Cash", run.finalCash());
            row(summary, "Final Available Cash (excluding short collateral)", run.finalAvailableCash());
            row(summary, "Long Market Value", run.finalLongMarketValue()); row(summary, "Short Market Value (liability)", run.finalShortMarketValue());
            row(summary, "Open Positions Market Value (net signed)", run.finalOpenPositionValue()); row(summary, "Final Equity", run.finalCapital());
            row(summary, "Return %", run.returnPercent()); row(summary, "CAGR % (effective dates)", run.cagr());
            row(summary, "Max Drawdown", run.maxDrawdown()); row(summary, "Max Drawdown %", run.maxDrawdownPercent());
            writeMetrics(summary, "", run.summary());
            run.directionResults().forEach((d,m) -> writeMetrics(summary, d + " ", m));
            row(summary, "Average Gross Exposure % (EOD market value / equity)", run.averageGrossExposurePercent());
            row(summary, "Time In Market % (trading sessions)", run.timeInMarketPercent());
            row(summary, "PF definition", "Gross winning realized PnL / abs(gross losing realized PnL); CLOSED only. Net PF and PF R separate.");
            row(summary, "Win Rate definition", "Net Wins / (Net Wins + Net Losses). Breakevens excluded and counted.");
            row(summary, "Expectancy R definition", "Mean gross price Result R of valid CLOSED trades including TIMEOUT; OPEN excluded.");
            row(summary, "Weighted Expectancy R", "All closed-trade weights include breakevens; costs can make a nonzero price R net breakeven.");
            row(summary, "Excursion policy", "Executable MFE/MAE conservative at exit; raw daily-bar excursions separate.");
            row(summary, "Attribution drawdown", "Symbol/direction marked PnL contributions, not independently funded portfolios.");
            run.warnings().forEach(w -> row(summary, "Warning", w));
            String[] fields = Arrays.stream(PatternV1Result.Metrics.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new);
            List<String> headers = new ArrayList<>(List.of("Symbol","Direction","Pattern","Target R"));
            headers.addAll(Arrays.stream(fields).map(PatternV1ExcelExporter::label).toList());
            headers.addAll(List.of("Research Occurrences","Research PF R (STOP/TARGET)","Research Expectancy R (STOP/TARGET)",
                    "V1 PF R (STOP/TARGET)","V1 Expectancy R (STOP/TARGET)","Warnings"));
            Sheet symbols = sheet(book, "SYMBOL_RESULTS", headers.toArray(String[]::new));
            for (var s : run.symbolResults()) {
                List<Object> values = new ArrayList<>(List.of(s.symbol(),s.direction(),s.pattern(),s.targetR()));
                for (String f : fields) values.add(PatternV1Result.Metrics.class.getMethod(f).invoke(s.metrics()));
                var r = s.researchBaseline();
                values.add(r.occurrences()); values.add(r.profitFactor()); values.add(r.expectancyR());
                values.add(s.resolvedProfitFactorR()); values.add(s.resolvedExpectancyR()); values.add(String.join("; ",s.warnings()));
                row(symbols, values.toArray());
            }
            String[] tradeFields = {"tradeId","symbol","strategy","pattern","direction","signalDate","entryDate","entryPrice","stopPrice","riskPerShare",
                    "targetR","takeProfitPrice","desiredQuantity","finalQuantity","quantityLimitingReason","quantityLimitingReasons","sizeConstrained",
                    "equityAtEntry","availableCashBeforeEntry","availableCashAfterEntry","orderValue","desiredRiskAmount","desiredRiskPercent",
                    "actualRiskAmount","actualRiskPercent","exitDate","exitPrice","exitReason","timeoutExitPrice","grossPnl","realizedPnl","costs","entryCosts",
                    "unrealizedPnl","netUnrealizedPnl","resultR","executedMfeR","executedMaeR","rawBarMfeR","rawBarMaeR","holdingBars","sameBarAmbiguous",
                    "closed","remainingQuantity","markDate","markPrice","patternStartIndex","signalIndex","entryIndex","exitIndex"};
            Sheet trades = sheet(book, "TRADES", Arrays.stream(tradeFields)
                    .map(f -> f.equals("grossPnl") ? "Realized Gross PnL" : label(f)).toArray(String[]::new));
            for (var t : run.trades()) {
                Object[] values = new Object[tradeFields.length];
                for (int i = 0; i < values.length; i++) values[i] = PatternV1Result.Trade.class.getMethod(tradeFields[i]).invoke(t);
                row(trades, values);
            }
            Sheet equity = sheet(book, "EQUITY_CURVE", "Date","Cash","Long Market Value","Short Market Value (liability)","Gross Exposure",
                    "Net Exposure","Equity","Running Peak","Drawdown","Drawdown %","Open Positions","Gross Exposure %","Available Cash","In Market During Session");
            run.equityCurve().forEach(p -> row(equity,p.date(),p.cash(),p.longMarketValue(),p.shortMarketValue(),p.grossExposureValue(),p.netExposureValue(),
                    p.equity(),p.runningPeak(),p.drawdown(),p.drawdownPercent(),p.openPositions(),p.grossExposurePercent(),p.availableCash(),p.inMarket()));
            if (!run.equityCurve().isEmpty()) {
                chart((XSSFSheet)equity,"Portfolio Equity","Equity",6,1);
                chart((XSSFSheet)equity,"Drawdown %","Drawdown %",9,23);
            }
            Sheet config = sheet(book, "CONFIGURATION", "Symbol / Parameter","Direction / Value","Pattern","Target R","Enabled");
            new TreeMap<>(run.configuration()).forEach((symbol,value) -> {
                for (var d : it.andreademartini.trading.research.pattern.PatternDirection.values()) {
                    var c = value.direction(d); row(config,symbol,d,c == null ? null : c.pattern(),c == null ? null : c.targetR(),c != null && c.enabled());
                }
            });
            for (var c : PatternV1Result.RunSettings.class.getRecordComponents()) row(config,c.getName(),c.getAccessor().invoke(run.settings()));
            if (run.settings().commission() != null) {
                for (var c : PatternV1Commission.class.getRecordComponents())
                    row(config,"commission." + c.getName(),c.getAccessor().invoke(run.settings().commission()));
            }
            row(config,"Unmapped symbols","DISABLED; BA/BAC excluded"); row(config,"Frozen targets","No optimization during backtest");
            row(config,"Schema version",run.schemaVersion());
            for (Sheet s : book) {
                s.createFreezePane(0,1); int columns = s.getRow(0).getLastCellNum();
                if (s.getLastRowNum() > 0) s.setAutoFilter(new CellRangeAddress(0,s.getLastRowNum(),0,columns-1));
                for (int c = 0; c < columns; c++) s.setColumnWidth(c,c == 0 ? 36*256 : 25*256);
            }
            book.write(output); return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("PATTERN_V1 Excel export failed",e); }
    }
    private static void writeMetrics(Sheet sheet,String prefix,PatternV1Result.Metrics metrics) {
        for (var c : PatternV1Result.Metrics.class.getRecordComponents()) {
            try { row(sheet,prefix+label(c.getName()),c.getAccessor().invoke(metrics)); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }
    private static String label(String name) {
        return switch (name) {
            case "grossPnl" -> "Total Gross Mark-to-Market PnL"; case "netPnl" -> "Total Net PnL";
            case "profitFactor" -> "Profit Factor PnL (gross closed)"; case "profitFactorNet" -> "Profit Factor Net PnL (closed)";
            case "averageWinner" -> "Average Winner PnL (net)"; case "averageLoser" -> "Average Loser PnL (net)";
            case "mfeR" -> "Executed MFE R"; case "maeR" -> "Executed MAE R";
            case "trades" -> "Signals Executed / Trades"; case "ambiguities" -> "Same Bar Ambiguities";
            case "skippedPositionOpen" -> "Signals Skipped Open Position"; case "skippedCapital" -> "Signals Skipped Capital";
            case "desiredRiskPercent" -> "Target Risk %"; case "actualRiskPercent" -> "Actual Risk %";
            case "averageActualRiskPercent" -> "Average Actual Risk %"; case "sizeConstrained" -> "SIZE_CONSTRAINED";
            case "realizedPnl" -> "Realized PnL (net)"; case "unrealizedPnl" -> "Unrealized PnL (gross)";
            default -> {
                String words = name.replaceAll("([A-Z])"," $1").replace("Pnl","PnL").replace("Mfe","MFE").replace("Mae","MAE");
                yield Character.toUpperCase(words.charAt(0)) + words.substring(1);
            }
        };
    }
    private static Sheet sheet(Workbook book,String name,String... headers) {
        Sheet sheet = book.createSheet(name); row(sheet,(Object[])headers);
        CellStyle style = book.createCellStyle(); Font font = book.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font); style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for (Cell c : sheet.getRow(0)) c.setCellStyle(style); return sheet;
    }
    private static void chart(XSSFSheet sheet,String title,String seriesName,int valueColumn,int firstRow) {
        var drawing = sheet.createDrawingPatriarch(); var anchor = drawing.createAnchor(0,0,0,0,16,firstRow,31,firstRow+20);
        var chart = drawing.createChart(anchor); chart.setTitleText(title); chart.setTitleOverlay(false);
        var datesAxis = chart.createCategoryAxis(AxisPosition.BOTTOM); var valueAxis = chart.createValueAxis(AxisPosition.LEFT);
        valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        var dates = XDDFDataSourcesFactory.fromStringCellRange(sheet,new CellRangeAddress(1,sheet.getLastRowNum(),0,0));
        var values = XDDFDataSourcesFactory.fromNumericCellRange(sheet,new CellRangeAddress(1,sheet.getLastRowNum(),valueColumn,valueColumn));
        var data = chart.createData(ChartTypes.LINE,datesAxis,valueAxis); data.addSeries(dates,values).setTitle(seriesName,null); chart.plot(data);
    }
    private static void row(Sheet sheet,Object... values) {
        Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i); Object value = values[i];
            if (value instanceof Number n) cell.setCellValue(n.doubleValue());
            else if (value instanceof Boolean b) cell.setCellValue(b);
            else if (value != null) cell.setCellValue(value.toString());
        }
    }
}
