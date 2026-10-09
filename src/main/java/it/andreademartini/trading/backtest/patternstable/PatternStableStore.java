package it.andreademartini.trading.backtest.patternstable;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;

/** Append-only stable versions/runs. Historical V1 and V2 files are never writable here. */
@Service
public class PatternStableStore {
    public static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private final Path root;
    public PatternStableStore(){this(Path.of("data/pattern-v1-stable"));}
    public PatternStableStore(Path root){this.root=root.toAbsolutePath().normalize();}
    private static JsonNode canonical(JsonNode node){
        if(node.isObject()){var result=JSON.createObjectNode();var names=new TreeSet<String>();node.fieldNames().forEachRemaining(names::add);for(var name:names)result.set(name,canonical(node.get(name)));return result;}
        if(node.isArray()){var result=JSON.createArrayNode();node.forEach(n->result.add(canonical(n)));return result;}
        if(node.isNumber())return new com.fasterxml.jackson.databind.node.DecimalNode(node.decimalValue().stripTrailingZeros());
        return node;
    }
    public static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(canonical(JSON.valueToTree(value)))));}catch(Exception e){throw new IllegalStateException(e);}}
    public synchronized void immutable(String relative,Object value){
        Path p=root.resolve(relative).normalize();if(!p.startsWith(root))throw new IllegalArgumentException("Invalid archive path");
        try {Files.createDirectories(p.getParent());byte[] bytes=JSON.writeValueAsBytes(value);
            if(Files.exists(p)){if(!canonical(JSON.readTree(bytes)).equals(canonical(JSON.readTree(Files.readAllBytes(p)))))throw new IllegalStateException("Immutable archive mismatch: "+relative);return;}
            var temp=Files.createTempFile(p.getParent(),"stable-",".tmp");
            try{Files.write(temp,bytes);Files.move(temp,p);}finally{Files.deleteIfExists(temp);}
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot archive stable version",e);}
    }
    public <T>T read(String relative,Class<T> type){try{return JSON.readValue(root.resolve(relative).toFile(),type);}catch(java.io.FileNotFoundException e){throw new NoSuchElementException(relative);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    public void save(PatternStableService.Run run){immutable("runs/"+UUID.fromString(run.runId())+".json",run);}
    public PatternStableService.Run run(String id){return read("runs/"+UUID.fromString(id)+".json",PatternStableService.Run.class);}
    public List<PatternStableService.Run> runs(){Path p=root.resolve("runs");if(!Files.isDirectory(p))return List.of();try(var files=Files.list(p)){return files.filter(f->f.toString().endsWith(".json")).map(f->read("runs/"+f.getFileName(),PatternStableService.Run.class)).sorted(Comparator.comparing(PatternStableService.Run::timestamp).reversed()).toList();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
