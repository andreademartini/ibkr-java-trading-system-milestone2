package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.PriceAdjustment;

import java.time.LocalDate;

public record StooqCsvImportReport(
        String symbol,
        String file,
        BarInterval interval,
        PriceAdjustment priceAdjustment,
        int rowsRead,
        int rowsValid,
        int rowsRejected,
        int duplicates,
        int rowsInserted,
        LocalDate from,
        LocalDate to,
        String status) {

    public int totalRows() {
        return rowsRead;
    }

    public int importedRows() {
        return rowsInserted;
    }

    public int skippedRows() {
        return duplicates;
    }

    public int errorRows() {
        return rowsRejected;
    }
}
