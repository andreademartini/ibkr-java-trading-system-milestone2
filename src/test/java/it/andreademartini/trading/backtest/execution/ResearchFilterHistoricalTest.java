package it.andreademartini.trading.backtest.execution;
import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv3.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.math.*;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Acquired pre-change DAILY/WEEKLY baseline; no broker lifecycle and no operational writes. */
@EnabledIfSystemProperty(named="researchFilterHistorical",matches="true")
class ResearchFilterHistoricalTest {
 @TempDir Path temp;
 private void exact(Object a,Object b,String oldId,String newId,String message)throws Exception{var json=PatternStableStore.JSON;assertEquals(json.readTree(json.writeValueAsString(a).replace(oldId,"RUN")),json.readTree(json.writeValueAsString(b).replace(newId,"RUN")),message);}
 private Map<String,Object> metrics(PatternV3Run r){
  var p=r.portfolio();var s=p.summary();var closed=p.trades().stream().filter(t->t.closed()).toList();var m=new LinkedHashMap<String,Object>();var gaps=GapExitAnalysis.groups(ExecutionRunView.of(r)).getFirst().statistics();
  m.put("Final Equity",p.finalCapital());m.put("CAGR %",p.cagr());m.put("Max Drawdown %",p.maxDrawdownPercent());m.put("Profit Factor Net",s.profitFactorNet());m.put("Win Rate %",s.winRate()==null?null:s.winRate()*100);m.put("Closed Trades",s.closedTrades());m.put("Average Net R",closed.isEmpty()?null:closed.stream().mapToDouble(t->t.netPnl().doubleValue()/t.riskPerShare().multiply(BigDecimal.valueOf(t.quantity())).doubleValue()).average().orElseThrow());m.put("Gap Stops",gaps.gapStopCount());m.put("Gap Targets",gaps.gapTargetCount());m.put("Total Costs",r.costs().total());m.put("Average Exposure %",p.averageGrossExposurePercent());m.put("Capital Skips",s.skippedCapital());m.put("Negative Years",r.analysis().yearly().stream().filter(y->y.returnPercent()!=null&&y.returnPercent()<0).count());m.put("Periods",r.analysis().periods());m.put("Years",r.analysis().yearly());
  m.put("RunId",r.runId());m.put("StrategyVersion",r.strategyVersion());m.put("SignalTimeframe",r.signalTimeframe());m.put("ExecutionTimeframe","DAILY");m.put("ManagementTimeframe","DAILY");m.put("StartDate",p.startDate());m.put("EndDate",p.endDate());m.put("ConfigurationHash",r.configurationHash());m.put("DataFingerprint",r.datasetId());m.put("Configuration",r.configuration());m.put("ExecutionModelVersion",r.executionModelVersion());m.put("FilterModelVersion",r.filterModelVersion());
  m.put("Raw Signals",r.signalFilterAudit().size());m.put("Accepted Signals",r.signalFilterAudit().stream().filter(SignalFilterAudit.Row::finalSignalAccepted).count());m.put("Executed Signals",r.signalFilterAudit().stream().filter(SignalFilterAudit.Row::tradeExecuted).count());m.put("RVOL Available",r.signalFilterAudit().stream().filter(a->a.relativeVolume()!=null).count());m.put("Volume Issues",r.signalFilterAudit().stream().filter(a->a.relativeVolumeIssue()!=null).collect(java.util.stream.Collectors.groupingBy(SignalFilterAudit.Row::relativeVolumeIssue,java.util.TreeMap::new,java.util.stream.Collectors.counting())));return m;
 }
 private void checkExcel(byte[] bytes,PatternV3Run r)throws Exception{
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertNotNull(book.getSheet("GAP_EXIT_ANALYSIS"));assertNotNull(book.getSheet("FILTER_ANALYSIS"));var audit=book.getSheet("SIGNAL_FILTER_AUDIT");assertEquals(r.signalFilterAudit().size(),audit.getLastRowNum());var header=new HashMap<String,Integer>();for(var c:audit.getRow(0))header.put(c.toString(),c.getColumnIndex());for(int i=0;i<r.signalFilterAudit().size();i++){var s=r.signalFilterAudit().get(i);assertEquals(s.signalId(),audit.getRow(i+1).getCell(header.get("SignalId")).toString());assertEquals(s.finalSignalAccepted(),audit.getRow(i+1).getCell(header.get("FinalSignalAccepted")).getBooleanCellValue());}
   var tradeHeaders=new HashSet<String>();for(var c:book.getSheet("TRADES").getRow(0))assertTrue(tradeHeaders.add(c.toString()));assertTrue(tradeHeaders.containsAll(ResearchFilterExcel.TRADE_HEADERS));for(var s:book)for(var row:s)for(var c:row){assertNotEquals(CellType.ERROR,c.getCellType());if(c.getCellType()==CellType.NUMERIC)assertTrue(Double.isFinite(c.getNumericCellValue()));}
  }
 }
 @Test void comparePresetsAndProtectEconomicBehaviorAndOtherStrategies()throws Exception{
  var json=PatternStableStore.JSON;var base=Path.of("target/research-filter-baseline");var manifest=json.readTree(base.resolve("manifest.json").toFile());var dataset=new PatternV2Store().dataset(manifest.path("datasetId").asText());
  var svc=new PatternV3Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV3Store(temp));var results=new LinkedHashMap<String,Object>();var out=Path.of("outputs/research-filter-evidence");Files.createDirectories(out);
  for(var timeframe:SignalTimeframe.values()){
   var old=json.readValue(base.resolve(timeframe+".json").toFile(),PatternV3Run.class);assertNull(old.signalFilterAudit());var baseline=svc.simulate(old.configuration(),dataset.bars());
   assertEquals(old.configurationHash(),baseline.configurationHash());exact(old.portfolio(),baseline.portfolio(),old.runId(),baseline.runId(),"OFF portfolio changed: "+timeframe);exact(old.executions(),baseline.executions(),old.runId(),baseline.runId(),"OFF fills changed: "+timeframe);exact(old.executionAudit(),baseline.executionAudit(),old.runId(),baseline.runId(),"OFF gap audit changed: "+timeframe);
   var variants=new LinkedHashMap<String,PatternV3Run>();variants.put("BASELINE",baseline);
   for(String preset:List.of("A","B","RVOL")){var c=old.configuration();if(preset.equals("RVOL"))c=c.withResearchFilters(false,c.closeStrengthMin(),false,c.rangeMinimum(),null,true,20,new BigDecimal("1.20"),null);else c=c.withResearchFilters(true,new BigDecimal(".906"),true,new BigDecimal(preset.equals("A")?"1.38":"3.63"),null,false,20,new BigDecimal("1.20"),null);c.validate(detectors());variants.put(preset,svc.simulate(c,dataset.bars()));}
   var summary=new LinkedHashMap<String,Object>();for(var entry:variants.entrySet()){var r=entry.getValue().withResearch(entry.getValue().patternRangeAnalysis(),dataset.id(),null);var name="PATTERN_V3_"+timeframe+"_"+entry.getKey()+".xlsx";var bytes=new PatternV3ExcelExporter().export(r);checkExcel(bytes,r);Files.write(out.resolve(name),bytes);summary.put(entry.getKey(),metrics(r));}
   results.put(timeframe.name(),summary);
  }
  for(var field:manifest.path("protectedHashes").properties())assertEquals(field.getValue().asText(),SignalTimeframeBaselineTest.sha(Path.of(field.getKey())),"Protected source changed: "+field.getKey());
  json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("comparison.json").toFile(),Map.of("ExploratoryOnly",true,"DAILY_OFF_ExactParity",true,"WEEKLY_OFF_ExactParity",true,"ProtectedFilesUnchanged",true,"DatasetId",dataset.id(),"Results",results,"VolumeLimitation","Source dataset does not certify adjusted volume basis. RVOL is unavailable until an explicit verified consistent-unit declaration. RVOL-only run diagnoses unavailable data, not a validated economic effect."));Files.copy(base.resolve("manifest.json"),out.resolve("baseline-manifest.json"),StandardCopyOption.REPLACE_EXISTING);
 }
}
