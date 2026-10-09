package it.andreademartini.trading.research.pattern;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research/patterns")
public class PatternResearchController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PatternResearchEngine engine;
    private final PatternResearchExcelExporter excelExporter;

    public PatternResearchController(
            PatternResearchEngine engine,
            PatternResearchExcelExporter excelExporter) {
        this.engine = engine;
        this.excelExporter = excelExporter;
    }

    @PostMapping("/run")
    public PatternResearchRunView run(@RequestBody(required = false) PatternResearchRequest request) {
        return engine.run(request);
    }

    @GetMapping("/{researchRunId}")
    public PatternResearchRunView run(@PathVariable String researchRunId) {
        return engine.getRun(researchRunId);
    }

    @GetMapping("/{researchRunId}/summary")
    public List<PatternSummaryRow> summary(@PathVariable String researchRunId) {
        return engine.summary(researchRunId);
    }

    @GetMapping("/{researchRunId}/trades")
    public List<PatternOccurrence> trades(@PathVariable String researchRunId) {
        return engine.trades(researchRunId);
    }

    @GetMapping("/{researchRunId}/export/excel")
    public ResponseEntity<byte[]> export(@PathVariable String researchRunId) {
        PatternResearchRunView run = engine.getRun(researchRunId);
        byte[] workbook = excelExporter.export(
                run, engine.summary(researchRunId), engine.trades(researchRunId));
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(excelExporter.fileName(run)).build().toString())
                .body(workbook);
    }
}
