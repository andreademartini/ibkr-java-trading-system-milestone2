package it.andreademartini.trading.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "excel-export")
public class ExcelExportProperties {

    private boolean enabled = true;

    @Min(0)
    private int historicalBarsBeforeEntry = 30;

    @Min(0)
    private int historicalBarsAfterExit = 10;

    private boolean includeStrategyIndicators = true;

    private boolean includeTradeChart = false;

    @Valid
    private final ColorProperties colors = new ColorProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getHistoricalBarsBeforeEntry() {
        return historicalBarsBeforeEntry;
    }

    public void setHistoricalBarsBeforeEntry(int historicalBarsBeforeEntry) {
        this.historicalBarsBeforeEntry = historicalBarsBeforeEntry;
    }

    public int getHistoricalBarsAfterExit() {
        return historicalBarsAfterExit;
    }

    public void setHistoricalBarsAfterExit(int historicalBarsAfterExit) {
        this.historicalBarsAfterExit = historicalBarsAfterExit;
    }

    public boolean isIncludeStrategyIndicators() {
        return includeStrategyIndicators;
    }

    public void setIncludeStrategyIndicators(boolean includeStrategyIndicators) {
        this.includeStrategyIndicators = includeStrategyIndicators;
    }

    public boolean isIncludeTradeChart() {
        return includeTradeChart;
    }

    public void setIncludeTradeChart(boolean includeTradeChart) {
        this.includeTradeChart = includeTradeChart;
    }

    public ColorProperties getColors() {
        return colors;
    }

    public static class ColorProperties {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
