package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;

import java.util.List;

public record StooqDirectoryImportReport(
        String importDirectory,
        BarInterval interval,
        int filesFound,
        int filesImported,
        int filesFailed,
        int totalRows,
        int importedRows,
        int skippedRows,
        int errorRows,
        List<StooqCsvImportReport> imports,
        List<StooqFileImportError> errors) {

    public StooqDirectoryImportReport {
        imports = imports == null ? List.of() : List.copyOf(imports);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public record StooqFileImportError(
            String file,
            String message) {
    }
}
