package it.andreademartini.trading.backtest.export;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.strategy.TradingSignal;
import it.andreademartini.trading.strategy.joeross.JoeRossEvent;

import java.time.Instant;
import java.util.List;

public record BacktestExportRun(
        String backtestId,
        String symbol,
        String strategy,
        String direction,
        BarInterval barInterval,
        Instant startAt,
        Instant endAt,
        List<BacktestExportBar> bars,
        List<TradingSignal> signals,
        List<BacktestExportTrade> trades,
        BacktestExportMetrics metrics,
        BacktestExportParameters parameters,
        List<JoeRossEvent> strategyEvents,
        Instant createdAt) {

    public BacktestExportRun {
        bars = bars == null ? List.of() : List.copyOf(bars);
        signals = signals == null ? List.of() : List.copyOf(signals);
        trades = trades == null ? List.of() : List.copyOf(trades);
        strategyEvents = strategyEvents == null ? List.of() : List.copyOf(strategyEvents);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public BacktestExportRun(
            String backtestId,
            String symbol,
            String strategy,
            String direction,
            BarInterval barInterval,
            Instant startAt,
            Instant endAt,
            List<BacktestExportBar> bars,
            List<TradingSignal> signals,
            List<BacktestExportTrade> trades,
            BacktestExportMetrics metrics,
            BacktestExportParameters parameters,
            Instant createdAt) {
        this(backtestId, symbol, strategy, direction, barInterval, startAt, endAt,
                bars, signals, trades, metrics, parameters, List.of(), createdAt);
    }
}
