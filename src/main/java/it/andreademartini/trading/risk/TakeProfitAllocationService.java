package it.andreademartini.trading.risk;

import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.orders.TakeProfitAllocation;
import it.andreademartini.trading.strategy.TradeDirection;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class TakeProfitAllocationService {

    private final TradingSystemProperties properties;
    private final TradePricingService pricingService;

    public TakeProfitAllocationService(
            TradingSystemProperties properties,
            TradePricingService pricingService) {
        this.properties = properties;
        this.pricingService = pricingService;
    }

    public List<TakeProfitAllocation> allocate(
            long positionQuantity,
            BigDecimal entryPrice,
            TradeDirection direction) {
        if (positionQuantity <= 0) {
            throw new IllegalArgumentException("positionQuantity deve essere positiva");
        }
        if (!properties.getTakeProfit().isEnabled()) {
            return List.of();
        }

        List<TradingSystemProperties.TargetProperties> targets =
                properties.getTakeProfit().targets();
        List<TakeProfitAllocation> allocations = new ArrayList<>(targets.size());
        long allocated = 0;
        for (int index = 0; index < targets.size(); index++) {
            TradingSystemProperties.TargetProperties target = targets.get(index);
            boolean lastTarget = index == targets.size() - 1;
            long remaining = positionQuantity - allocated;
            long quantity = lastTarget
                    ? remaining
                    : Math.min(
                            quantityForTarget(positionQuantity, target.getPositionPercent()),
                            remaining);
            allocated += quantity;
            allocations.add(new TakeProfitAllocation(
                    index + 1,
                    BigDecimal.valueOf(target.getProfitPercent()),
                    BigDecimal.valueOf(target.getPositionPercent()),
                    quantity,
                    pricingService.calculateTakeProfitPrice(
                            entryPrice,
                            BigDecimal.valueOf(target.getProfitPercent()),
                            direction)));
        }
        return List.copyOf(allocations);
    }

    private long quantityForTarget(long positionQuantity, double positionPercent) {
        if (positionPercent <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(positionQuantity)
                .multiply(BigDecimal.valueOf(positionPercent))
                .divide(BigDecimal.valueOf(100), 12, RoundingMode.DOWN)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }
}
