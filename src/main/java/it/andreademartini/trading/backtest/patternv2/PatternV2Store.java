package it.andreademartini.trading.backtest.patternv2;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Only data/pattern-v2 is writable through this repository; names never become file paths. */
@Service
public class PatternV2Store {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private final Path root;
    public record Saved(String id,String name,Instant timestamp,PatternV2Configuration configuration) {}
    public PatternV2Store() { this(Path.of("data","pattern-v2")); }
    public PatternV2Store(Path root) {this.root=root.toAbsolutePath().normalize();}
    private Path file(String area,String id) {return root.resolve(area).resolve(UUID.fromString(id)+".json");}
    public synchronized PatternV2Configuration initialize(PatternV2Configuration baseline) {
        Path defaults=root.resolve("default.json"),current=root.resolve("current.json");
        if(!Files.isRegularFile(defaults))write(defaults,baseline);
        if(!Files.isRegularFile(current))write(current,read(defaults,PatternV2Configuration.class));
        return read(current,PatternV2Configuration.class);
    }
    public PatternV2Configuration current() {return read(root.resolve("current.json"),PatternV2Configuration.class);}
    public void current(PatternV2Configuration config) {write(root.resolve("current.json"),config);}
    public PatternV2Configuration reset() {var config=read(root.resolve("default.json"),PatternV2Configuration.class);current(config);return config;}
    public Saved saveConfiguration(PatternV2Configuration config) {return saveConfiguration(config,true);}
    public Saved saveConfiguration(PatternV2Configuration config,boolean updateCurrent) {var saved=new Saved(UUID.randomUUID().toString(),config.name(),Instant.now(),config);write(file("configurations",saved.id()),saved);if(updateCurrent)current(config);return saved;}
    public Saved configuration(String id) {return read(file("configurations",id),Saved.class);}
    public List<Saved> configurations() {return list("configurations",Saved.class).stream().sorted(Comparator.comparing(Saved::timestamp).reversed()).toList();}
    public void save(PatternV2Run run) {if(!"PATTERN_V2".equals(run.strategy()))throw new IllegalArgumentException("V2 results only");write(file("runs",run.runId()),run);}
    private Path datasetFile(String id) {
        if(id==null||!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid dataset ID");
        return root.resolve("datasets").resolve(id+".json");
    }
    public void saveDataset(PatternV2Dataset dataset) {var path=datasetFile(dataset.id());if(!Files.isRegularFile(path))write(path,dataset);}
    public PatternV2Dataset dataset(String id) {
        var snapshot=read(datasetFile(id),PatternV2Dataset.class);
        if(!id.equals(PatternV2Dataset.capture(snapshot.bars()).id()))throw new IllegalStateException("Dataset checksum mismatch");
        return snapshot;
    }
    public void saveShadowAnalysis(String id,CapitalSkipShadowAnalysis.Analysis analysis){if(!UUID.fromString(id).toString().equals(analysis.runId()))throw new IllegalArgumentException("Run mismatch");write(file("shadow-analysis",id),analysis);}
    public Optional<CapitalSkipShadowAnalysis.Analysis> shadowAnalysis(String id){var path=file("shadow-analysis",id);return Files.isRegularFile(path)?Optional.of(read(path,CapitalSkipShadowAnalysis.Analysis.class)):Optional.empty();}
    public PatternV2Run run(String id) {return read(file("runs",id),PatternV2Run.class);}
    public List<PatternV2Run> runs() {return list("runs",PatternV2Run.class).stream().sorted(Comparator.comparing(PatternV2Run::timestamp).reversed()).toList();}
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
