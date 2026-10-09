package it.andreademartini.trading.risk;

import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.strategy.TradeDirection;
import it.andreademartini.trading.strategy.joeross.JoeRossTakeProfitParameters;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JoeRossPoint2TakeProfitServiceTest {

    private final JoeRossPoint2TakeProfitService service =
            new JoeRossPoint2TakeProfitService(null);

    @Test
    void longUsesPoint2AndDeltaTargetsWithExactAllocation() {
        List<TakeProfitAllocation> joeRoss = service.allocate(
                100, new BigDecimal("100"), new BigDecimal("102"),
                new BigDecimal("2"), TradeDirection.LONG,
                JoeRossTakeProfitParameters.defaults());

        assertThat(joeRoss).extracting(TakeProfitAllocation::price)
                .containsExactly(new BigDecimal("102"), new BigDecimal("106"),
                        new BigDecimal("110"));
        assertThat(joeRoss).extracting(TakeProfitAllocation::quantity)
                .containsExactly(60L, 20L, 20L);
    }

    @Test
    void shortMatchesRealExampleAndAssignsRoundingResidueToTp3() {
        List<TakeProfitAllocation> joeRoss = service.allocate(
                243, new BigDecimal("28.51"), new BigDecimal("27.70"),
                new BigDecimal("0.82"), TradeDirection.SHORT,
                JoeRossTakeProfitParameters.defaults());

        assertThat(joeRoss).extracting(TakeProfitAllocation::price)
                .containsExactly(new BigDecimal("27.70"), new BigDecimal("26.06"),
                        new BigDecimal("24.42"));
        assertThat(joeRoss).extracting(TakeProfitAllocation::quantity)
                .containsExactly(145L, 48L, 50L);
        assertThat(joeRoss).extracting(TakeProfitAllocation::quantity)
                .satisfies(values -> assertThat(values.stream().mapToLong(Long::longValue).sum())
                        .isEqualTo(243));
    }
}
