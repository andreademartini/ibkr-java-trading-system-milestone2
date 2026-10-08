package it.andreademartini.trading.backtest.patternv1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;

/** Dedicated backtest artifacts, no changes to the operational audit database. */
@Service
public class PatternV1RunStore {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private final Path directory;
    public PatternV1RunStore() { this(Path.of("data", "pattern-v1", "backtests")); }
    public PatternV1RunStore(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    private Path file(String id) { return directory.resolve(UUID.fromString(id).toString() + ".json"); }
    public void save(PatternV1Result result) {
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "run-", ".tmp");
            try {
                mapper.writeValue(temp.toFile(), result);
                Files.move(temp, file(result.backtestId()), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { throw new IllegalStateException("Cannot persist PATTERN_V1 run", e); }
    }
    public PatternV1Result find(String id) {
        Path file = file(id);
        if (!Files.isRegularFile(file)) throw new java.util.NoSuchElementException("Backtest not found: " + id);
        try { return mapper.readValue(file.toFile(), PatternV1Result.class); }
        catch (IOException e) { throw new IllegalStateException("Cannot read PATTERN_V1 run", e); }
    }
}
