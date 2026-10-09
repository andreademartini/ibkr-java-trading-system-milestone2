package it.andreademartini.trading.research.pattern;

import java.math.BigDecimal;

public record PatternDetectorParameters(
        BigDecimal hammerMinLowerShadowBodyRatio,
        BigDecimal hammerMaxUpperShadowBodyRatio,
        BigDecimal shootingStarMinUpperShadowBodyRatio,
        BigDecimal shootingStarMaxLowerShadowBodyRatio,
        boolean piercingGapRequired,
        boolean darkCloudGapRequired) {

    public PatternDetectorParameters {
        hammerMinLowerShadowBodyRatio = positive(
                hammerMinLowerShadowBodyRatio, "hammerMinLowerShadowBodyRatio");
        hammerMaxUpperShadowBodyRatio = nonNegative(
                hammerMaxUpperShadowBodyRatio, "hammerMaxUpperShadowBodyRatio");
        shootingStarMinUpperShadowBodyRatio = positive(
                shootingStarMinUpperShadowBodyRatio,
                "shootingStarMinUpperShadowBodyRatio");
        shootingStarMaxLowerShadowBodyRatio = nonNegative(
                shootingStarMaxLowerShadowBodyRatio,
                "shootingStarMaxLowerShadowBodyRatio");
    }

    public static PatternDetectorParameters defaults() {
        return new PatternDetectorParameters(
                new BigDecimal("2.0"), BigDecimal.ONE,
                new BigDecimal("2.0"), BigDecimal.ONE,
                false, false);
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " deve essere positivo");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " non può essere negativo");
        }
        return value;
    }
}
