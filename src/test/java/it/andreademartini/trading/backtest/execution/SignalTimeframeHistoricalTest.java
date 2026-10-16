package it.andreademartini.trading.backtest.execution;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv2stable.*;
import it.andreademartini.trading.backtest.patternv3.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.math.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Real frozen dataset, no Spring lifecycle, broker services or operational run writes. */
@EnabledIfSystemProperty(named="signalTimeframeHistorical",matches="true")
class SignalTimeframeHistoricalTest {
    @TempDir Path temp;
    private void exact(Object before,Object after,String oldId,String newId,String message)throws Exception{
        var json=PatternStableStore.JSON;
        assertEquals(json.readTree(json.writeValueAsString(before).replace(oldId,"RUN")),json.readTree(json.writeValueAsString(after).replace(newId,"RUN")),message);
    }
    private Map<String,Object> statistics(PatternV3Run run){
        var p=run.portfolio();var s=p.summary();var closed=p.trades().stream().filter(t->t.closed()).toList();var gaps=GapExitAnalysis.groups(ExecutionRunView.of(run)).getFirst().statistics();
        var m=new LinkedHashMap<String,Object>();m.put("Final Equity",p.finalCapital());m.put("CAGR %",p.cagr());m.put("Max Drawdown %",p.maxDrawdownPercent());m.put("Profit Factor Net",s.profitFactorNet());m.put("Win Rate %",s.winRate()==null?null:s.winRate()*100);m.put("Closed Trades",s.closedTrades());
        m.put("Average Holding Days",closed.stream().mapToLong(t->ChronoUnit.DAYS.between(t.entryDate(),t.exitDate())+1).average().orElse(0));m.put("Average Holding DAILY Sessions",closed.stream().mapToInt(t->t.holdingBars()).average().orElse(0));
        m.put("Gap Stop Count",gaps.gapStopCount());m.put("Gap Target Count",gaps.gapTargetCount());m.put("Total Costs",run.costs().total());m.put("Capital Skips",s.skippedCapital());m.put("Average Exposure %",p.averageGrossExposurePercent());
        m.put("Worst Price R",closed.stream().map(t->t.resultR()).min(BigDecimal::compareTo).orElse(null));m.put("Worst Net R",closed.stream().map(t->t.netPnl().divide(t.actualRiskAmount(),MathContext.DECIMAL128)).min(BigDecimal::compareTo).orElse(null));
        m.put("BySymbol",run.analysis().bySymbol());m.put("ByPattern",run.analysis().byPattern());m.put("Years",run.analysis().yearly());
        m.put("ConfigurationHash",run.configurationHash());m.put("SignalTimeframe",run.signalTimeframe());m.put("ExecutionTimeframe",run.executionTimeframe());m.put("ManagementTimeframe",run.managementTimeframe());
        return m;
    }
    private void checkExcel(byte[] bytes,PatternV3Run run)throws Exception{
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            assertNotNull(book.getSheet("GAP_EXIT_ANALYSIS"));var sheet=book.getSheet("TRADES");var columns=new HashMap<String,Integer>();for(var c:sheet.getRow(0))columns.put(c.toString(),c.getColumnIndex());
            assertTrue(columns.keySet().containsAll(GapAuditExcel.HEADERS));assertTrue(columns.keySet().containsAll(List.of("SignalTimeframe","SignalPeriodStart","SignalPeriodEnd","SignalConfirmationDate")));
            for(int i=0;i<run.executions().size();i++){var t=run.executions().get(i).trade();var row=sheet.getRow(i+1);assertEquals(run.signalTimeframe().name(),row.getCell(columns.get("SignalTimeframe")).toString());assertEquals(t.signalDate().toString(),row.getCell(columns.get("SignalConfirmationDate")).toString());assertTrue(t.entryDate().isAfter(t.signalDate()));assertTrue(t.holdingBars()<=run.configuration().global().maxHoldingBars());}
            for(var s:book)for(var r:s)for(var c:r){assertNotEquals(CellType.ERROR,c.getCellType());if(c.getCellType()==CellType.NUMERIC)assertTrue(Double.isFinite(c.getNumericCellValue()));}
        }
    }
    @Test void exactDailyRegressionAndWeeklyComparisonOnFrozenData()throws Exception{
        var json=PatternStableStore.JSON;var base=Path.of("target/signal-timeframe-baseline");var manifest=json.readTree(base.resolve("manifest.json").toFile());
        var old=json.readValue(base.resolve("v3.json").toFile(),PatternV3Run.class);assertEquals(SignalTimeframe.DAILY,old.signalTimeframe());
        var history=new PatternV2Store();var dataset=history.dataset(manifest.path("datasetId").asText());var store=new PatternV3Store(temp.resolve("v3"));
        var svc=new PatternV3Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);
        var daily=svc.simulate(old.configuration(),dataset.bars());var weekly=svc.simulate(old.configuration().withSignalTimeframe(SignalTimeframe.WEEKLY),dataset.bars());
        assertEquals(old.configurationHash(),daily.configurationHash());assertNotEquals(daily.configurationHash(),weekly.configurationHash());
        exact(old.portfolio(),daily.portfolio(),old.runId(),daily.runId(),"DAILY portfolio regression");exact(old.executions(),daily.executions(),old.runId(),daily.runId(),"DAILY fills/costs regression");exact(old.executionAudit(),daily.executionAudit(),old.runId(),daily.runId(),"DAILY gap audit regression");
        exact(old.rangeSignals(),daily.rangeSignals(),old.runId(),daily.runId(),"DAILY signals regression");exact(old.bollingerAtrSignals(),daily.bollingerAtrSignals(),old.runId(),daily.runId(),"DAILY indicator regression");
        var v1=new PatternStableService(new PatternStableStore(temp.resolve("v1")),history,configuration(),new TradingSystemProperties()).run("2.2");
        var engine=new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),history);var v2=new FrozenPatternV2Service(engine,history,temp.resolve("v2")).run("1.1");
        var priorV1=json.readValue(base.resolve("v1.json").toFile(),PatternStableService.Run.class);var priorV2=json.readValue(base.resolve("v2.json").toFile(),PatternStableService.Run.class);
        exact(priorV1.simulation().portfolio(),v1.simulation().portfolio(),priorV1.runId(),v1.runId(),"V1 unchanged");exact(priorV2.simulation().portfolio(),v2.simulation().portfolio(),priorV2.runId(),v2.runId(),"V2 unchanged");
        for(var field:manifest.path("protectedHashes").properties())assertEquals(field.getValue().asText(),SignalTimeframeBaselineTest.sha(Path.of(field.getKey())),"Protected file changed: "+field.getKey());
        daily=daily.withResearch(daily.patternRangeAnalysis(),dataset.id(),null);weekly=weekly.withResearch(weekly.patternRangeAnalysis(),dataset.id(),null);store.save(daily);store.save(weekly);
        assertEquals(2,new PatternV3Controller(svc,store,new PatternV3ExcelExporter()).history().size());assertEquals(2,PatternV3RunComparison.compare(List.of(daily,weekly)).runs().size());
        var out=Path.of("outputs/signal-timeframe-evidence");Files.createDirectories(out);var exporter=new PatternV3ExcelExporter();var a=exporter.export(daily);var b=exporter.export(weekly);checkExcel(a,daily);checkExcel(b,weekly);
        Files.write(out.resolve("PATTERN_V3_DAILY.xlsx"),a);Files.write(out.resolve("PATTERN_V3_WEEKLY.xlsx"),b);
        json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("comparison.json").toFile(),Map.of("DatasetId",dataset.id(),"DailyExactParity",true,"V1ExactParity",true,"V2ExactParity",true,"DAILY",statistics(daily),"WEEKLY",statistics(weekly),"WeeklyWarnings",weekly.portfolio().warnings()));
        Files.copy(base.resolve("manifest.json"),out.resolve("baseline-manifest.json"),StandardCopyOption.REPLACE_EXISTING);
    }
}
