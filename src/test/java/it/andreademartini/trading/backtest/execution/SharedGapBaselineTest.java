package it.andreademartini.trading.backtest.execution;

import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv2stable.*;
import it.andreademartini.trading.backtest.patternv3.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Run once before refactoring; the baseline is never silently overwritten. No broker lifecycle. */
@EnabledIfSystemProperty(named="sharedGapBaseline",matches="true")
class SharedGapBaselineTest {
    @TempDir Path temp;
    @Test void captureBeforeRefactoring() throws Exception {
        var out=Path.of("target/shared-gap-baseline");
        if(Files.exists(out.resolve("manifest.json")))throw new IllegalStateException("Baseline already captured; preserve it");
        Files.createDirectories(out);var json=PatternStableStore.JSON;var history=new PatternV2Store();
        var v1=new PatternStableService(new PatternStableStore(temp.resolve("v1")),history,configuration(),new TradingSystemProperties()).run();
        var v2engine=new PatternV2Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),history);
        var v2=new FrozenPatternV2Service(v2engine,history,temp.resolve("v2")).run();
        var config=json.readValue(Path.of("data/pattern-v3/current.json").toFile(),PatternV3Configuration.class);
        var bars=history.dataset(v2.datasetId()).bars();
        var v3=new PatternV3Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV3Store(temp.resolve("v3"))).simulate(config,bars);
        json.writeValue(out.resolve("v1.json").toFile(),v1);json.writeValue(out.resolve("v2.json").toFile(),v2);json.writeValue(out.resolve("v3.json").toFile(),v3);
        Map<String,String> hashes=new TreeMap<>();
        for(var root:List.of(Path.of("src/main/java/it/andreademartini/trading/paper"),Path.of("src/main/java/it/andreademartini/trading/orders"),Path.of("src/main/resources/pattern-stable"),Path.of("src/main/resources/pattern-v2-stable"),Path.of("src/main/resources/pattern-research"))){
            try(var paths=Files.walk(root)){for(var p:paths.filter(Files::isRegularFile).toList())hashes.put(p.toString(),sha(p));}
        }
        for(var p:List.of(Path.of("data/pattern-v3/current.json"),Path.of("data/pattern-v3/default.json"),Path.of("data/pattern-v2/current.json")))hashes.put(p.toString(),sha(p));
        json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("manifest.json").toFile(),Map.of("datasetId",v2.datasetId(),"protectedHashes",hashes,"V3BaselineKind","Fresh pre-change simulation; no archived V3 run available","V1Equity",v1.simulation().portfolio().finalCapital(),"V2Equity",v2.simulation().portfolio().finalCapital(),"V3Equity",v3.portfolio().finalCapital()));
    }
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
}
