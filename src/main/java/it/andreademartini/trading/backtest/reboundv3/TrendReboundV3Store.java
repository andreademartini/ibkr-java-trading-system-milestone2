package it.andreademartini.trading.backtest.reboundv3;

import com.fasterxml.jackson.databind.*;
import it.andreademartini.trading.backtest.patternv2.PatternV2Dataset;
import it.andreademartini.trading.backtest.weeklytte.WeeklyTteStore;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class TrendReboundV3Store {
    public static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root;
    public TrendReboundV3Store(){this(Path.of(System.getProperty("trading.research.v3.store","data/trend-pullback-rebound-v3-v1")));}
    public TrendReboundV3Store(Path root){this.root=root.toAbsolutePath().normalize();}
    public synchronized TrendReboundV3Configuration current(){var file=root.resolve("current.json");if(!Files.isRegularFile(file))configuration(TrendReboundV3Configuration.defaults());return read(file,TrendReboundV3Configuration.class);}
    public void configuration(TrendReboundV3Configuration c){c.validate();write(root.resolve("current.json"),c);write(root.resolve("configurations").resolve(WeeklyTteStore.hash(c)+".json"),c);}
    public void save(TrendReboundV3Run run){
        if(!TrendReboundV3Configuration.ID.equals(run.strategy()))throw new IllegalArgumentException("Separate research storage only");
        write(runPath(run.runId()),run);
        write(root.resolve("history").resolve(UUID.fromString(run.runId())+".json"),Map.of("runId",run.runId(),"strategy",run.strategy(),"timestamp",run.timestamp(),"name",run.configuration().name(),"configurationHash",run.configurationHash(),"datasetId",run.datasetId(),"performance",run.analysis().get("performance")));
    }
    private Path runPath(String id){return root.resolve("runs").resolve(UUID.fromString(id)+".json");}
    public TrendReboundV3Run run(String id){return read(runPath(id),TrendReboundV3Run.class);}
    private Path datasetPath(String id){if(id==null||!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid dataset fingerprint");return root.resolve("datasets").resolve(id+".json");}
    public void dataset(PatternV2Dataset snapshot){var file=datasetPath(snapshot.id());if(!Files.exists(file))write(file,snapshot);}
    public PatternV2Dataset dataset(String id){var snapshot=read(datasetPath(id),PatternV2Dataset.class);if(!id.equals(PatternV2Dataset.capture(snapshot.bars()).id()))throw new IllegalStateException("Frozen dataset checksum mismatch");return snapshot;}
    public List<Map<String,Object>> history(){var folder=root.resolve("history");if(!Files.isDirectory(folder))return List.of();try(var paths=Files.list(folder)){return paths.filter(p->p.toString().endsWith(".json")).map(p->(Map<String,Object>)read(p,Map.class)).sorted(Comparator.comparing((Map<String,Object> m)->m.get("timestamp").toString()).reversed()).toList();}catch(IOException e){throw new UncheckedIOException(e);}}
    private <T>T read(Path file,Class<T> type){if(!Files.isRegularFile(file))throw new NoSuchElementException("Research file not found");try{return JSON.readValue(file.toFile(),type);}catch(IOException e){throw new UncheckedIOException(e);}}
    private synchronized void write(Path file,Object value){
        try{Files.createDirectories(file.getParent());var temp=Files.createTempFile(file.getParent(),"rebound-",".tmp");
            try{JSON.writeValue(temp.toFile(),value);for(int retry=0;;retry++)try{try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}break;}catch(AccessDeniedException e){if(retry>=5)throw e;try{Thread.sleep(25L*(retry+1));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}}}finally{Files.deleteIfExists(temp);}
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
}
