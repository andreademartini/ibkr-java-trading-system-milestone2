package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.config.ExcelExportProperties;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TradeHistoricalDataExporter {

    private static final Logger log =
            LoggerFactory.getLogger(TradeHistoricalDataExporter.class);

    private final ExcelExportProperties properties;
    private final HistoricalDataService historicalDataService;

    public TradeHistoricalDataExporter(
            ExcelExportProperties properties,
            HistoricalDataService historicalDataService) {
        this.properties = properties;
        this.historicalDataService = historicalDataService;
    }

    public List<BacktestExportBar> tradeWindow(
            BacktestExportRun run,
            BacktestExportTrade trade) {
        if (run == null || trade == null || run.bars().isEmpty()) {
            return List.of();
        }

        int entryIndex = clamp(trade.entryIndex(), 0, run.bars().size() - 1);
        int exitIndex = trade.exitAt() == null
                ? run.bars().getLast().index()
                : trade.exitIndex() >= entryIndex
                ? trade.exitIndex()
                : run.bars().getLast().index();
        exitIndex = clamp(exitIndex, entryIndex, run.bars().getLast().index());

        int startIndex = Math.max(
                run.bars().getFirst().index(),
                entryIndex - properties.getHistoricalBarsBeforeEntry());
        int endIndex = Math.min(
                run.bars().getLast().index(),
                exitIndex + properties.getHistoricalBarsAfterExit());

        List<BacktestExportBar> planned = run.bars().stream()
                .filter(bar -> bar.index() >= startIndex && bar.index() <= endIndex)
                .toList();
        if (planned.isEmpty()) {
            return List.of();
        }

        List<BacktestExportBar> loaded = loadFromHistoricalService(run, planned);
        return loaded.size() == planned.size() ? loaded : planned;
    }

    private List<BacktestExportBar> loadFromHistoricalService(
            BacktestExportRun run,
            List<BacktestExportBar> planned) {
        LocalDate from = toDate(planned.getFirst());
        LocalDate to = toDate(planned.getLast());
        try {
            List<HistoricalBar> historicalBars = historicalDataService.load(
                    run.symbol(),
                    from,
                    to,
                    run.barInterval());
            if (historicalBars == null || historicalBars.isEmpty()) {
                return List.of();
            }
            return alignToBacktestIndexes(historicalBars, planned);
        } catch (RuntimeException exception) {
            log.debug(
                    "Storico non disponibile per export Excel {} {}-{}: {}",
                    run.symbol(),
                    from,
                    to,
                    exception.getMessage());
            return List.of();
        }
    }

    private List<BacktestExportBar> alignToBacktestIndexes(
            List<HistoricalBar> historicalBars,
            List<BacktestExportBar> planned) {
        Map<LocalDate, BacktestExportBar> plannedByDate = planned.stream()
                .collect(Collectors.toMap(
                        this::toDate,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        return historicalBars.stream()
                .sorted(Comparator.comparing(HistoricalBar::timestamp))
                .map(bar -> {
                    BacktestExportBar plannedBar = plannedByDate.get(toDate(bar));
                    if (plannedBar == null) {
                        return null;
                    }
                    return new BacktestExportBar(
                            plannedBar.index(),
                            bar.timestamp(),
                            bar.label(),
                            bar.open(),
                            bar.high(),
                            bar.low(),
                            bar.close(),
                            bar.volume(),
                            bar.interval());
                })
                .filter(bar -> bar != null)
                .toList();
    }

    private LocalDate toDate(BacktestExportBar bar) {
        return bar.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private LocalDate toDate(HistoricalBar bar) {
        return bar.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
