package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv2stable.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Offline certification: exact archived data, temporary Stable stores, no Spring/broker lifecycle. */
@EnabledIfSystemProperty(named="gapAuditRegression",matches="true")
class GapAuditRegressionTest {
    static final String REFERENCE="5b3f1c64-d042-415b-b291-682389a0f2e2";
    @TempDir Path temp;
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private void samePortfolio(PatternStableService.Run before,PatternStableService.Run after)throws Exception{
        assertEquals(before.configurationHash(),after.configurationHash());assertEquals(before.datasetId(),after.datasetId());
        var json=PatternStableStore.JSON;
        var old=json.readTree(json.writeValueAsString(before.simulation().portfolio()).replace(before.runId(),after.runId()));
        var current=json.readTree(json.writeValueAsString(after.simulation().portfolio()));
        old.fieldNames().forEachRemaining(field->assertTrue(old.path(field).equals(current.path(field)),"Financial portfolio changed: "+field));
        assertEquals(before.simulation().costs(),after.simulation().costs());
        var e1=json.readTree(json.writeValueAsString(before.simulation().executions()).replace(before.runId(),after.runId()));
        var e2=json.readTree(json.writeValueAsString(after.simulation().executions()));assertTrue(e1.equals(e2),"Executions changed");
        assertEquals(before.simulation().analysis(),after.simulation().analysis());
    }
    @Test void certifyEconomicParityAndProduceAuditedWorkbooks()throws Exception{
        var sourcePath=Path.of("data/pattern-v2-stable/runs/"+REFERENCE+".json");
        var paths=List.of(sourcePath,Path.of("data/pattern-v2/current.json"),
            Path.of("data/pattern-v2/runs/"+FrozenPatternV2Service.REFERENCE+".json"),
            Path.of("data/pattern-v2/datasets/190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd.json"),
            Path.of("src/main/resources/pattern-stable/2.2.json"),Path.of("src/main/resources/pattern-v2-stable/1.1.json"));
        Map<String,String> hashes=new TreeMap<>();for(var p:paths)hashes.put(p.toString(),sha(p));
        var reference=PatternStableStore.JSON.readValue(sourcePath.toFile(),PatternStableService.Run.class);
        var history=new PatternV2Store();var engine=new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),history);
        var v2=new FrozenPatternV2Service(engine,history,temp.resolve("v2"));var newV2=v2.run("1.1");samePortfolio(reference,newV2);
        assertNotNull(v2.result(newV2.runId()).simulation().executionAudit());
        var v1=new PatternStableService(new PatternStableStore(temp.resolve("v1")),history,configuration(),new TradingSystemProperties());var newV1=v1.run("2.2");
        assertNotNull(v1.result(newV1.runId()).simulation().executionAudit());
        byte[] x1=new PatternStableController(v1,null,null,new PatternV2ExcelExporter()).export(newV1);
        byte[] x2=new FrozenPatternV2Controller(v2,new PatternV2ExcelExporter()).export(newV2.runId()).getBody();
        try(var previous=new XSSFWorkbook(Files.newInputStream(Path.of("outputs/gap-execution-evidence/PATTERN_V1_STABLE_2_2.xlsx")));var next=new XSSFWorkbook(new ByteArrayInputStream(x1))){
            var a=previous.getSheet("SUMMARY");var b=next.getSheet("SUMMARY");assertEquals(a.getLastRowNum(),b.getLastRowNum());
            for(int i=0;i<=a.getLastRowNum();i++){assertEquals(a.getRow(i).getCell(0).toString(),b.getRow(i).getCell(0).toString());assertEquals(a.getRow(i).getCell(1).toString(),b.getRow(i).getCell(1).toString(),"V1 economic summary row "+i);}
            var oldTrades=previous.getSheet("TRADES");var newTrades=next.getSheet("TRADES");assertEquals(oldTrades.getLastRowNum(),newTrades.getLastRowNum());
            var columns=GapAuditExcelTest.columns(newTrades);
            for(var header:oldTrades.getRow(0)){
                String name=header.toString();if(name.equals("tradeId"))continue;
                Integer index=columns.get(name);assertNotNull(index,name);
                for(int row=1;row<=oldTrades.getLastRowNum();row++)assertEquals(oldTrades.getRow(row).getCell(header.getColumnIndex()).toString(),newTrades.getRow(row).getCell(index).toString(),"V1 trade "+row+" "+name);
            }
        }
        for(byte[] bytes:List.of(x1,x2))try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            GapAuditExcelTest.finite(book);assertNotNull(book.getSheet("GAP_EXIT_ANALYSIS"));assertNotNull(book.getSheet("STRATEGY_VERSION"));
            assertTrue(GapAuditExcelTest.columns(book.getSheet("TRADES")).keySet().containsAll(GapAuditExcel.HEADERS));
        }
        for(var p:paths)assertEquals(hashes.get(p.toString()),sha(p),"Protected input changed: "+p);
        var out=Path.of("outputs/gap-audit-evidence");Files.createDirectories(out);
        Files.write(out.resolve("PATTERN_V1_STABLE_2_2_AUDIT.xlsx"),x1);Files.write(out.resolve("PATTERN_V2_STABLE_1_1_AUDIT.xlsx"),x2);
        var report=new LinkedHashMap<String,Object>();report.put("referenceRunId",REFERENCE);report.put("economicParity",true);report.put("protectedHashes",hashes);
        report.put("V1",summary(newV1));report.put("V2",summary(newV2));report.put("brokerObjectsCreated",false);
        PatternStableStore.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("audit-summary.json").toFile(),report);
        PatternStableStore.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("persisted-audits.json").toFile(),Map.of("V1",newV1.simulation().executionAudit(),"V2",newV2.simulation().executionAudit()));
        var sample=history.dataset(newV2.datasetId());var samples=new TreeMap<String,Object>();
        for(var entry:Map.of("AAPL",List.of("2020-08-28","2020-08-31"),"NVDA",List.of("2021-07-19","2021-07-20","2024-06-07","2024-06-10"),"TSLA",List.of("2022-08-24","2022-08-25")).entrySet()){
            samples.put(entry.getKey(),sample.symbols().get(entry.getKey()).stream().filter(b->entry.getValue().contains(b.dateTime().toLocalDate().toString())).toList());
        }
        PatternStableStore.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("split-price-samples.json").toFile(),samples);
    }
    private Map<String,Object> summary(PatternStableService.Run r){
        var p=r.simulation().portfolio();return Map.of("runId",r.runId(),"strategyId",r.strategyId(),"strategyVersion",r.strategyVersion(),"configurationHash",r.configurationHash(),
            "datasetId",r.datasetId(),"finalEquity",p.finalCapital(),"cagr",p.cagr(),"maxDrawdownPercent",p.maxDrawdownPercent(),"pfNet",p.summary().profitFactorNet(),"gapGroups",GapExitAnalysis.groups(r.simulation()));
    }
}
