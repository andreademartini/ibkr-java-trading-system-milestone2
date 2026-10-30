package it.andreademartini.trading.backtest.reboundv2;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/backtest/trend-pullback-rebound-v2")
public class TrendReboundV2Controller {
    private final TrendReboundV2Store store;private final TrendReboundV2Service service;private final TrendReboundV2ExcelExporter excel;
    public TrendReboundV2Controller(TrendReboundV2Store store,TrendReboundV2Service service,TrendReboundV2ExcelExporter excel){this.store=store;this.service=service;this.excel=excel;}
    @GetMapping("/configuration")public TrendReboundV2Configuration configuration(){return store.current();}
    @PutMapping("/configuration")public TrendReboundV2Configuration save(@RequestBody TrendReboundV2Configuration c){store.configuration(c);return c;}
    @PostMapping("/run")public TrendReboundV2Run run(@RequestBody TrendReboundV2Configuration c,@RequestParam(required=false) String referenceV1RunId){return referenceV1RunId==null||referenceV1RunId.isBlank()?service.run(c):service.fromV1(c,referenceV1RunId);}
    @GetMapping("/reference/{id}/configuration")public TrendReboundV2Configuration referenceConfiguration(@PathVariable String id,@RequestParam(defaultValue="ATR_TARGET_ONLY") TrendReboundV2Configuration.ExitMode exitMode){return service.configurationFromV1(id,exitMode);}
    @GetMapping("/runs")public List<Map<String,Object>> history(){return store.history();}
    @GetMapping("/runs/{id}")public TrendReboundV2Run result(@PathVariable String id){return store.run(id);}
    @PostMapping("/runs/{id}/replay")public TrendReboundV2Run replay(@PathVariable String id){return service.replay(id);}
    public record Compare(List<String> runIds){}
    @PostMapping("/runs/compare")public Map<String,Object> compare(@RequestBody Compare request){return service.compare(request.runIds());}
    @PostMapping("/runs/compare/excel")public ResponseEntity<byte[]> comparison(@RequestBody Compare request){var runs=service.comparable(request.runIds());return file(excel.export(runs.getFirst(),runs),"TREND_PULLBACK_REBOUND_V2_COMPARISON.xlsx");}
    @GetMapping("/runs/{id}/export/excel")public ResponseEntity<byte[]> export(@PathVariable String id){return file(excel.export(store.run(id)),"TREND_PULLBACK_REBOUND_V2_"+UUID.fromString(id)+".xlsx");}
    private static ResponseEntity<byte[]> file(byte[] bytes,String name){return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString()).body(bytes);}
    @ExceptionHandler(IllegalArgumentException.class)public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",Objects.toString(e.getMessage(),"Invalid request")));}
    @ExceptionHandler(NoSuchElementException.class)public ResponseEntity<Map<String,String>> missing(NoSuchElementException e){return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class)public ResponseEntity<Map<String,String>> conflict(IllegalStateException e){return ResponseEntity.status(409).body(Map.of("message",e.getMessage()));}
}
