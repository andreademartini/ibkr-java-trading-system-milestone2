package it.andreademartini.trading.backtest.patternstable.engine;
import it.andreademartini.trading.backtest.patternv2.*;

import it.andreademartini.trading.backtest.patternv1.PatternV1Commission;
import it.andreademartini.trading.marketdata.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Explicit dataset share basis. Splits are never inferred from a low price or a price jump. */
public record PatternV2SplitCosts(List<Rule> rules) {
    public enum Basis { AUTO_PROVIDER, RAW, SPLIT_ADJUSTED }
    public record Split(LocalDate effectiveDate, BigDecimal ratio) {
        public Split {
            if (effectiveDate == null || ratio == null || ratio.signum() <= 0)
                throw new IllegalArgumentException("Split: data e rapporto positivo obbligatori");
        }
    }
    public record Rule(String symbol, Basis basis, LocalDate validFrom, LocalDate adjustedThrough, List<Split> splits) {
        public Rule {
            if (symbol == null || !symbol.trim().toUpperCase(Locale.ROOT).matches("[A-Z0-9][A-Z0-9._-]{0,31}") || basis == null
                    || validFrom == null || adjustedThrough == null || adjustedThrough.isBefore(validFrom))
                throw new IllegalArgumentException("Split: simbolo, base prezzi e date non validi");
            symbol = symbol.trim().toUpperCase(Locale.ROOT);
            splits = splits == null ? List.of() : List.copyOf(splits);
            Set<LocalDate> dates = new HashSet<>();
            for (var s : splits) if (!dates.add(s.effectiveDate()) || s.effectiveDate().isAfter(adjustedThrough) || s.effectiveDate().isBefore(validFrom))
                throw new IllegalArgumentException(symbol + ": split duplicato o fuori dal periodo della base dati");
        }
    }
    public PatternV2SplitCosts {
        rules = rules == null ? List.of() : List.copyOf(rules);
        Set<String> names = new HashSet<>();
        for (var r : rules) if (!names.add(r.symbol())) throw new IllegalArgumentException("Split: simbolo duplicato " + r.symbol());
    }
    public static PatternV2SplitCosts defaults() {
        // Verified issuer sources; this catalog covers NVDA data from 2010 onward, in the 2024 share basis.
        // https://nvidianews.nvidia.com/news/nvidia-announces-four-for-one-stock-split-pending-stockholder-approval-at-annual-meeting-set-for-june-3
        // https://investor.nvidia.com/files/doc_downloads/2024/06/nvidia-2024-stock-split_faq_investors.pdf
        return new PatternV2SplitCosts(List.of(new Rule("NVDA", Basis.AUTO_PROVIDER, LocalDate.of(2010,1,1), LocalDate.of(2024,6,10),
                List.of(new Split(LocalDate.of(2021,7,20),new BigDecimal("4")),new Split(LocalDate.of(2024,6,10),new BigDecimal("10"))))));
    }
    public BigDecimal factor(String symbol, HistoricalBar bar) {
        var rule = rules.stream().filter(r -> r.symbol().equals(symbol)).findFirst().orElse(null);
        if (rule == null || rule.basis() == Basis.RAW) return BigDecimal.ONE;
        // The local STOOQ CSV import labels its adjusted OHLC RAW. Explicit RAW overrides this known provider convention.
        if (rule.basis() == Basis.AUTO_PROVIDER && !"STOOQ".equalsIgnoreCase(bar.provider()) && bar.priceAdjustment() != PriceAdjustment.ADJUSTED)
            return BigDecimal.ONE;
        var date = bar.dateTime().toLocalDate();
        if (date.isBefore(rule.validFrom())) throw new IllegalArgumentException(symbol + ": calendario split disponibile dal " + rule.validFrom() + "; estenderlo prima di simulare date precedenti");
        var factor = BigDecimal.ONE;
        for (var split : rule.splits()) if (date.isBefore(split.effectiveDate())) factor = factor.multiply(split.ratio());
        return factor;
    }
    public static PatternV1Commission commission(PatternV1Commission fee, BigDecimal factor) {
        return factor.compareTo(BigDecimal.ONE) == 0 ? fee : new PatternV1Commission(fee.model(),fee.fixedPerOrder(),
                perShare(fee.perShare(),factor),fee.minimumPerOrder(),fee.maximumPercentOrderValue());
    }
    public static BigDecimal perShare(BigDecimal value, BigDecimal factor) { return value.divide(factor,MathContext.DECIMAL64); }
    public static BigDecimal equivalentQuantity(long quantity, BigDecimal factor) { return perShare(BigDecimal.valueOf(quantity),factor); }
}
