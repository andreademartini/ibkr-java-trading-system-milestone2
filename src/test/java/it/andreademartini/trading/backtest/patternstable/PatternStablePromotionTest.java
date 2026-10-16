package it.andreademartini.trading.backtest.patternstable;

import static org.junit.jupiter.api.Assertions.*;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.configuration;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.*;
import java.util.*;

/** Explicit promotion using exact archived bars; no network/provider/broker objects. */
@EnabledIfSystemProperty(named="patternStablePromotion",matches="true")
class PatternStablePromotionTest {
    @Test void archiveTimeoutRevisionWithoutChangingOriginal() throws Exception {
        var env=new StandardEnvironment();new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")).forEach(s->env.getPropertySources().addLast(s));
        var trading=Binder.get(env).bind("trading",Bindable.of(TradingSystemProperties.class)).get();
        var store=new PatternStableStore();var references=new PatternV2Store();var service=new PatternStableService(store,references,configuration(),trading);
        byte[] originalVersion=Files.readAllBytes(Path.of("data/pattern-v1-stable/versions/2.0.json"));
        byte[] currentV2=Files.readAllBytes(Path.of("data/pattern-v2/current.json"));
        var version=service.promote("2.1");
        assertEquals("2.1",version.strategyVersion());assertEquals(13,version.configuration().global().maxHoldingBars());
        assertEquals(version,store.read("versions/2.1.json",PatternStableService.Version.class));
        assertArrayEquals(originalVersion,Files.readAllBytes(Path.of("data/pattern-v1-stable/versions/2.0.json")));
        assertArrayEquals(currentV2,Files.readAllBytes(Path.of("data/pattern-v2/current.json")));
    }
    @Test void promoteAndProveEconomicEquivalence() throws Exception {
        var env=new StandardEnvironment();new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")).forEach(s->env.getPropertySources().addLast(s));
        var trading=Binder.get(env).bind("trading",Bindable.of(TradingSystemProperties.class)).get();
        var store=new PatternStableStore();var references=new PatternV2Store();var service=new PatternStableService(store,references,configuration(),trading);
        byte[] current=Files.readAllBytes(Path.of("data/pattern-v2/current.json"));byte[] original=Files.readAllBytes(Path.of("data/pattern-v2/runs/"+PatternStableService.REFERENCE+".json"));
        var version=service.promote("2.0");var actual=service.run("2.0");var source=references.run(PatternStableService.REFERENCE);
        // Exact comparison of every portfolio field including every trade/event/price/equity point.
        // Only run UUID and strategy label differ. No decimal tolerance is needed: monetary results are exact.
        var before=PatternStableStore.JSON.readTree(PatternStableStore.JSON.writeValueAsString(source.portfolio()).replace(source.runId(),"RUN").replace("PATTERN_V2","PATTERN_V1"));
        var after=PatternStableStore.JSON.readTree(PatternStableStore.JSON.writeValueAsString(actual.simulation().portfolio()).replace(actual.runId(),"RUN"));
        before.fieldNames().forEachRemaining(key->assertTrue(before.path(key).equals(after.path(key)),"Economic portfolio mismatch: "+key));
        assertEquals(source.costs(),actual.simulation().costs());assertEquals(source.analysis(),actual.simulation().analysis());
        assertEquals(1344,actual.simulation().portfolio().summary().closedTrades());assertEquals(61,actual.simulation().portfolio().summary().skippedCapital());
        assertEquals(75595.0234224412,actual.simulation().portfolio().finalCapital().doubleValue(),1e-8);
        assertArrayEquals(current,Files.readAllBytes(Path.of("data/pattern-v2/current.json")));assertArrayEquals(original,Files.readAllBytes(Path.of("data/pattern-v2/runs/"+PatternStableService.REFERENCE+".json")));
        var out=Path.of("outputs/v1-promotion-evidence");Files.createDirectories(out);
        PatternStableStore.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("PATTERN_V1_STABLE.json").toFile(),actual);
        PatternStableStore.JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("configuration.json").toFile(),version);
        var controller=new PatternStableController(service,null,null,new PatternV2ExcelExporter());byte[] excel=controller.export(actual);Files.write(out.resolve("PATTERN_V1_STABLE.xlsx"),excel);
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel))){assertNotNull(book.getSheet("TRADES"));assertNotNull(book.getSheet("CONFIGURATION"));assertEquals("2.0",book.getSheet("STRATEGY_VERSION").getRow(1).getCell(1).getStringCellValue());}
        Files.writeString(out.resolve("equivalence.json"),"{\"entirePortfolioIdenticalExceptRunIdAndStrategyLabel\":true,\"costsIdentical\":true,\"analysisIdentical\":true,\"v2CurrentUnchanged\":true,\"referenceFileUnchanged\":true,\"exactArchivedDataset\":true,\"moneyComparison\":\"exact\",\"floatingTolerance\":1e-8,\"noBrokerDependencies\":true}");
    }
}
