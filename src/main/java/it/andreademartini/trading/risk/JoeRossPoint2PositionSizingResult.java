package it.andreademartini.trading.risk;

import java.math.BigDecimal;

public record JoeRossPoint2PositionSizingResult(
        PositionSizingResult positionSizing,
        BigDecimal targetProfitPercent,
        BigDecimal targetProfitAmount,
        BigDecimal point2Delta,
        long quantityCalculatedFromPoint2,
        BigDecimal expectedProfitAtPoint2,
        BigDecimal initialStopPrice,
        BigDecimal actualRiskPerShare,
        BigDecimal actualStopRiskAmount,
        BigDecimal actualStopRiskPercent) {

    /** Compatibility alias used by existing exports and clients. */
    public BigDecimal distanceToPoint2() {
        return point2Delta;
    }
}
