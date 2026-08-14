package it.andreademartini.trading.risk;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.strategy.TradeDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeCalculationServiceTest {

    @Test
    void calculatesDirectionalTakeProfitPrices() {
        TradePricingService pricingService = new TradePricingService();

        assertEquals(
                BigDecimal.valueOf(102.0000).setScale(4),
                pricingService.calculateTakeProfitPrice(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(2.0),
                        TradeDirection.LONG));
        assertEquals(
                BigDecimal.valueOf(98.0000).setScale(4),
                pricingService.calculateTakeProfitPrice(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(2.0),
                        TradeDirection.SHORT));
    }

    @Test
    void sizesShortPositionWithoutRoundingUp() {
        PositionSizingService sizingService =
                new PositionSizingService(new TradePricingService());

        PositionSizingResult result = sizingService.calculate(
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(2.0),
                BigDecimal.valueOf(95),
                BigDecimal.valueOf(110),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(10_000),
                false,
                1_000);

        assertEquals(13, result.quantity());
        assertEquals(0, result.maximumRisk().compareTo(BigDecimal.valueOf(200)));
        assertEquals(0, result.effectiveRisk().compareTo(BigDecimal.valueOf(195)));
        assertEquals(0, result.notional().compareTo(BigDecimal.valueOf(1235)));
    }

    @Test
    void allocatesTakeProfitQuantitiesWithResidualOnLastTarget() {
        TradingSystemProperties properties = new TradingSystemProperties();
        TakeProfitAllocationService allocationService =
                new TakeProfitAllocationService(properties, new TradePricingService());

        List<TakeProfitAllocation> allocations = allocationService.allocate(
                101,
                BigDecimal.valueOf(100),
                TradeDirection.LONG);

        assertEquals(50, allocations.get(0).quantity());
        assertEquals(30, allocations.get(1).quantity());
        assertEquals(21, allocations.get(2).quantity());
        assertEquals(101, allocations.stream().mapToLong(TakeProfitAllocation::quantity).sum());
    }
}
