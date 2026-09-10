package it.andreademartini.trading.web;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import it.andreademartini.trading.marketdata.stooq.StooqCsvImportReport;
import it.andreademartini.trading.marketdata.stooq.StooqCsvImportService;
import it.andreademartini.trading.marketdata.stooq.StooqDirectoryImportReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/historical")
public class HistoricalDataController {

    private final HistoricalDataService historicalDataService;
    private final StooqCsvImportService stooqCsvImportService;

    public HistoricalDataController(
            HistoricalDataService historicalDataService,
            StooqCsvImportService stooqCsvImportService) {
        this.historicalDataService = historicalDataService;
        this.stooqCsvImportService = stooqCsvImportService;
    }

    @PostMapping("/import/stooq")
    public StooqCsvImportReport importStooq(
            @RequestBody StooqImportRequest request) {
        BarInterval interval = parseInterval(request.interval());
        PriceAdjustment adjustment = request.priceAdjustment() == null
                ? PriceAdjustment.RAW
                : request.priceAdjustment();
        return stooqCsvImportService.importConfiguredFile(
                request.file(),
                request.symbol(),
                interval,
                adjustment);
    }

    @PostMapping(
            value = "/import/stooq/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StooqCsvImportReport importStooqFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "DAILY") String interval,
            @RequestParam(required = false) PriceAdjustment priceAdjustment) {
        BarInterval resolvedInterval = parseInterval(interval);
        PriceAdjustment adjustment = priceAdjustment == null
                ? PriceAdjustment.RAW
                : priceAdjustment;
        return stooqCsvImportService.importUploadedFile(
                file,
                symbol,
                resolvedInterval,
                adjustment);
    }

    @PostMapping("/import/stooq/directory")
    public StooqDirectoryImportReport importStooqDirectory(
            @RequestParam(defaultValue = "DAILY") String interval,
            @RequestParam(required = false) PriceAdjustment priceAdjustment) {
        BarInterval resolvedInterval = parseInterval(interval);
        PriceAdjustment adjustment = priceAdjustment == null
                ? PriceAdjustment.RAW
                : priceAdjustment;
        return stooqCsvImportService.importAllFiles(resolvedInterval, adjustment);
    }

    @GetMapping("/{symbol}")
    public List<HistoricalBarResponse> historical(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAILY") String interval) {
        return historicalDataService.load(
                        normalizeSymbol(symbol),
                        from,
                        to,
                        parseInterval(interval))
                .stream()
                .map(HistoricalBarResponse::from)
                .toList();
    }

    @GetMapping("/{symbol}/status")
    public HistoricalDataService.HistoricalDataStatus status(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "DAILY") String interval) {
        return historicalDataService.status(
                normalizeSymbol(symbol),
                parseInterval(interval));
    }

    private BarInterval parseInterval(String value) {
        return BarInterval.fromTimeframe(value);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol obbligatorio");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    public record StooqImportRequest(
            String symbol,
            String file,
            String interval,
            PriceAdjustment priceAdjustment) {
    }

    public record HistoricalBarResponse(
            String symbol,
            LocalDateTime dateTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            BarInterval interval,
            String provider,
            PriceAdjustment priceAdjustment) {

        static HistoricalBarResponse from(HistoricalBar bar) {
            return new HistoricalBarResponse(
                    bar.symbol(),
                    bar.dateTime(),
                    bar.getOpen(),
                    bar.getHigh(),
                    bar.getLow(),
                    bar.getClose(),
                    bar.volume(),
                    bar.interval(),
                    bar.provider(),
                    bar.priceAdjustment());
        }
    }
}
