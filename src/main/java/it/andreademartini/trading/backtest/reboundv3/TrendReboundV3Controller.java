package it.andreademartini.trading.backtest.reboundv3;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/backtest/trend-pullback-rebound-v3")
public class TrendReboundV3Controller {
    private final TrendReboundV3Store store;private final TrendReboundV3Service service;private final TrendReboundV3ExcelExporter excel;
    public TrendReboundV3Controller(TrendReboundV3Store store,TrendReboundV3Service service,TrendReboundV3ExcelExporter excel){this.store=store;this.service=service;this.excel=excel;}
    @GetMapping("/configuration")public TrendReboundV3Configuration configuration(){return store.current().asNoTickEntry();}
    @PutMapping("/configuration")public TrendReboundV3Configuration save(@RequestBody TrendReboundV3Configuration c){var normalized=c.asNoTickEntry();store.configuration(normalized);return normalized;}
    @PostMapping("/run")public TrendReboundV3Run run(@RequestBody TrendReboundV3Configuration c){return service.run(c);}
    @PostMapping("/minimum-ticks/prevalidate")public V3TickSupport.Report prevalidate(@RequestBody TrendReboundV3Configuration c){return service.prevalidate(c);}
    @PostMapping("/minimum-ticks/export/excel")public ResponseEntity<byte[]> tickExport(@RequestBody TrendReboundV3Configuration c){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("V3_MINIMUM_TICK_AUDIT.xlsx").build().toString()).body(excel.exportValidation(service.withLocalMetadata(c),service.prevalidate(c)));}
    @ExceptionHandler(V3TickSupport.ValidationException.class)public ResponseEntity<Map<String,Object>> tickFailure(V3TickSupport.ValidationException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage(),"tickPrevalidation",e.report()));}
    @GetMapping("/runs")public List<Map<String,Object>> history(){return store.history();}
    @GetMapping("/runs/{id}")public TrendReboundV3Run result(@PathVariable String id){return store.run(id);}
    @PostMapping("/runs/{id}/replay")public TrendReboundV3Run replay(@PathVariable String id){return service.replay(id);}
    @GetMapping("/runs/{id}/export/excel")public ResponseEntity<byte[]> export(@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(TrendReboundV3Configuration.ID+"_"+UUID.fromString(id)+".xlsx").build().toString()).body(excel.export(store.run(id)));}
    @ExceptionHandler(IllegalArgumentException.class)public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",Objects.toString(e.getMessage(),"Invalid configuration")));}
    @ExceptionHandler(NoSuchElementException.class)public ResponseEntity<Map<String,String>> missing(NoSuchElementException e){return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
}
