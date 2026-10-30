package it.andreademartini.trading.backtest.weeklytte;

import com.fasterxml.jackson.databind.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WeeklyTteStore {
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root;
    public WeeklyTteStore(){this(Path.of("data","weekly-high-tte-research"));}
    public WeeklyTteStore(Path root){this.root=root.toAbsolutePath().normalize();}
    public synchronized WeeklyTteConfiguration current(){var p=root.resolve("current.json");if(!Files.isRegularFile(p))configuration(WeeklyTteConfiguration.defaults());return read(p,WeeklyTteConfiguration.class);}
    public void configuration(WeeklyTteConfiguration c){c.validate();write(root.resolve("current.json"),c);write(root.resolve("configurations").resolve(hash(c)+".json"),c);}
    public void save(WeeklyTteRun r){if(!WeeklyTteRun.STRATEGY.equals(r.strategy()))throw new IllegalArgumentException("Weekly research runs only");write(runFile(r.runId()),r);}
    public WeeklyTteRun run(String id){return read(runFile(id),WeeklyTteRun.class);}
    private Path runFile(String id){return root.resolve("runs").resolve(UUID.fromString(id)+".json");}
    public void dataset(PatternV2Dataset d){var path=datasetFile(d.id());if(!Files.isRegularFile(path))write(path,d);}
    public PatternV2Dataset dataset(String id){var d=read(datasetFile(id),PatternV2Dataset.class);if(!id.equals(PatternV2Dataset.capture(d.bars()).id()))throw new IllegalStateException("Dataset checksum mismatch");return d;}
    private Path datasetFile(String id){if(id==null||!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid dataset hash");return root.resolve("datasets").resolve(id+".json");}
    public List<WeeklyTteRun> runs(){return list("runs",WeeklyTteRun.class).stream().sorted(Comparator.comparing(WeeklyTteRun::timestamp).reversed()).toList();}
    public List<WeeklyTteConfiguration> configurations(){return list("configurations",WeeklyTteConfiguration.class);}
    private <T> List<T> list(String folder,Class<T> type){var p=root.resolve(folder);if(!Files.isDirectory(p))return List.of();try(var paths=Files.list(p)){return paths.filter(f->f.toString().endsWith(".json")).sorted().map(f->read(f,type)).toList();}catch(IOException e){throw new UncheckedIOException(e);}}
    private <T> T read(Path p,Class<T> type){if(!Files.isRegularFile(p))throw new NoSuchElementException("Weekly research file not found");try{return JSON.readValue(p.toFile(),type);}catch(IOException e){throw new UncheckedIOException(e);}}
    private synchronized void write(Path p,Object value){try{
        Files.createDirectories(p.getParent());var temp=Files.createTempFile(p.getParent(),"weekly-",".tmp");
        try{JSON.writeValue(temp.toFile(),value);replace(temp,p);}finally{Files.deleteIfExists(temp);}
    }catch(IOException e){throw new UncheckedIOException(e);}}
    private static void replace(Path temp,Path target)throws IOException{
        for(int attempt=0;;attempt++)try{
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
            return;
        }catch(AccessDeniedException e){
            // Windows scanners can briefly hold the destination without FILE_SHARE_DELETE.
            // Retry the same replacement; never delete the previous valid JSON first.
            if(attempt>=5)throw e;
            try{Thread.sleep(25L*(attempt+1));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Interrupted saving weekly research JSON",interrupted);}
        }
    }
    public static String hash(Object value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(value)));}catch(Exception e){throw new IllegalStateException(e);}}
}
