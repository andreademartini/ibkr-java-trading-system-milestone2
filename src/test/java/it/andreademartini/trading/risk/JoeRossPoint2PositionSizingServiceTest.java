package it.andreademartini.trading.risk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JoeRossPoint2PositionSizingServiceTest {

    private final JoeRossPoint2PositionSizingService service =
            new JoeRossPoint2PositionSizingService(
                    new PositionSizingService(new TradePricingService()));

    @Test
    void shortUsesPoint3LowToPoint2DeltaWithoutEntryTick() {
        JoeRossPoint2PositionSizingResult result = calculate(
                "28.51", "0.82", "29.04");

        assertThat(result.targetProfitAmount()).isEqualByComparingTo("200");
        assertThat(result.point2Delta()).isEqualByComparingTo("0.82");
        assertThat(result.quantityCalculatedFromPoint2()).isEqualTo(243);
        assertThat(result.positionSizing().quantity()).isEqualTo(243);
        assertThat(result.expectedProfitAtPoint2()).isEqualByComparingTo("199.26");
        assertThat(result.actualRiskPerShare()).isEqualByComparingTo("0.53");
        assertThat(result.actualStopRiskAmount()).isEqualByComparingTo("128.79");
    }

    @Test
    void longUsesPoint2DistanceAndKeepsStopRiskSeparate() {
        JoeRossPoint2PositionSizingResult result = calculate("100", "2", "96");

        assertThat(result.quantityCalculatedFromPoint2()).isEqualTo(100);
        assertThat(result.positionSizing().quantity()).isEqualTo(100);
        assertThat(result.expectedProfitAtPoint2()).isEqualByComparingTo("200");
        assertThat(result.actualRiskPerShare()).isEqualByComparingTo("4");
        assertThat(result.actualStopRiskAmount()).isEqualByComparingTo("400");
        assertThat(result.actualStopRiskPercent()).isEqualByComparingTo("4");
    }

    @Test
    void rejectsZeroOrConfiguredMinimumDistance() {
        assertThatThrownBy(() -> calculate("100", "0", "99"))
                .isInstanceOf(RiskRejectedException.class)
                .hasMessage("POINT2_DISTANCE_TOO_SMALL");
    }

    private JoeRossPoint2PositionSizingResult calculate(
            String entry, String delta, String stop) {
        return service.calculate(
                new BigDecimal("10000"), new BigDecimal("2"),
                new BigDecimal(entry), new BigDecimal(delta), BigDecimal.ZERO,
                new BigDecimal(stop), new BigDecimal("2"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("1000000"), true, 100000);
    }
}
