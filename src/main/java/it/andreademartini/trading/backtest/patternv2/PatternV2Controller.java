package it.andreademartini.trading.backtest.patternv2;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/backtest/pattern-v2")
public class PatternV2Controller {
    private final PatternV2Service service;private final PatternV2Store store;private final PatternV2ExcelExporter excel;
    public PatternV2Controller(PatternV2Service service,PatternV2Store store,PatternV2ExcelExporter excel) {this.service=service;this.store=store;this.excel=excel;}
    @GetMapping("/configuration") public PatternV2Configuration configuration() {return service.current();}
    @PutMapping("/configuration") public PatternV2Configuration update(@RequestBody PatternV2Configuration config) {var normalized=config.forNewRun();service.current(normalized);return normalized;}
    @GetMapping("/patterns") public Map<String,List<String>> patterns() {return service.patterns();}
    @PostMapping("/baseline") public PatternV2Configuration baseline() {return service.loadBaseline();}
    @PostMapping("/reset") public PatternV2Configuration reset() {return service.reset();}
    @GetMapping("/configurations") public List<PatternV2Store.Saved> configurations() {return store.configurations();}
    @PostMapping("/configurations") public PatternV2Store.Saved save(@RequestBody PatternV2Configuration config) {return service.save(config);}
    @GetMapping("/configurations/{id}") public PatternV2Store.Saved load(@PathVariable String id) {return store.configuration(id);}
    @PostMapping("/run") public PatternV2Run run(@RequestBody PatternV2Configuration config) {return service.run(config);}
    @GetMapping("/runs") public List<Map<String,Object>> history() {
        return store.runs().stream().map(r->{Map<String,Object> m=new LinkedHashMap<>();var p=r.portfolio();
            m.put("runId",r.runId());m.put("timestamp",r.timestamp());m.put("configurationName",r.configuration().name());
            m.put("patternRangeFilterEnabled",r.configuration().patternRangeFilterEnabled());m.put("minimumPatternRangePct",r.configuration().minimumPatternRangePct());m.put("pairedRunId",r.pairedRunId());
            m.put("closeStrengthFilterEnabled",r.configuration().closeStrengthFilterEnabled());m.put("maxCloseStrength",r.configuration().maxCloseStrength());m.put("datasetId",r.datasetId());
            m.put("bollingerAtrFilterEnabled",r.configuration().bollingerAtrFilterEnabled());m.put("bollingerPeriod",r.configuration().bollingerPeriod());m.put("bollingerStdDev",r.configuration().bollingerStdDev());m.put("atrPeriod",r.configuration().atrPeriod());m.put("maxBollingerDistanceAtr",r.configuration().maxBollingerDistanceAtr());
            var g=r.configuration().global();
            m.put("maxPositionValueMode",g.maxPositionValueMode());m.put("maxPositionValuePercent",g.maxPositionValuePercent());
            m.put("maxLongNotionalMode",g.maxLongNotionalMode());m.put("maxLongNotionalPercent",g.maxLongNotionalPercent());
            m.put("maxShortNotionalMode",g.maxShortNotionalMode());m.put("maxShortNotionalPercent",g.maxShortNotionalPercent());
            m.put("capitalSkipShadowAnalysisEnabled",r.configuration().capitalSkipShadowAnalysisEnabled());m.put("initialCapital",p.initialCapital());m.put("startDate",p.startDate());m.put("endDate",p.endDate());
            m.put("activeSymbols",r.configuration().symbols().stream().filter(PatternV2Configuration.Symbol::enabled).map(PatternV2Configuration.Symbol::symbol).toList());
            m.put("cagr",p.cagr());m.put("maxDrawdownPercent",p.maxDrawdownPercent());m.put("maxDrawdown",p.maxDrawdown());m.put("pfNet",p.summary().profitFactorNet());m.put("expectancyR",p.summary().expectancyR());m.put("netPnl",p.summary().netPnl());m.put("trades",p.summary().trades());return m;}).toList();
    }
    @GetMapping("/runs/{id}") public PatternV2Run result(@PathVariable String id) {return service.result(id);}
    @PostMapping("/runs/{id}/replay") public PatternV2Run replay(@PathVariable String id,@RequestBody(required=false) PatternV2Configuration configuration) {return service.replay(id,configuration);}
    @PostMapping("/runs/{id}/capital-skips-analysis") public CapitalSkipShadowAnalysis.Analysis capitalSkips(@PathVariable String id){return service.capitalSkipsAnalysis(id);}
    public record CompareRequest(List<String> runIds) {}
    @PostMapping("/runs/compare") public PatternV2RunComparison.Comparison compare(@RequestBody CompareRequest request) {return service.compare(request.runIds());}
    @PostMapping("/runs/{id}/capital-limits-replay") public PatternV2Run capitalLimitsReplay(@PathVariable String id,@RequestBody PatternV2Configuration configuration) {return service.replayCapitalLimits(id,configuration);}
    @PostMapping("/runs/compare-capital-limits") public PatternV2CapitalComparison.Comparison compareCapitalLimits(@RequestBody CompareRequest request) {return service.compareCapitalLimits(request.runIds());}
    @PostMapping("/configurations/preset") public PatternV2Store.Saved preset(@RequestBody PatternV2Configuration configuration) {return service.savePreset(configuration);}
    @PostMapping("/runs/{id}/benchmark") public PatternV2Run benchmark(@PathVariable String id) {return service.benchmark(id);}
    @GetMapping("/runs/{id}/analysis") public PatternV2Analysis.Analysis analysis(@PathVariable String id,@RequestParam(required=false) Double minTarget,@RequestParam(required=false) Double exactTarget) {
        if(minTarget!=null&&(!Double.isFinite(minTarget)||minTarget<=0)||exactTarget!=null&&(!Double.isFinite(exactTarget)||exactTarget<=0))throw new IllegalArgumentException("Target filter deve essere positivo");
        return PatternV2Analysis.calculate(store.run(id).portfolio(),minTarget,exactTarget);
    }
    @GetMapping("/runs/{id}/export/excel") public ResponseEntity<byte[]> excel(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("PATTERN_V2_"+UUID.fromString(id)+".xlsx").build().toString()).body(excel.export(service.result(id)));
    }
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<Map<String,String>> missing(NoSuchElementException e) {return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
}
