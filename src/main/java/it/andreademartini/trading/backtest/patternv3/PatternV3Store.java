package it.andreademartini.trading.backtest.patternv3;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Only data/pattern-v3 is writable through this repository; names never become file paths. */
@Service
public class PatternV3Store {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private final Path root;
    public record Saved(String id,String name,Instant timestamp,PatternV3Configuration configuration) {}
    public PatternV3Store() { this(Path.of("data","pattern-v3")); }
    public PatternV3Store(Path root) {this.root=root.toAbsolutePath().normalize();}
    private Path file(String area,String id) {return root.resolve(area).resolve(UUID.fromString(id)+".json");}
    public synchronized PatternV3Configuration initialize(PatternV3Configuration baseline) {
        Path defaults=root.resolve("default.json"),current=root.resolve("current.json");
        if(!Files.isRegularFile(defaults))write(defaults,baseline);
        if(!Files.isRegularFile(current))write(current,read(defaults,PatternV3Configuration.class));
        return read(current,PatternV3Configuration.class);
    }
    public PatternV3Configuration current() {return read(root.resolve("current.json"),PatternV3Configuration.class);}
    public void current(PatternV3Configuration config) {write(root.resolve("current.json"),config);}
    public PatternV3Configuration reset() {var config=read(root.resolve("default.json"),PatternV3Configuration.class);current(config);return config;}
    public Saved saveConfiguration(PatternV3Configuration config) {return saveConfiguration(config,true);}
    public Saved saveConfiguration(PatternV3Configuration config,boolean updateCurrent) {var saved=new Saved(UUID.randomUUID().toString(),config.name(),Instant.now(),config);write(file("configurations",saved.id()),saved);if(updateCurrent)current(config);return saved;}
    public Saved configuration(String id) {return read(file("configurations",id),Saved.class);}
    public List<Saved> configurations() {return list("configurations",Saved.class).stream().sorted(Comparator.comparing(Saved::timestamp).reversed()).toList();}
    public void save(PatternV3Run run) {if(!"PATTERN_V3".equals(run.strategy()))throw new IllegalArgumentException("V2 results only");write(file("runs",run.runId()),run);}
    private Path datasetFile(String id) {
        if(id==null||!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid dataset ID");
        return root.resolve("datasets").resolve(id+".json");
    }
    public void saveDataset(PatternV3Dataset dataset) {var path=datasetFile(dataset.id());if(!Files.isRegularFile(path))write(path,dataset);}
    public PatternV3Dataset dataset(String id) {
        var snapshot=read(datasetFile(id),PatternV3Dataset.class);
        if(!id.equals(PatternV3Dataset.capture(snapshot.bars()).id()))throw new IllegalStateException("Dataset checksum mismatch");
        return snapshot;
    }
    public void saveShadowAnalysis(String id,CapitalSkipShadowAnalysis.Analysis analysis){if(!UUID.fromString(id).toString().equals(analysis.runId()))throw new IllegalArgumentException("Run mismatch");write(file("shadow-analysis",id),analysis);}
    public Optional<CapitalSkipShadowAnalysis.Analysis> shadowAnalysis(String id){var path=file("shadow-analysis",id);return Files.isRegularFile(path)?Optional.of(read(path,CapitalSkipShadowAnalysis.Analysis.class)):Optional.empty();}
    public PatternV3Run run(String id) {return read(file("runs",id),PatternV3Run.class);}
    public List<PatternV3Run> runs() {return list("runs",PatternV3Run.class).stream().sorted(Comparator.comparing(PatternV3Run::timestamp).reversed()).toList();}
    private <T> List<T> list(String area,Class<T> type) {
        Path directory=root.resolve(area);if(!Files.isDirectory(directory))return List.of();
        try(var files=Files.list(directory)) {return files.filter(p->p.getFileName().toString().endsWith(".json")).map(p->read(p,type)).toList();}
        catch(IOException e) {throw new IllegalStateException("Impossibile leggere cronologia V2",e);}
    }
    private <T> T read(Path path,Class<T> type) {
        if(!Files.isRegularFile(path))throw new NoSuchElementException("File V2 non trovato");
        try{return mapper.readValue(path.toFile(),type);}catch(IOException e){throw new IllegalStateException("Impossibile leggere file V2",e);}
    }
    private synchronized void write(Path path,Object value) {
        try {
            Files.createDirectories(path.getParent());var temp=Files.createTempFile(path.getParent(),"v2-",".tmp");
            try {mapper.writeValue(temp.toFile(),value);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
        }catch(IOException e){throw new IllegalStateException("Impossibile salvare file V2",e);}
    }
}
