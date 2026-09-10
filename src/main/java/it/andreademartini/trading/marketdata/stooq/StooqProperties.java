package it.andreademartini.trading.marketdata.stooq;

import it.andreademartini.trading.marketdata.BarInterval;
import it.andreademartini.trading.marketdata.PriceAdjustment;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "stooq")
public class StooqProperties {

    private boolean enabled = true;

    private String importDirectory = "./data/stooq";

    private String defaultMarketSuffix = "US";

    private boolean acceptZeroVolume = true;

    private PriceAdjustment priceAdjustment = PriceAdjustment.RAW;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getImportDirectory() {
        return importDirectory;
    }

    public void setImportDirectory(String importDirectory) {
        this.importDirectory = importDirectory == null || importDirectory.isBlank()
                ? "./data/stooq"
                : importDirectory.trim();
    }

    public String getDefaultMarketSuffix() {
        return defaultMarketSuffix;
    }

    public void setDefaultMarketSuffix(String defaultMarketSuffix) {
        this.defaultMarketSuffix = defaultMarketSuffix == null
                ? ""
                : defaultMarketSuffix.trim();
    }

    public boolean isAcceptZeroVolume() {
        return acceptZeroVolume;
    }

    public void setAcceptZeroVolume(boolean acceptZeroVolume) {
        this.acceptZeroVolume = acceptZeroVolume;
    }

    public PriceAdjustment getPriceAdjustment() {
        return priceAdjustment;
    }

    public void setPriceAdjustment(PriceAdjustment priceAdjustment) {
        this.priceAdjustment = priceAdjustment == null
                ? PriceAdjustment.RAW
                : priceAdjustment;
    }

    public Path importDirectoryPath() {
        return Path.of(importDirectory).toAbsolutePath().normalize();
    }

    public Path csvPath(String stooqSymbol, BarInterval interval) {
        return importDirectoryPath().resolve(stooqSymbol + ".csv");
    }

    public List<Path> csvCandidatePaths(String stooqSymbol, BarInterval interval) {
        BarInterval resolvedInterval = interval == null ? BarInterval.DAILY : interval;
        return List.of(
                csvPath(stooqSymbol, resolvedInterval),
                importDirectoryPath()
                        .resolve(resolvedInterval.directoryName())
                        .resolve(stooqSymbol + ".csv"));
    }
}
