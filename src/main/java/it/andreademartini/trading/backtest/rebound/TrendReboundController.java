package it.andreademartini.trading.backtest.rebound;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/backtest/trend-pullback-rebound")
public class TrendReboundController {
    private final TrendReboundStore store;private final TrendReboundService service;private final TrendReboundExcelExporter excel;
    public TrendReboundController(TrendReboundStore store,TrendReboundService service,TrendReboundExcelExporter excel){this.store=store;this.service=service;this.excel=excel;}
    @GetMapping("/configuration")public TrendReboundConfiguration configuration(){return store.current();}
    @PutMapping("/configuration")public TrendReboundConfiguration save(@RequestBody TrendReboundConfiguration c){store.configuration(c);return c;}
    @PostMapping("/run")public TrendReboundRun run(@RequestBody TrendReboundConfiguration c){return service.run(c);}
    @GetMapping("/runs")public List<Map<String,Object>> history(){return store.history();}
    @GetMapping("/runs/{id}")public TrendReboundRun result(@PathVariable String id){return store.run(id);}
    @PostMapping("/runs/{id}/replay")public TrendReboundRun replay(@PathVariable String id){return service.replay(id);}
    @GetMapping("/runs/{id}/export/excel")public ResponseEntity<byte[]> export(@PathVariable String id){return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(TrendReboundConfiguration.ID+"_"+UUID.fromString(id)+".xlsx").build().toString()).body(excel.export(store.run(id)));}
    @ExceptionHandler(IllegalArgumentException.class)public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",Objects.toString(e.getMessage(),"Invalid configuration")));}
    @ExceptionHandler(NoSuchElementException.class)public ResponseEntity<Map<String,String>> missing(NoSuchElementException e){return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
}
