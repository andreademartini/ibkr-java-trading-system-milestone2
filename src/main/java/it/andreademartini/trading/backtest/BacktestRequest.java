package it.andreademartini.trading.backtest;

import it.andreademartini.trading.marketdata.BarInterval;

import java.time.LocalDate;

public record BacktestRequest(
        String symbol,
        LocalDate from,
        LocalDate to,
        BarInterval interval) {
}
