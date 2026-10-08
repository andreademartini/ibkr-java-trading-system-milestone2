package it.andreademartini.trading.strategy.patternv1;

import it.andreademartini.trading.research.pattern.PatternDirection;

public record SymbolPatternConfiguration(DirectionPatternConfiguration longConfig,
                                         DirectionPatternConfiguration shortConfig) {
    public DirectionPatternConfiguration direction(PatternDirection direction) {
        return direction == PatternDirection.LONG ? longConfig : shortConfig;
    }
}
