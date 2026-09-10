package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.ExcelExportProperties;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ExcelStyleFactory {

    private final XSSFWorkbook workbook;
    private final ExcelExportProperties properties;
    private final Map<String, CellStyle> cache = new HashMap<>();

    public ExcelStyleFactory(
            XSSFWorkbook workbook,
            ExcelExportProperties properties) {
        this.workbook = workbook;
        this.properties = properties;
    }

    public CellStyle title() {
        return cache.computeIfAbsent("title", key -> {
            CellStyle style = base();
            style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setFontHeightInPoints((short) 14);
            style.setFont(font);
            return style;
        });
    }

    public CellStyle section() {
        return cache.computeIfAbsent("section", key -> {
            CellStyle style = base();
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        });
    }

    public CellStyle header() {
        return cache.computeIfAbsent("header", key -> {
            CellStyle style = base();
            style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.CENTER);
            return style;
        });
    }

    public CellStyle label() {
        return cache.computeIfAbsent("label", key -> {
            CellStyle style = base();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        });
    }

    public CellStyle text() {
        return cache.computeIfAbsent("text", key -> base());
    }

    public CellStyle notAvailable() {
        return cache.computeIfAbsent("na", key -> {
            CellStyle style = base();
            Font font = workbook.createFont();
            font.setItalic(true);
            font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setFont(font);
            return style;
        });
    }

    public CellStyle hyperlink() {
        return cache.computeIfAbsent("hyperlink", key -> {
            CellStyle style = base();
            Font font = workbook.createFont();
            font.setUnderline(Font.U_SINGLE);
            font.setColor(IndexedColors.BLUE.getIndex());
            style.setFont(font);
            return style;
        });
    }

    public CellStyle integer() {
        return number("integer", "0");
    }

    public CellStyle price() {
        return number("price", "0.0000");
    }

    public CellStyle money() {
        return number("money", "0.00");
    }

    public CellStyle percent() {
        return number("percent", "0.00%");
    }

    public CellStyle date() {
        return number("date", "yyyy-mm-dd");
    }

    public CellStyle dateTime() {
        return number("datetime", "yyyy-mm-dd hh:mm:ss");
    }

    public CellStyle phase(String phase) {
        if (!colorsEnabled()) {
            return text();
        }
        String normalized = normalize(phase);
        return cache.computeIfAbsent("phase:" + normalized, key -> {
            CellStyle style = base();
            short color = switch (normalized) {
                case "POST_TRADE" -> IndexedColors.GREY_25_PERCENT.getIndex();
                case "IN_TRADE" -> IndexedColors.WHITE.getIndex();
                default -> IndexedColors.LEMON_CHIFFON.getIndex();
            };
            style.setFillForegroundColor(color);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        });
    }

    public CellStyle event(String eventType) {
        if (!colorsEnabled()) {
            return text();
        }
        String normalized = normalize(eventType);
        return cache.computeIfAbsent("event:" + normalized, key -> {
            CellStyle style = base();
            short color;
            if (normalized.contains("STOP_MOVED")
                    || normalized.contains("STOP QUANTITY")
                    || normalized.contains("STOP MOVED")) {
                color = IndexedColors.LIGHT_YELLOW.getIndex();
            } else if (normalized.contains("ENTRY")) {
                color = IndexedColors.LIGHT_GREEN.getIndex();
            } else if (normalized.contains("TP")
                    || normalized.contains("TAKE_PROFIT")) {
                color = IndexedColors.LIGHT_TURQUOISE.getIndex();
            } else if (normalized.contains("STOP")
                    || normalized.contains("LOSS")) {
                color = IndexedColors.ROSE.getIndex();
            } else {
                color = IndexedColors.WHITE.getIndex();
            }
            style.setFillForegroundColor(color);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        });
    }

    private CellStyle number(String key, String format) {
        return cache.computeIfAbsent(key, ignored -> {
            CellStyle style = base();
            style.setDataFormat(workbook.createDataFormat().getFormat(format));
            return style;
        });
    }

    private CellStyle base() {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private boolean colorsEnabled() {
        return properties == null
                || properties.getColors() == null
                || properties.getColors().isEnabled();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
