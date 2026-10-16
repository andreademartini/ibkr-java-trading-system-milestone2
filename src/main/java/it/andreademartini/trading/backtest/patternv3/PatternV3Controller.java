package it.andreademartini.trading.backtest.patternv3;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/backtest/pattern-v3")
public class PatternV3Controller {
    private final PatternV3Service service;private final PatternV3Store store;private final PatternV3ExcelExporter excel;
    public PatternV3Controller(PatternV3Service service,PatternV3Store store,PatternV3ExcelExporter excel) {this.service=service;this.store=store;this.excel=excel;}
    @GetMapping("/research-filter-capabilities") public Map<String,Object> researchFilterCapabilities(){return Map.of("filterModelVersion",SignalFilterAudit.VERSION,"closeStrengthModes",List.of("LEGACY_MAX_LE","MIN_GE"),"volumeBases",List.of("UNVERIFIED","CONSISTENT_DATASET_UNITS"));}
    @GetMapping("/configuration") public PatternV3Configuration configuration() {return service.current();}
    @PutMapping("/configuration") public PatternV3Configuration update(@RequestBody PatternV3Configuration config) {var normalized=config.forNewRun();service.current(normalized);return normalized;}
    @GetMapping("/patterns") public Map<String,List<String>> patterns() {return service.patterns();}
    @PostMapping("/baseline") public PatternV3Configuration baseline() {return service.loadBaseline();}
    @PostMapping("/reset") public PatternV3Configuration reset() {return service.reset();}
    @GetMapping("/configurations") public List<PatternV3Store.Saved> configurations() {return store.configurations();}
    @PostMapping("/configurations") public PatternV3Store.Saved save(@RequestBody PatternV3Configuration config) {return service.save(config);}
    @GetMapping("/configurations/{id}") public PatternV3Store.Saved load(@PathVariable String id) {return store.configuration(id);}
    @PostMapping("/run") public PatternV3Run run(@RequestBody PatternV3Configuration config) {return service.run(config);}
    @GetMapping("/runs") public List<Map<String,Object>> history() {
        return store.runs().stream().map(r->{Map<String,Object> m=new LinkedHashMap<>();var p=r.portfolio();
            m.put("closeStrengthMin",r.configuration().closeStrengthMin());m.put("patternRangeMinPct",r.configuration().rangeMinimum());m.put("patternRangeMaxPct",r.configuration().patternRangeMaxPct());m.put("relativeVolumeFilterEnabled",r.configuration().rvolEnabled());m.put("relativeVolumePeriod",r.configuration().rvolPeriod());m.put("relativeVolumeMin",r.configuration().rvolMinimum());m.put("relativeVolumeMax",r.configuration().relativeVolumeMax());m.put("relativeVolumeBasis",r.configuration().rvolBasis());m.put("filterModelVersion",r.filterModelVersion());m.put("signalFilterAuditAvailable",r.signalFilterAudit()!=null);
            m.put("signalTimeframe",r.signalTimeframe());m.put("executionTimeframe","DAILY");m.put("managementTimeframe","DAILY");m.put("signalCalendarVersion",r.signalCalendarVersion());m.put("executionModelVersion",r.executionModelVersion());m.put("executionAuditAvailable",r.executionAudit()!=null);
            m.put("strategyId",r.strategyId());m.put("strategyVersion",r.strategyVersion());m.put("configurationHash",r.configurationHash());m.put("runId",r.runId());m.put("timestamp",r.timestamp());m.put("configurationName",r.configuration().name());
            m.put("marketRegimeFilterEnabled",r.configuration().marketRegimeFilterEnabled());m.put("marketRegimeFilterAdxPeriod",r.configuration().marketRegimeFilterAdxPeriod());m.put("marketRegimeFilterAdxThreshold",r.configuration().marketRegimeFilterAdxThreshold());m.put("marketRegimeFilterMode",r.configuration().marketRegimeFilterMode());
            m.put("marketRegimeAnalysisEnabled",r.configuration().marketRegimeAnalysisEnabled());m.put("patternRangeFilterEnabled",r.configuration().patternRangeFilterEnabled());m.put("minimumPatternRangePct",r.configuration().rangeMinimum());m.put("pairedRunId",r.pairedRunId());
            m.put("closeStrengthFilterEnabled",r.configuration().closeStrengthFilterEnabled());m.put("maxCloseStrength",r.configuration().maxCloseStrength());m.put("datasetId",r.datasetId());
            m.put("bollingerAtrFilterEnabled",r.configuration().bollingerAtrFilterEnabled());m.put("bollingerPeriod",r.configuration().bollingerPeriod());m.put("bollingerStdDev",r.configuration().bollingerStdDev());m.put("atrPeriod",r.configuration().atrPeriod());m.put("maxBollingerDistanceAtr",r.configuration().maxBollingerDistanceAtr());
            var g=r.configuration().global();
            m.put("maxPositionValueMode",g.maxPositionValueMode());m.put("maxPositionValuePercent",g.maxPositionValuePercent());
            m.put("maxLongNotionalMode",g.maxLongNotionalMode());m.put("maxLongNotionalPercent",g.maxLongNotionalPercent());
            m.put("maxShortNotionalMode",g.maxShortNotionalMode());m.put("maxShortNotionalPercent",g.maxShortNotionalPercent());
            m.put("capitalSkipShadowAnalysisEnabled",r.configuration().capitalSkipShadowAnalysisEnabled());m.put("initialCapital",p.initialCapital());m.put("startDate",p.startDate());m.put("endDate",p.endDate());
            m.put("activeSymbols",r.configuration().symbols().stream().filter(PatternV3Configuration.Symbol::enabled).map(PatternV3Configuration.Symbol::symbol).toList());
            m.put("cagr",p.cagr());m.put("maxDrawdownPercent",p.maxDrawdownPercent());m.put("maxDrawdown",p.maxDrawdown());m.put("pfNet",p.summary().profitFactorNet());m.put("expectancyR",p.summary().expectancyR());m.put("netPnl",p.summary().netPnl());m.put("trades",p.summary().trades());return m;}).toList();
    }
    @GetMapping("/runs/{id}") public PatternV3Run result(@PathVariable String id) {return service.result(id);}
    @PostMapping("/runs/{id}/replay") public PatternV3Run replay(@PathVariable String id,@RequestBody(required=false) PatternV3Configuration configuration) {return service.replay(id,configuration);}
    @PostMapping("/runs/{id}/capital-skips-analysis") public CapitalSkipShadowAnalysis.Analysis capitalSkips(@PathVariable String id){return service.capitalSkipsAnalysis(id);}
    public record CompareRequest(List<String> runIds) {}
    @PostMapping("/runs/compare") public PatternV3RunComparison.Comparison compare(@RequestBody CompareRequest request) {return service.compare(request.runIds());}
    @PostMapping("/runs/{id}/capital-limits-replay") public PatternV3Run capitalLimitsReplay(@PathVariable String id,@RequestBody PatternV3Configuration configuration) {return service.replayCapitalLimits(id,configuration);}
    @PostMapping("/runs/compare-capital-limits") public PatternV3CapitalComparison.Comparison compareCapitalLimits(@RequestBody CompareRequest request) {return service.compareCapitalLimits(request.runIds());}
    @PostMapping("/configurations/preset") public PatternV3Store.Saved preset(@RequestBody PatternV3Configuration configuration) {return service.savePreset(configuration);}
    @PostMapping("/runs/{id}/benchmark") public PatternV3Run benchmark(@PathVariable String id) {return service.benchmark(id);}
    @GetMapping("/runs/{id}/analysis") public PatternV3Analysis.Analysis analysis(@PathVariable String id,@RequestParam(required=false) Double minTarget,@RequestParam(required=false) Double exactTarget) {
        if(minTarget!=null&&(!Double.isFinite(minTarget)||minTarget<=0)||exactTarget!=null&&(!Double.isFinite(exactTarget)||exactTarget<=0))throw new IllegalArgumentException("Target filter deve essere positivo");
        return PatternV3Analysis.calculate(store.run(id).portfolio(),minTarget,exactTarget);
    }
    @GetMapping("/runs/{id}/export/excel") public ResponseEntity<byte[]> excel(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("PATTERN_V3_"+UUID.fromString(id)+".xlsx").build().toString()).body(excel.export(service.result(id)));
    }
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<Map<String,String>> missing(NoSuchElementException e) {return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
}
