package it.andreademartini.trading.web;

import it.andreademartini.trading.backtest.export.BacktestExcelExportService;
import it.andreademartini.trading.backtest.export.BacktestExportRun;
import it.andreademartini.trading.backtest.export.BacktestRunRegistry;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class BacktestExportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final BacktestRunRegistry backtestRunRegistry;
    private final BacktestExcelExportService excelExportService;

    public BacktestExportController(
            BacktestRunRegistry backtestRunRegistry,
            BacktestExcelExportService excelExportService) {
        this.backtestRunRegistry = backtestRunRegistry;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/api/backtest/{backtestId}/export/excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String backtestId) {
        BacktestExportRun run = backtestRunRegistry.find(backtestId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Backtest non trovato: " + backtestId));
        try {
            byte[] workbook = excelExportService.export(run);
            return ResponseEntity.ok()
                    .contentType(XLSX_MEDIA_TYPE)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(excelExportService.fileName(run))
                                    .build()
                                    .toString())
                    .body(workbook);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
