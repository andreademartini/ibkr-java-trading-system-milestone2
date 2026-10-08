package it.andreademartini.trading.backtest.patternv1;

import static it.andreademartini.trading.backtest.patternv1.PatternV1Result.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Closed-trade statistics never consume mark-to-market PnL. */
public final class PatternV1MetricsCalculator {
    private PatternV1MetricsCalculator() {}
    public static Metrics calculate(List<Trade> trades, List<Event> events, double maxDrawdown) {
        List<Trade> closed = trades.stream().filter(Trade::closed).toList();
        List<Trade> open = trades.stream().filter(t -> !t.closed()).toList();
        List<Trade> winners = closed.stream().filter(t -> t.netPnl().signum() > 0).toList();
        List<Trade> losers = closed.stream().filter(t -> t.netPnl().signum() < 0).toList();
        List<Trade> breakevens = closed.stream().filter(t -> t.netPnl().signum() == 0).toList();
        List<Trade> measured = closed.stream().filter(t -> t.resultR() != null).toList();
        double realizedGross = sum(closed, t -> t.grossPnl().doubleValue());
        double realizedNet = sum(closed, t -> t.netPnl().doubleValue());
        double unrealized = sum(open, t -> t.unrealizedPnl().doubleValue());
        double netUnrealized = sum(open, t -> t.netUnrealizedPnl().doubleValue());
        // Weighted over all measured closes (including breakevens); costs can change PnL sign without changing price R.
        Double weighted = measured.isEmpty() ? null :
                (sum(measured.stream().filter(t -> t.netPnl().signum() > 0).toList(), t -> t.resultR().doubleValue())
                + sum(measured.stream().filter(t -> t.netPnl().signum() < 0).toList(), t -> t.resultR().doubleValue())
                + sum(measured.stream().filter(t -> t.netPnl().signum() == 0).toList(), t -> t.resultR().doubleValue())) / measured.size();
        return new Metrics(trades.size(), winners.size(), losers.size(), count(closed, t -> "TIMEOUT".equals(t.exitReason())),
                realizedGross + unrealized, realizedNet + netUnrealized,
                winners.size() + losers.size() == 0 ? null : (double) winners.size() / (winners.size() + losers.size()),
                profitFactor(closed, t -> t.grossPnl().doubleValue()), average(measured, t -> t.resultR().doubleValue()),
                average(measured, t -> t.resultR().doubleValue()), average(winners, t -> t.netPnl().doubleValue()),
                average(losers, t -> t.netPnl().doubleValue()), average(closed, t -> t.mfe().doubleValue()),
                average(closed, t -> t.mae().doubleValue()), average(closed, t -> t.executedMfeR().doubleValue()),
                average(closed, t -> t.executedMaeR().doubleValue()), count(closed, Trade::sameBarAmbiguous),
                events.stream().filter(e -> e.eventType().equals("SIGNAL_SKIPPED_POSITION_ALREADY_OPEN")).count(),
                events.stream().filter(e -> e.eventType().equals("SIGNAL_SKIPPED_CAPITAL")).count(),
                closed.size(), open.size(), breakevens.size(), events.stream().filter(e -> e.eventType().equals("PATTERN_DETECTED")).count(),
                realizedGross, realizedNet, unrealized, netUnrealized, profitFactor(measured, t -> t.resultR().doubleValue()),
                profitFactor(closed, t -> t.netPnl().doubleValue()),
                average(winners, t -> t.resultR().doubleValue()), average(losers, t -> t.resultR().doubleValue()),
                average(trades, t -> t.actualRiskPercent().doubleValue()), average(trades, t -> t.orderValue().doubleValue()), maxDrawdown, weighted);
    }
    public static Double cagr(double initial, double equity, LocalDate start, LocalDate end) {
        if (start == null || end == null) return null;
        return cagr(initial, equity, ChronoUnit.DAYS.between(start, end) / 365.25);
    }
    public static Double cagr(double initial, double equity, double years) {
        return initial > 0 && equity > 0 && years > 0 ? (Math.pow(equity / initial, 1 / years) - 1) * 100 : null;
    }
    public static Double average(List<Trade> trades, ToDoubleFunction<Trade> value) {
        return trades.isEmpty() ? null : sum(trades, value) / trades.size();
    }
    public static Double profitFactor(List<Trade> trades, ToDoubleFunction<Trade> value) {
        double gains = 0, losses = 0;
        for (var trade : trades) { double pnl = value.applyAsDouble(trade); gains += Math.max(0, pnl); losses += Math.max(0, -pnl); }
        return losses == 0 ? null : gains / losses;
    }
    private static double sum(List<Trade> trades, ToDoubleFunction<Trade> value) { return trades.stream().mapToDouble(value).sum(); }
    private static long count(List<Trade> trades, java.util.function.Predicate<Trade> predicate) { return trades.stream().filter(predicate).count(); }
}
