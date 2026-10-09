package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.strategy.joeross.JoeRossEvent;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Exports every setup event, including setups that never become trades. */
public final class JoeRossSetupEventsSheetWriter {

    private static final String[] HEADERS = {
            "Direction", "Event", "Bar Index", "Timestamp", "Price", "Details"
    };

    public void write(
            XSSFWorkbook workbook,
            BacktestExportRun run,
            ExcelStyleFactory styles) {
        if (run.strategyEvents().isEmpty()) {
            return;
        }
        Sheet sheet = workbook.createSheet("SETUP EVENTS");
        Row header = sheet.createRow(0);
        ExcelSheetSupport.header(header, styles.header(), HEADERS);
        int rowIndex = 1;
        for (JoeRossEvent event : run.strategyEvents()) {
            Row row = sheet.createRow(rowIndex++);
            ExcelSheetSupport.cell(row, 0, event.direction().name(), styles.text());
            ExcelSheetSupport.cell(row, 1, event.type().name(), styles.text());
            ExcelSheetSupport.cell(row, 2, event.barIndex(), styles.integer());
            ExcelSheetSupport.cell(row, 3, event.timestamp(), styles.dateTime());
            ExcelSheetSupport.cell(row, 4, event.price(), styles.price());
            ExcelSheetSupport.cell(row, 5, event.details(), styles.text());
        }
        sheet.createFreezePane(0, 1);
        if (rowIndex > 1) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, rowIndex - 1, 0, HEADERS.length - 1));
        }
        ExcelSheetSupport.autoSize(sheet, HEADERS.length);
    }
}
