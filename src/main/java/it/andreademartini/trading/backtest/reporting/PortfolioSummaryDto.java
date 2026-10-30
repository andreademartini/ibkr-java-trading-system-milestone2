package it.andreademartini.trading.backtest.reporting;

import java.util.*;

/** Immutable reporting view; percent values are percentage points, never display strings. */
public record PortfolioSummaryDto(String schemaVersion, Map<String,Object> context, List<Metric> metrics) {
    public record Metric(String key,String label,String format,Double value,String reason) {}
}
