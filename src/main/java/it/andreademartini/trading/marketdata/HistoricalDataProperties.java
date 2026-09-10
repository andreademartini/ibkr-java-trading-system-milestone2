package it.andreademartini.trading.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "historical-data")
public class HistoricalDataProperties {

    private String provider = "STOOQ";

    private boolean cacheEnabled = true;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider == null || provider.isBlank()
                ? "STOOQ"
                : provider.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }
}
