package it.andreademartini.trading.marketdata.provider;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.HistoricalBar;
import it.andreademartini.trading.marketdata.HistoricalDataProvider;

import java.time.LocalDate;
import java.util.List;

public class IbkrHistoricalDataProvider implements HistoricalDataProvider {

    @Override
    public List<HistoricalBar> getHistoricalData(
            String symbol,
            LocalDate from,
            LocalDate to,
            BarInterval interval) {
        throw new UnsupportedOperationException(
                "IbkrHistoricalDataProvider storico esteso non implementato");
    }

    @Override
    public boolean supports(BarInterval interval) {
        return false;
    }

    @Override
    public String getProviderName() {
        return "IBKR";
    }
}
