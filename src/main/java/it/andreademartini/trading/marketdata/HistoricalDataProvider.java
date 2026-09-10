package it.andreademartini.trading.marketdata;

import java.time.LocalDate;
import java.util.List;

public interface HistoricalDataProvider {

    List<HistoricalBar> getHistoricalData(
            String symbol,
            LocalDate from,
            LocalDate to,
            BarInterval interval);

    boolean supports(BarInterval interval);

    String getProviderName();
}
