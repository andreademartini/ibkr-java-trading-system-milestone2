package it.andreademartini.trading.research.pattern;

public enum ResearchDirection {
    LONG,
    SHORT,
    BOTH;

    public boolean includes(PatternDirection direction) {
        return this == BOTH || name().equals(direction.name());
    }
}
