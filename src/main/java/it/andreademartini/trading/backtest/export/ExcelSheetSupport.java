package it.andreademartini.trading.backtest.export;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

final class ExcelSheetSupport {

    private ExcelSheetSupport() {
    }

    static Cell cell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        setValue(cell, value);
        if (style != null) {
            cell.setCellStyle(style);
        }
        return cell;
    }

    static void setValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof String stringValue) {
            cell.setCellValue(stringValue);
            return;
        }
        if (value instanceof Integer integerValue) {
            cell.setCellValue(integerValue);
            return;
        }
        if (value instanceof Long longValue) {
            cell.setCellValue(longValue);
            return;
        }
        if (value instanceof Double doubleValue) {
            cell.setCellValue(doubleValue);
            return;
        }
        if (value instanceof Float floatValue) {
            cell.setCellValue(floatValue.doubleValue());
            return;
        }
        if (value instanceof BigDecimal decimalValue) {
            cell.setCellValue(decimalValue.doubleValue());
            return;
        }
        if (value instanceof Instant instantValue) {
            cell.setCellValue(Date.from(instantValue));
            return;
        }
        if (value instanceof LocalDate dateValue) {
            cell.setCellValue(Date.from(dateValue.atStartOfDay()
                    .toInstant(ZoneOffset.UTC)));
            return;
        }
        if (value instanceof Enum<?> enumValue) {
            cell.setCellValue(enumValue.name());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    static void sectionTitle(
            Sheet sheet,
            int rowIndex,
            String title,
            int lastColumn,
            CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, title, style);
        if (lastColumn > 0) {
            sheet.addMergedRegion(new CellRangeAddress(
                    rowIndex,
                    rowIndex,
                    0,
                    lastColumn));
        }
    }

    static void header(Row row, CellStyle style, String... headers) {
        for (int column = 0; column < headers.length; column++) {
            cell(row, column, headers[column], style);
        }
    }

    static void autoSize(Sheet sheet, int columns) {
        for (int column = 0; column < columns; column++) {
            sheet.autoSizeColumn(column);
            int currentWidth = sheet.getColumnWidth(column);
            int boundedWidth = Math.max(2_800, Math.min(currentWidth + 600, 12_000));
            sheet.setColumnWidth(column, boundedWidth);
        }
    }

    static void documentHyperlink(
            Workbook workbook,
            Cell cell,
            String text,
            String sheetName,
            String cellReference,
            CellStyle style) {
        CreationHelper helper = workbook.getCreationHelper();
        Hyperlink hyperlink = helper.createHyperlink(HyperlinkType.DOCUMENT);
        hyperlink.setAddress("'" + sheetName.replace("'", "''") + "'!" + cellReference);
        cell.setCellValue(text);
        cell.setHyperlink(hyperlink);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
}
