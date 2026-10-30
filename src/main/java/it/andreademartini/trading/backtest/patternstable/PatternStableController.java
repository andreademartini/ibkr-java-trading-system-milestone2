package it.andreademartini.trading.backtest.patternstable;

import it.andreademartini.trading.backtest.patternv1.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2ExcelExporter;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api/backtest/pattern-v1")
public class PatternStableController {
    private final PatternStableService stable;
    private final PatternV1RunStore legacy;
    private final PatternV1ExcelExporter legacyExcel;
    private final PatternV2ExcelExporter excel;
    public PatternStableController(PatternStableService stable,PatternV1RunStore legacy,PatternV1ExcelExporter legacyExcel,PatternV2ExcelExporter excel){this.stable=stable;this.legacy=legacy;this.legacyExcel=legacyExcel;this.excel=excel;}
    @GetMapping("/configuration") public PatternStableService.Version configuration(){return stable.version();}
    @PostMapping("/run") public PatternV1Result run(@RequestBody(required=false) Map<String,Object> request){
        try {var risk=BacktestRiskParameter.parse(request);return (risk==null?stable.run():stable.runWithRisk(risk)).simulation().portfolio();}
        catch(IllegalArgumentException invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,invalid.getMessage());}
    }
    @GetMapping("/runs") public List<Map<String,Object>> history(){
        List<Map<String,Object>> entries=new ArrayList<>();
        for(var r:stable.history())entries.add(Map.of("runId",r.runId(),"strategyId",r.strategyId(),"strategyVersion",r.strategyVersion(),"timestamp",r.timestamp(),"configurationHash",r.configurationHash(),"riskPercent",r.simulation().configuration().global().riskPercent()));
        for(var r:legacy.history())entries.add(Map.of("runId",r.backtestId(),"strategyId",r.strategy(),"strategyVersion","1.0-legacy","configurationHash",PatternStableStore.hash(Map.of("configuration",r.configuration(),"settings",r.settings())),"riskPercent",r.settings().riskPercent()));
        return entries;
    }
    @GetMapping("/{id}/metadata") public PatternStableService.Run metadata(@PathVariable String id){return stable.result(id);}
    @GetMapping("/{id}") public PatternV1Result result(@PathVariable String id){
        try{return stable.result(id).simulation().portfolio();}catch(NoSuchElementException absent){
            try{return legacy.find(id);}catch(NoSuchElementException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());}
        }
    }
    @GetMapping("/{id}/export/excel") public ResponseEntity<byte[]> excel(@PathVariable String id){
        byte[] bytes;
        try{bytes=export(stable.result(id));}catch(NoSuchElementException e){bytes=legacyExcel.export(result(id));}
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("PATTERN_V1_"+id+".xlsx").build().toString()).body(bytes);
    }
    public byte[] export(PatternStableService.Run run){
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(excel.export(run.simulation())));var out=new ByteArrayOutputStream()){
            var sheet=book.createSheet("STRATEGY_VERSION");int i=0;
            for(var row:List.of(List.of("Strategy ID",run.strategyId()),List.of("Strategy Version",run.strategyVersion()),List.of("Run ID",run.runId()),List.of("Configuration Hash",run.configurationHash()),List.of("Reference Run",run.referenceRunId()),List.of("Dataset ID",run.datasetId()),List.of("Execution","HISTORICAL ONLY — orders disabled"))){var r=sheet.createRow(i++);r.createCell(0).setCellValue(row.get(0));r.createCell(1).setCellValue(row.get(1));}
            sheet.setColumnWidth(0,6500);sheet.setColumnWidth(1,23000);
            book.removeSheetAt(book.getSheetIndex("PORTFOLIO_SUMMARY"));
            it.andreademartini.trading.backtest.reporting.PortfolioSummaryExcel.write(book,run);
            book.write(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Stable Excel export failed",e);}
    }
}
