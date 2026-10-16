package it.andreademartini.trading.backtest.execution;
import static it.andreademartini.trading.backtest.patternv1.PatternV1TestSupport.*;
import it.andreademartini.trading.backtest.patternstable.*;
import it.andreademartini.trading.backtest.patternv2.*;
import it.andreademartini.trading.backtest.patternv3.*;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.risk.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
@EnabledIfSystemProperty(named="researchFilterBaseline",matches="true")
class ResearchFilterBaselineTest {
 @TempDir Path temp;
 @Test void captureBeforeModification()throws Exception{
  var out=Path.of("target/research-filter-baseline");if(Files.exists(out.resolve("manifest.json")))throw new IllegalStateException("Baseline already exists");Files.createDirectories(out);
  var json=PatternStableStore.JSON;var old=json.readTree(Path.of("outputs/signal-timeframe-evidence/baseline-manifest.json").toFile());var history=new PatternV2Store();var dataset=history.dataset(old.path("datasetId").asText());
  var config=json.readValue(Path.of("data/pattern-v3/current.json").toFile(),PatternV3Configuration.class).withRangeFilter(false,new java.math.BigDecimal("2.50")).withCloseStrengthFilter(false,new java.math.BigDecimal(".80"));
  var svc=new PatternV3Service(configuration(),new TradingSystemProperties(),null,detectors(),new PositionSizingService(new TradePricingService()),new PatternV3Store(temp));
  for(var tf:SignalTimeframe.values())json.writeValue(out.resolve(tf+".json").toFile(),svc.simulate(config.withSignalTimeframe(tf),dataset.bars()));
  var hashes=new TreeMap<String,String>();
  for(var root:List.of("src/main/java/it/andreademartini/trading/backtest/patternstable","src/main/java/it/andreademartini/trading/backtest/patternv2","src/main/java/it/andreademartini/trading/backtest/patternv2stable","src/main/java/it/andreademartini/trading/paper","src/main/java/it/andreademartini/trading/orders","src/main/resources/pattern-stable","src/main/resources/pattern-v2-stable"))try(var paths=Files.walk(Path.of(root))){for(var p:paths.filter(Files::isRegularFile).toList())hashes.put(p.toString(),SignalTimeframeBaselineTest.sha(p));}
  for(var file:List.of("src/main/resources/static/pattern-v1.html","src/main/resources/static/pattern-v2.html","src/main/resources/static/pattern-v1-stable.js","src/main/resources/static/pattern-v2-stable.js","data/pattern-v3/current.json","data/pattern-v3/default.json")){var p=Path.of(file);hashes.put(file,SignalTimeframeBaselineTest.sha(p));}
  json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("manifest.json").toFile(),Map.of("datasetId",dataset.id(),"protectedHashes",hashes));
 }
}
