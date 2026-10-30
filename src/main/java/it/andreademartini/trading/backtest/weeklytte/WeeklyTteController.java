package it.andreademartini.trading.backtest.weeklytte;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/backtest/weekly-high-tte")
public class WeeklyTteController {
    private final WeeklyTteService service;private final WeeklyTteStore store;private final WeeklyTteExcelExporter excel;
    public WeeklyTteController(WeeklyTteService service,WeeklyTteStore store,WeeklyTteExcelExporter excel){this.service=service;this.store=store;this.excel=excel;}
    @GetMapping("/configuration") public WeeklyTteConfiguration current(){return store.current();}
    @PutMapping("/configuration") public WeeklyTteConfiguration save(@RequestBody WeeklyTteConfiguration c){store.configuration(c);return c;}
    @GetMapping("/configurations") public List<WeeklyTteConfiguration> configurations(){return store.configurations();}
    @PostMapping("/run") public WeeklyTteRun run(@RequestBody WeeklyTteConfiguration c){return service.run(c);}
    @GetMapping("/runs") public Map<String,Object> history(){return Map.of("configurationsExperimented",store.runs().stream().map(WeeklyTteRun::configurationHash).distinct().count(),"runs",store.runs().stream().map(r->Map.of("runId",r.runId(),"timestamp",r.timestamp(),"mode",r.configuration().mode(),"name",r.configuration().name(),"configurationHash",r.configurationHash(),"datasetId",r.datasetId(),"metrics",r.analysis().get("performance"))).toList());}
    @GetMapping("/runs/{id}") public WeeklyTteRun result(@PathVariable String id){return store.run(id);}
    @PostMapping("/runs/{id}/replay") public WeeklyTteRun replay(@PathVariable String id){return service.replay(id);}
    @PostMapping("/runs/{id}/modes") public List<WeeklyTteRun> modes(@PathVariable String id){return service.modes(id);}
    @PostMapping("/runs/{id}/sensitivity") public WeeklyTteRun sensitivity(@PathVariable String id){return service.sensitivity(id);}
    public record Compare(List<String> runIds){}
    @PostMapping("/runs/compare") public Map<String,Object> compare(@RequestBody Compare r){return service.compare(r.runIds());}
    @PostMapping("/runs/compare/excel") public ResponseEntity<byte[]> compareExcel(@RequestBody Compare r){return download(excel.comparison(service.compare(r.runIds())),"WEEKLY_TTE_COMPARISON.xlsx");}
    @GetMapping("/runs/{id}/export/excel") public ResponseEntity<byte[]> excel(@PathVariable String id){return download(excel.export(store.run(id)),"WEEKLY_HIGH_TTE_"+UUID.fromString(id)+".xlsx");}
    private static ResponseEntity<byte[]> download(byte[] bytes,String name){return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString()).body(bytes);}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<Map<String,String>> missing(NoSuchElementException e){return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
}
