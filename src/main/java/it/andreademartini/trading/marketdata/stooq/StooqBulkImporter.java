package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class StooqBulkImporter {

    public StooqBulkImportReport importZip(Path zipFile, BarInterval interval) {
        throw new UnsupportedOperationException(
                "Import bulk ZIP STOOQ non implementato in questa milestone");
    }

    public record StooqBulkImportReport(
            Path zipFile,
            BarInterval interval,
            int filesImported,
            int rowsInserted) {
    }
}
