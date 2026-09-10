package it.andreademartini.trading.backtest.export;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class BacktestRunRegistry {

    private static final int MAX_RUNS = 25;

    private final Map<String, BacktestExportRun> runs = new LinkedHashMap<>();

    public synchronized void register(BacktestExportRun run) {
        runs.put(run.backtestId(), run);
        while (runs.size() > MAX_RUNS) {
            String oldest = runs.values().stream()
                    .min(Comparator.comparing(BacktestExportRun::createdAt))
                    .map(BacktestExportRun::backtestId)
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            runs.remove(oldest);
        }
    }

    public synchronized Optional<BacktestExportRun> find(String backtestId) {
        return Optional.ofNullable(runs.get(backtestId));
    }

    public synchronized Instant lastCreatedAt() {
        return runs.values().stream()
                .map(BacktestExportRun::createdAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
