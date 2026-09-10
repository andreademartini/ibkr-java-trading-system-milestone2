package it.andreademartini.trading.orders;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.strategy.TradeDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressiveStopLossServiceTest {

    private final TradingSystemProperties properties = new TradingSystemProperties();
    private final ProgressiveStopLossService service =
            new ProgressiveStopLossService(properties);

    @Test
    void longTp1FullFillMovesStopToEntry() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                longTrade(BigDecimal.valueOf(95), 100, 50),
                TakeProfitLevel.TP1,
                BigDecimal.valueOf(50),
                true);

        assertThat(result.newStopPrice()).isEqualByComparingTo("100");
        assertThat(result.newQuantity()).isEqualTo(50);
        assertThat(result.reason())
                .isEqualTo(StopLossAdjustmentReason.TP1_TO_BREAK_EVEN);
    }

    @Test
    void longTp2FullFillMovesStopToTp1() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                longTrade(BigDecimal.valueOf(100), 50, 20),
                TakeProfitLevel.TP2,
                BigDecimal.valueOf(30),
                true);

        assertThat(result.newStopPrice()).isEqualByComparingTo("102");
        assertThat(result.newQuantity()).isEqualTo(20);
        assertThat(result.reason()).isEqualTo(StopLossAdjustmentReason.TP2_TO_TP1);
    }

    @Test
    void longTp3FullFillCancelsStopAndClosesTradeWhenNoQuantityRemains() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                longTrade(BigDecimal.valueOf(102), 20, 0),
                TakeProfitLevel.TP3,
                BigDecimal.valueOf(20),
                true);

        assertThat(result.stopCancelled()).isTrue();
        assertThat(result.tradeClosed()).isTrue();
        assertThat(result.newStopPrice()).isNull();
        assertThat(result.newQuantity()).isZero();
        assertThat(result.reason()).isEqualTo(StopLossAdjustmentReason.POSITION_CLOSED);
    }

    @Test
    void longTp3FullFillMovesStopToTp2WhenQuantityRemains() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                longTrade(BigDecimal.valueOf(102), 20, 10),
                TakeProfitLevel.TP3,
                BigDecimal.valueOf(10),
                true);

        assertThat(result.newStopPrice()).isEqualByComparingTo("104");
        assertThat(result.newQuantity()).isEqualTo(10);
        assertThat(result.reason()).isEqualTo(StopLossAdjustmentReason.TP3_TO_TP2);
    }

    @Test
    void shortTp1FullFillMovesStopToEntry() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                shortTrade(BigDecimal.valueOf(105), 100, 50),
                TakeProfitLevel.TP1,
                BigDecimal.valueOf(50),
                true);

        assertThat(result.newStopPrice()).isEqualByComparingTo("100");
        assertThat(result.newQuantity()).isEqualTo(50);
        assertThat(result.reason())
                .isEqualTo(StopLossAdjustmentReason.TP1_TO_BREAK_EVEN);
    }

    @Test
    void shortTp2FullFillMovesStopToTp1() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                shortTrade(BigDecimal.valueOf(100), 50, 20),
                TakeProfitLevel.TP2,
                BigDecimal.valueOf(30),
                true);

        assertThat(result.newStopPrice()).isEqualByComparingTo("98");
        assertThat(result.newQuantity()).isEqualTo(20);
        assertThat(result.reason()).isEqualTo(StopLossAdjustmentReason.TP2_TO_TP1);
    }

    @Test
    void shortTp3FullFillCancelsStopAndClosesTradeWhenNoQuantityRemains() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                shortTrade(BigDecimal.valueOf(98), 20, 0),
                TakeProfitLevel.TP3,
                BigDecimal.valueOf(20),
                true);

        assertThat(result.stopCancelled()).isTrue();
        assertThat(result.tradeClosed()).isTrue();
        assertThat(result.newStopPrice()).isNull();
        assertThat(result.newQuantity()).isZero();
    }

    @Test
    void shortTp3FullFillMovesStopToTp2WhenQuantityRemains() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                shortTrade(BigDecimal.valueOf(98), 20, 10),
                TakeProfitLevel.TP3,
                BigDecimal.valueOf(10),
                true);

        assertThat(result.newStopPrice()).isEqualByComparingTo("96");
        assertThat(result.newQuantity()).isEqualTo(10);
        assertThat(result.reason()).isEqualTo(StopLossAdjustmentReason.TP3_TO_TP2);
    }

    @Test
    void partialFillUpdatesStopQuantityButKeepsStopPrice() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                longTrade(BigDecimal.valueOf(95), 100, 70),
                TakeProfitLevel.TP1,
                BigDecimal.valueOf(30),
                false);

        assertThat(result.stopPriceChanged()).isFalse();
        assertThat(result.stopQuantityChanged()).isTrue();
        assertThat(result.newStopPrice()).isEqualByComparingTo("95");
        assertThat(result.newQuantity()).isEqualTo(70);
        assertThat(result.reason())
                .isEqualTo(StopLossAdjustmentReason.QUANTITY_UPDATE_AFTER_PARTIAL_FILL);
    }

    @Test
    void fullFillAfterPartialMovesStopToEntry() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                longTrade(BigDecimal.valueOf(95), 70, 50),
                TakeProfitLevel.TP1,
                BigDecimal.valueOf(20),
                true);

        assertThat(result.newStopPrice()).isEqualByComparingTo("100");
        assertThat(result.newQuantity()).isEqualTo(50);
    }

    @Test
    void rejectsLongStopMoveThatWouldIncreaseRisk() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                longTrade(BigDecimal.valueOf(104), 50, 20),
                TakeProfitLevel.TP2,
                BigDecimal.valueOf(30),
                true);

        assertThat(result.stopPriceRejected()).isTrue();
        assertThat(result.rejectionReason()).isEqualTo("NEW STOP WOULD INCREASE RISK");
        assertThat(result.newStopPrice()).isEqualByComparingTo("104");
        assertThat(result.newQuantity()).isEqualTo(20);
    }

    @Test
    void rejectsShortStopMoveThatWouldIncreaseRisk() {
        StopAdjustmentResult result = service.onTakeProfitExecution(
                shortTrade(BigDecimal.valueOf(96), 50, 20),
                TakeProfitLevel.TP2,
                BigDecimal.valueOf(30),
                true);

        assertThat(result.stopPriceRejected()).isTrue();
        assertThat(result.rejectionReason()).isEqualTo("NEW STOP WOULD INCREASE RISK");
        assertThat(result.newStopPrice()).isEqualByComparingTo("96");
        assertThat(result.newQuantity()).isEqualTo(20);
    }

    private TradeStopState longTrade(
            BigDecimal currentStop,
            long currentStopQuantity,
            long remainingQuantity) {
        return new TradeStopState(
                "T001",
                TradeDirection.LONG,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(95),
                currentStop,
                100,
                remainingQuantity,
                currentStopQuantity,
                List.of(
                        allocation(1, 50, 102),
                        allocation(2, 30, 104),
                        allocation(3, 20, 108)));
    }

    private TradeStopState shortTrade(
            BigDecimal currentStop,
            long currentStopQuantity,
            long remainingQuantity) {
        return new TradeStopState(
                "T002",
                TradeDirection.SHORT,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(105),
                currentStop,
                100,
                remainingQuantity,
                currentStopQuantity,
                List.of(
                        allocation(1, 50, 98),
                        allocation(2, 30, 96),
                        allocation(3, 20, 92)));
    }

    private TakeProfitAllocation allocation(
            int level,
            long quantity,
            double price) {
        return new TakeProfitAllocation(
                level,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                quantity,
                BigDecimal.valueOf(price));
    }
}
