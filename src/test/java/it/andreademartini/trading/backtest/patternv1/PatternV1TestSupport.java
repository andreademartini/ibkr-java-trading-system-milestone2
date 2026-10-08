package it.andreademartini.trading.backtest.patternv1;

import it.andreademartini.trading.marketdata.*;
import it.andreademartini.trading.research.pattern.*;
import it.andreademartini.trading.strategy.patternv1.*;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PatternV1TestSupport {
    public static final LocalDate START = LocalDate.of(2020, 1, 1);
    public static List<PatternDetector> detectors() { return List.of(new BullishEngulfingDetector(), new BullishHaramiDetector(), new HammerDetector(), new PiercingPatternDetector(), new ShootingStarDetector()); }
    public static PatternV1Properties configuration() {
        try {
            var environment = new StandardEnvironment();
            new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                    .forEach(source -> environment.getPropertySources().addLast(source));
            return Binder.get(environment).bind("pattern-v1", Bindable.of(PatternV1Properties.class)).get();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static BigDecimal bd(String value) { return new BigDecimal(value); }
    public static HistoricalBar bar(String symbol, int index, double open, double high, double low, double close) {
        return new HistoricalBar(symbol, START.plusDays(index).atStartOfDay(), BigDecimal.valueOf(open),
                BigDecimal.valueOf(high), BigDecimal.valueOf(low), BigDecimal.valueOf(close), 1000, BarInterval.DAILY, "TEST").withIndex(index);
    }
    public static List<HistoricalBar> engulfing(String symbol) {
        return List.of(bar(symbol, 0, 100, 101, 98, 99), bar(symbol, 1, 98.5, 101, 98.2, 100.5),
                bar(symbol, 2, 100, 105, 99, 101));
    }
    private PatternV1TestSupport() {}
}
