package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.ExcelExportProperties;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class BacktestExcelExportService {

    private static final DateTimeFormatter FILE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneOffset.UTC);

    private final ExcelExportProperties properties;
    private final SummarySheetWriter summarySheetWriter;
    private final TradesSheetWriter tradesSheetWriter;
    private final TradeSheetWriter tradeSheetWriter;
    private final JoeRossSetupEventsSheetWriter setupEventsSheetWriter =
            new JoeRossSetupEventsSheetWriter();

    public BacktestExcelExportService(
            ExcelExportProperties properties,
            SummarySheetWriter summarySheetWriter,
            TradesSheetWriter tradesSheetWriter,
            TradeSheetWriter tradeSheetWriter) {
        this.properties = properties;
        this.summarySheetWriter = summarySheetWriter;
        this.tradesSheetWriter = tradesSheetWriter;
        this.tradeSheetWriter = tradeSheetWriter;
    }

    public byte[] export(BacktestExportRun run) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Excel export disabilitato");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ExcelStyleFactory styles = new ExcelStyleFactory(workbook, properties);
            summarySheetWriter.write(workbook, run, styles);
            tradesSheetWriter.write(workbook, run, styles);
            setupEventsSheetWriter.write(workbook, run, styles);
            for (BacktestExportTrade trade : run.trades()) {
                tradeSheetWriter.write(workbook, run, trade, styles);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Creazione export Excel fallita", exception);
        }
    }

    public String fileName(BacktestExportRun run) {
        String date = run.createdAt() == null
                ? FILE_DATE.format(java.time.Instant.now())
                : FILE_DATE.format(run.createdAt());
        return "Backtest_"
                + safeToken(run.symbol())
                + "_"
                + safeToken(run.strategy())
                + "_"
                + date
                + ".xlsx";
    }

    private String safeToken(String value) {
        if (value == null || value.isBlank()) {
            return "NA";
        }
        String token = value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return token.isBlank() ? "NA" : token;
    }
}
