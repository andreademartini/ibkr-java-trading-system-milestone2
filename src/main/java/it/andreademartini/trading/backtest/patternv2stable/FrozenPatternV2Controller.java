package it.andreademartini.trading.backtest.patternv2stable;

import it.andreademartini.trading.backtest.patternv2.PatternV2ExcelExporter;
import it.andreademartini.trading.backtest.patternv2.PatternV2Run;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/backtest/pattern-v2-stable")
public class FrozenPatternV2Controller {
    private final FrozenPatternV2Service service;private final PatternV2ExcelExporter excel;
    public FrozenPatternV2Controller(FrozenPatternV2Service service,PatternV2ExcelExporter excel){this.service=service;this.excel=excel;}
    @GetMapping("/configuration") public Object configuration(){return service.version();}
    @PostMapping("/run") public Object run(@RequestBody(required=false) Map<String,Object> request){try{var risk=it.andreademartini.trading.backtest.patternstable.BacktestRiskParameter.parse(request);return present(risk==null?service.run():service.runWithRisk(risk));}catch(IllegalArgumentException invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,invalid.getMessage());}}
    @GetMapping("/runs") public Object history(){return service.history().stream().map(r->Map.of("runId",r.runId(),"strategyId",r.strategyId(),"strategyVersion",r.strategyVersion(),"configurationHash",r.configurationHash(),"timestamp",r.timestamp(),"riskPercent",r.simulation().configuration().global().riskPercent())).toList();}
    @GetMapping("/runs/{id}") public Object result(@PathVariable String id){return present(service.result(id));}
    private Object present(it.andreademartini.trading.backtest.patternstable.PatternStableService.Run run){
        var json=(com.fasterxml.jackson.databind.node.ObjectNode)it.andreademartini.trading.backtest.patternstable.PatternStableStore.JSON.valueToTree(run.simulation());
        json.put("strategyId",run.strategyId());json.put("strategyVersion",run.strategyVersion());json.put("configurationHash",run.configurationHash());json.set("configurationSnapshot",json.get("configuration"));
        for(var trade:json.path("portfolio").path("trades")){var row=(com.fasterxml.jackson.databind.node.ObjectNode)trade;row.put("runId",run.runId());row.put("strategyId",run.strategyId());row.put("strategyVersion",run.strategyVersion());row.put("configurationHash",run.configurationHash());}return it.andreademartini.trading.backtest.patternstable.PatternStableStore.JSON.convertValue(json,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
    }
    @GetMapping("/runs/{id}/metadata") public Object metadata(@PathVariable String id){return service.result(id);}
    @GetMapping("/runs/{id}/export/excel") public ResponseEntity<byte[]> export(@PathVariable String id){
        var run=service.result(id);
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel.export(run.simulation())));var out=new java.io.ByteArrayOutputStream()){
            var trades=book.getSheet("TRADES");int offset=trades.getRow(0).getLastCellNum();var labels=List.of("RunId","StrategyId","StrategyVersion","ConfigurationHash");var values=List.of(run.runId(),run.strategyId(),run.strategyVersion(),run.configurationHash());for(int c=0;c<labels.size();c++){trades.getRow(0).createCell(offset+c).setCellValue(labels.get(c));for(int r=1;r<=trades.getLastRowNum();r++)trades.getRow(r).createCell(offset+c).setCellValue(values.get(c));}
            var sheet=book.createSheet("STRATEGY_VERSION");int n=0;
            for(var pair:List.of(List.of("strategyId",run.strategyId()),List.of("strategyVersion",run.strategyVersion()),List.of("configurationHash",run.configurationHash()),List.of("referenceRunId",run.referenceRunId()),List.of("runId",run.runId()),List.of("datasetId",run.datasetId()))){var row=sheet.createRow(n++);row.createCell(0).setCellValue(pair.get(0));row.createCell(1).setCellValue(pair.get(1));}sheet.setColumnWidth(0,6500);sheet.setColumnWidth(1,24000);book.write(out);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=PATTERN_V2_STABLE_"+id+".xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(out.toByteArray());
        }catch(java.io.IOException e){throw new IllegalStateException("Stable V2 export failed",e);}
    }
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<Object> missing(RuntimeException e){return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
}
