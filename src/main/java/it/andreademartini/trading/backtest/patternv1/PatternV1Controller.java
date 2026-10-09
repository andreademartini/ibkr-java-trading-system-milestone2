package it.andreademartini.trading.backtest.patternv1;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/backtest/pattern-v1/legacy")
public class PatternV1Controller {
    private final PatternV1BacktestService service;
    private final PatternV1RunStore store;
    private final PatternV1ExcelExporter exporter;
    public PatternV1Controller(PatternV1BacktestService service, PatternV1RunStore store, PatternV1ExcelExporter exporter) {
        this.service = service; this.store = store; this.exporter = exporter;
    }
    @GetMapping("/configuration")
    public it.andreademartini.trading.strategy.patternv1.PatternV1Properties configuration() { return service.configuration(); }
    @PostMapping("/run")
    public PatternV1Result run(@RequestBody(required = false) PatternV1Request request) { return service.run(request); }
    @GetMapping("/{id}")
    public PatternV1Result result(@PathVariable String id) {
        try { return store.find(id); }
        catch (NoSuchElementException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()); }
    }
    @GetMapping("/{id}/export/excel")
    public ResponseEntity<byte[]> excel(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("PATTERN_V1_" + id + ".xlsx").build().toString())
                .body(exporter.export(result(id)));
    }
}
