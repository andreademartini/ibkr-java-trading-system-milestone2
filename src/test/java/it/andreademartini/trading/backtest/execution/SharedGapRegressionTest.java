package it.andreademartini.trading.backtest.execution;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1Result.Trade;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv2stable.*;
import it.andreademartini.trading.backtest.patternv3.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named="sharedGapRegression",matches="true")
class SharedGapRegressionTest {
    @TempDir Path temp;
    private static String key(Trade t){return t.symbol()+"|"+t.direction()+"|"+t.pattern()+"|"+t.signalDate();}
    private static boolean samePrice(BigDecimal a,BigDecimal b){return a==null||b==null?Objects.equals(a,b):a.compareTo(b)==0;}
    private void exact(Object old,Object fresh,String oldId,String newId,String message)throws Exception{
        var json=PatternStableStore.JSON;
        var a=json.readTree(json.writeValueAsString(old).replace(oldId,"RUN"));var b=json.readTree(json.writeValueAsString(fresh).replace(newId,"RUN"));
        assertTrue(a.equals(b),message);
    }
    private Map<String,Object> metrics(ExecutionRunView view){
        var p=view.portfolio();var m=p.summary();var result=new LinkedHashMap<String,Object>();
        result.put("Final Equity",p.finalCapital());result.put("CAGR %",p.cagr());result.put("Max Drawdown %",p.maxDrawdownPercent());result.put("PF Net",m.profitFactorNet());
        result.put("Win Rate %",m.winRate()==null?null:m.winRate()*100);result.put("Closed Trades",m.closedTrades());
        result.put("Total Costs",view.executions().stream().map(e->e.commission().add(e.slippageCost()).add(e.transactionCosts())).reduce(BigDecimal.ZERO,BigDecimal::add));
        var stats=GapExitAnalysis.groups(view).getFirst().statistics();result.put("Gap Stop Count",stats.gapStopCount());result.put("Gap Target Count",stats.gapTargetCount());result.put("Capital Skips",m.skippedCapital());
        return result;
    }
    private Map<String,Object> compare(ExecutionRunView before,ExecutionRunView after){
        var rows=new ArrayList<Map<String,Object>>();var a=metrics(before);var b=metrics(after);
        for(String name:a.keySet()){
            var row=new LinkedHashMap<String,Object>();row.put("metric",name);row.put("before",a.get(name));row.put("after",b.get(name));
            row.put("difference",a.get(name) instanceof Number x&&b.get(name) instanceof Number y?new BigDecimal(y.toString()).subtract(new BigDecimal(x.toString())):null);rows.add(row);
        }
        var old=new HashMap<String,Trade>();before.executions().forEach(e->{if(old.put(key(e.trade()),e.trade())!=null)throw new IllegalStateException("Ambiguous signal key");});
        var current=new HashMap<String,Trade>();after.executions().forEach(e->current.put(key(e.trade()),e.trade()));
        var changes=new ArrayList<Map<String,Object>>();long direct=0,quantity=0,onlyBefore=0,onlyAfter=0;
        var audit=new HashMap<String,GapExecutionAudit.TradeAudit>();if(after.executionAudit()!=null)after.executionAudit().trades().forEach(t->audit.put(t.tradeId(),t));
        for(var entry:current.entrySet()){
            var t=entry.getValue();var prior=old.get(entry.getKey());if(prior==null){onlyAfter++;continue;}
            boolean changed=t.quantity()!=prior.quantity()||!Objects.equals(t.exitDate(),prior.exitDate())||t.netPnl().compareTo(prior.netPnl())!=0||!samePrice(t.exitPrice(),prior.exitPrice());
            if(t.quantity()!=prior.quantity())quantity++;
            if(changed){var gap=audit.get(t.tradeId());boolean gapFill=gap!=null&&Boolean.TRUE.equals(gap.gapExitFlag())&&(!Objects.equals(t.exitDate(),prior.exitDate())||!samePrice(t.exitPrice(),prior.exitPrice()));if(gapFill)direct++;
                var record=new LinkedHashMap<String,Object>();record.put("signal",entry.getKey());record.put("before",prior);record.put("after",t);record.put("directOpeningExitChanged",gapFill);changes.add(record);}
        }
        for(var k:old.keySet())if(!current.containsKey(k))onlyBefore++;
        return Map.of("metrics",rows,"changedCommonTrades",changes,"changedCommonCount",changes.size(),"directOpeningExitChanges",direct,
            "quantityChanges",quantity,"onlyBefore",onlyBefore,"onlyAfter",onlyAfter,"gapGroups",GapExitAnalysis.groups(after));
    }
    private void checkWorkbook(byte[] bytes,ExecutionRunView view)throws Exception{
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            var trades=book.getSheet("TRADES");var columns=new HashMap<String,Integer>();for(var c:trades.getRow(0))columns.put(c.toString(),c.getColumnIndex());
            assertTrue(columns.keySet().containsAll(GapAuditExcel.HEADERS));assertNotNull(book.getSheet("GAP_EXIT_ANALYSIS"));
            var audits=new HashMap<String,GapExecutionAudit.TradeAudit>();view.executionAudit().trades().forEach(a->audits.put(a.tradeId(),a));
            for(int i=0;i<view.executions().size();i++){
                var trade=view.executions().get(i).trade();if(!trade.closed())continue;var a=audits.get(trade.tradeId());assertNull(a.issue());
                assertEquals(a.gapExitType().name(),trades.getRow(i+1).getCell(columns.get("GapExitType")).toString());
                assertEquals(a.exitBarOpen().doubleValue(),trades.getRow(i+1).getCell(columns.get("ExitBarOpen")).getNumericCellValue());
            }
            for(var s:book)for(var row:s)for(var c:row){assertNotEquals(CellType.ERROR,c.getCellType());if(c.getCellType()==CellType.NUMERIC)assertTrue(Double.isFinite(c.getNumericCellValue()));}
        }
    }
    @Test void protectV1V2AndCompareV3OnTheSameFrozenData()throws Exception{
        var json=PatternStableStore.JSON;var baseline=Path.of("target/shared-gap-baseline");
        var manifest=json.readTree(baseline.resolve("manifest.json").toFile());
        var oldV1=json.readValue(baseline.resolve("v1.json").toFile(),PatternStableService.Run.class);var oldV2=json.readValue(baseline.resolve("v2.json").toFile(),PatternStableService.Run.class);
        var oldV3=json.readValue(baseline.resolve("v3.json").toFile(),PatternV3Run.class);
        var history=new PatternV2Store();var dataset=history.dataset(manifest.path("datasetId").asText());
        var v1svc=new PatternStableService(new PatternStableStore(temp.resolve("v1")),history,configuration(),new TradingSystemProperties());var v1=v1svc.run("2.2");
        var v2svc=new FrozenPatternV2Service(new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),history),history,temp.resolve("v2"));var v2=v2svc.run("1.1");
        for(var pair:List.of(List.of(oldV1,v1),List.of(oldV2,v2))){
            var a=pair.get(0);var b=pair.get(1);assertEquals(a.configurationHash(),b.configurationHash());assertEquals(a.datasetId(),b.datasetId());
            exact(a.simulation().portfolio(),b.simulation().portfolio(),a.runId(),b.runId(),"Stable financial portfolio changed");
            exact(a.simulation().executions(),b.simulation().executions(),a.runId(),b.runId(),"Stable fills/costs changed");
            exact(a.simulation().executionAudit(),b.simulation().executionAudit(),a.runId(),b.runId(),"Stable audit changed");
            assertEquals(GapExitAnalysis.groups(a.simulation()),GapExitAnalysis.groups(b.simulation()));
        }
        var v3svc=new PatternV3Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV3Store(temp.resolve("v3")));
        var legacyReplay=v3svc.simulate(oldV3.configuration(),dataset.bars(),ExitFillPolicy.LEVEL_ONLY);
        exact(oldV3.portfolio(),legacyReplay.portfolio(),oldV3.runId(),legacyReplay.runId(),"Historical V3 model no longer reproducible");
        var v3=v3svc.simulate(oldV3.configuration(),dataset.bars());v3=v3.withResearch(v3.patternRangeAnalysis(),dataset.id(),null);assertEquals(oldV3.configurationHash(),v3.configurationHash());
        var x1=new PatternStableController(v1svc,null,null,new PatternV2ExcelExporter()).export(v1);
        var x2=new FrozenPatternV2Controller(v2svc,new PatternV2ExcelExporter()).export(v2.runId()).getBody();
        var x3=new PatternV3ExcelExporter().export(v3);
        checkWorkbook(x1,ExecutionRunView.of(v1.simulation()));checkWorkbook(x2,ExecutionRunView.of(v2.simulation()));checkWorkbook(x3,ExecutionRunView.of(v3));
        var fields=manifest.path("protectedHashes").properties();for(var field:fields)assertEquals(field.getValue().asText(),SharedGapBaselineTest.sha(Path.of(field.getKey())),"Protected PAPER/orders/config changed: "+field.getKey());
        var out=Path.of("outputs/shared-gap-evidence");Files.createDirectories(out);
        Files.write(out.resolve("PATTERN_V1_STABLE_2_2.xlsx"),x1);Files.write(out.resolve("PATTERN_V2_STABLE_1_1.xlsx"),x2);Files.write(out.resolve("PATTERN_V3_OPEN_FIRST.xlsx"),x3);
        json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("comparison.json").toFile(),Map.of("V1",compare(ExecutionRunView.of(oldV1.simulation()),ExecutionRunView.of(v1.simulation())),
            "V2",compare(ExecutionRunView.of(oldV2.simulation()),ExecutionRunView.of(v2.simulation())),"V3",compare(ExecutionRunView.of(oldV3),ExecutionRunView.of(v3)),
            "V2ExactParity",true,"V1ExactParity",true,"V3LegacyReplayExactParity",true,"V3BaselineKind",manifest.path("V3BaselineKind").asText(),"noBrokerObjects",true));
        json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("baseline-manifest.json").toFile(),manifest);
    }
}
