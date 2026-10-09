package it.andreademartini.trading.backtest.patternv2;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.detectors;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import it.andreademartini.trading.strategy.patternv1.PatternV1Properties;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Explicit historical research experiment; not executed by the default suite. */
@EnabledIfSystemProperty(named="capitalLimitsExperiment",matches="true")
class PatternV2CapitalLimitsExperimentTest {
    @Test void compareFixedAndDynamicOnExactSavedDataset() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var store=new PatternV2Store(Path.of("data/pattern-v2"));var source=store.run("eae8d248-c355-4670-abdc-3ddd10f77412");
        var dataset=store.dataset(source.datasetId());var output=Path.of("outputs/capital-limits-evidence");Files.createDirectories(output);
        var currentBytes=Files.readAllBytes(Path.of("data/pattern-v2/current.json"));
        var service=new PatternV2Service(new PatternV1Properties(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),store);
        var fixedConfig=source.configuration().withName("V2_BASELINE_FIXED_LIMITS");ObjectNode json=mapper.valueToTree(fixedConfig);var global=(ObjectNode)json.get("global");
        for(var key:List.of("maxPositionValue","maxLongNotional","maxShortNotional")){global.put(key+"Mode","CURRENT_EQUITY_PERCENT");global.put(key+"Percent",100);}
        var dynamicConfig=mapper.treeToValue(json,PatternV2Configuration.class).withName("V2_DYNAMIC_LIMITS");
        PatternV2CapitalComparison.requireSameConfiguration(fixedConfig,dynamicConfig);
        var fixed=service.simulate(fixedConfig,dataset.bars(),source.runId());
        assertEquals(mapper.valueToTree(source.portfolio()),mapper.valueToTree(fixed.portfolio()),"Entire previous fixed portfolio must match");
        assertEquals(mapper.valueToTree(source.costs()),mapper.valueToTree(fixed.costs()));assertEquals(mapper.valueToTree(source.analysis()),mapper.valueToTree(fixed.analysis()));
        fixed=service.runOnSnapshot(fixedConfig,dataset.bars());var dynamic=service.runOnSnapshot(dynamicConfig,dataset.bars());
        var comparison=service.compareCapitalLimits(List.of(fixed.runId(),dynamic.runId()));
        for(var run:List.of(fixed,dynamic)){
            store.saveConfiguration(run.configuration(),false);
            mapper.writeValue(output.resolve(run.configuration().name()+".json").toFile(),run);
            Files.write(output.resolve(run.configuration().name()+".xlsx"),new PatternV2ExcelExporter().export(run));
            for(var execution:run.executions()){var t=execution.trade();assertTrue(t.actualRiskPercent().doubleValue()<=2);assertTrue(t.availableCashAfterEntry().signum()>=0);assertNotNull(execution.capitalLimits());}
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("comparison.json").toFile(),comparison);
        try(var book=new XSSFWorkbook()){
            var sheet=book.createSheet("FIXED_VS_DYNAMIC");var header=sheet.createRow(0);String[] labels={"Metric","V2 Fixed Limits","V2 Dynamic Limits","Difference"};for(int i=0;i<labels.length;i++)header.createCell(i).setCellValue(labels[i]);int n=1;
            for(var metric:comparison.metrics()){var row=sheet.createRow(n++);row.createCell(0).setCellValue(metric.metric());for(int i=0;i<metric.values().size();i++)if(metric.values().get(i)!=null)row.createCell(i+1).setCellValue(metric.values().get(i));if(metric.difference()!=null)row.createCell(3).setCellValue(metric.difference());}
            sheet.createFreezePane(0,1);for(int i=0;i<4;i++)sheet.setColumnWidth(i,32*256);try(var out=Files.newOutputStream(output.resolve("CAPITAL_LIMITS_COMPARISON.xlsx"))){book.write(out);}
        }
        assertArrayEquals(currentBytes,Files.readAllBytes(Path.of("data/pattern-v2/current.json")),"Experiment must not switch current configuration");
        Files.writeString(output.resolve("parity.json"),"{\"fixedPortfolioIdentical\":true,\"costsIdentical\":true,\"analysisIdentical\":true,\"currentConfigurationUnchanged\":true,\"exactDataset\":true,\"noBrokerDependencies\":true}");
        System.out.println(mapper.writeValueAsString(comparison));
    }
}
