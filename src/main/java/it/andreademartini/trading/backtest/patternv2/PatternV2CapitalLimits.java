package it.andreademartini.trading.backtest.patternv2;

import java.math.*;
import java.util.*;
import it.andreademartini.trading.backtest.patternv1.PatternV1PositionSizing;
import it.andreademartini.trading.research.pattern.PatternDirection;

/** V2-only entry limits and observational audit. Never chooses or changes executed quantity. */
public final class PatternV2CapitalLimits {
    private static final MathContext MATH = MathContext.DECIMAL64;
    private PatternV2CapitalLimits() {}
    public record Effective(BigDecimal maxOrderValue, BigDecimal maxPositionValue,
            BigDecimal maxLongNotional, BigDecimal maxShortNotional) {}
    public record Step(String constraint, long quantityBefore, long quantityAfter, long maximumQuantity,
            boolean reduced, boolean binding) {}
    public record Audit(BigDecimal equityAtEntry, BigDecimal maxOrderValueEffective,
            BigDecimal maxPositionValueEffective, BigDecimal maxLongNotionalEffective,
            BigDecimal maxShortNotionalEffective, long desiredQuantity, long finalQuantity,
            BigDecimal desiredOrderValue, BigDecimal finalOrderValue, BigDecimal actualRiskPercent,
            String quantityLimitingReason, List<String> quantityLimitingReasons,
            boolean cashLimited, List<Step> steps) {}
    public static BigDecimal resolve(PatternV2Configuration.CapitalLimitMode mode, double fixed,
            double percent, BigDecimal equity) {
        requireEquity(equity);
        return mode == PatternV2Configuration.CapitalLimitMode.CURRENT_EQUITY_PERCENT
                ? equity.multiply(BigDecimal.valueOf(percent), MATH).divide(BigDecimal.valueOf(100), MATH)
                : BigDecimal.valueOf(fixed);
    }
    public static Effective effective(PatternV2Configuration config, PatternV2Configuration.Symbol symbol, BigDecimal equity) {
        requireEquity(equity); var g=config.global();
        double orderPercent=symbol!=null&&symbol.maxOrderValuePercent()!=null?symbol.maxOrderValuePercent():g.maxOrderValuePercent();
        var order=g.maxOrderValue()!=null&&(symbol==null||symbol.maxOrderValuePercent()==null)
                ? BigDecimal.valueOf(g.maxOrderValue())
                : (g.maxOrderValueMode()==PatternV2Configuration.OrderValueMode.CURRENT_EQUITY_PERCENT?equity:BigDecimal.valueOf(g.initialCapital()))
                    .multiply(BigDecimal.valueOf(orderPercent)).divide(BigDecimal.valueOf(100),MATH).max(BigDecimal.ZERO);
        var result=new Effective(order,resolve(g.maxPositionValueMode(),g.maxPositionValue(),g.maxPositionValuePercent(),equity),
                resolve(g.maxLongNotionalMode(),g.maxLongNotional(),g.maxLongNotionalPercent(),equity),
                resolve(g.maxShortNotionalMode(),g.maxShortNotional(),g.maxShortNotionalPercent(),equity));
        for(var n:List.of(result.maxOrderValue(),result.maxPositionValue(),result.maxLongNotional(),result.maxShortNotional()))
            if(n.signum()<=0||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("INVALID_EFFECTIVE_CAPITAL_LIMIT");
        return result;
    }
    private static void requireEquity(BigDecimal equity) {
        if(equity==null||equity.signum()<=0||!Double.isFinite(equity.doubleValue()))throw new IllegalArgumentException("INVALID_ENTRY_EQUITY");
    }
    public static Audit audit(Effective limits, PatternV1PositionSizing.Decision decision, BigDecimal entry,
            long maxQuantity, PatternDirection direction) {
        List<Step> steps=new ArrayList<>();long q=decision.desiredQuantity();
        q=step(steps,"MAX_QUANTITY",q,maxQuantity);
        q=step(steps,"MAX_ORDER_VALUE",q,floor(limits.maxOrderValue(),entry));
        q=step(steps,"MAX_POSITION_VALUE",q,floor(limits.maxPositionValue(),entry));
        step(steps,direction==PatternDirection.LONG?"MAX_LONG_NOTIONAL":"MAX_SHORT_NOTIONAL",q,
                floor(direction==PatternDirection.LONG?limits.maxLongNotional():limits.maxShortNotional(),entry));
        return new Audit(decision.equity(),limits.maxOrderValue(),limits.maxPositionValue(),limits.maxLongNotional(),limits.maxShortNotional(),
                decision.desiredQuantity(),decision.quantity(),entry.multiply(BigDecimal.valueOf(decision.desiredQuantity())),decision.orderValue(),
                decision.actualRiskPercent(),decision.reason().name(),decision.reasons().stream().map(Enum::name).toList(),decision.capitalConstrained(),List.copyOf(steps));
    }
    private static long floor(BigDecimal amount,BigDecimal entry) {
        return amount.divide(entry,0,RoundingMode.DOWN).min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    private static long step(List<Step> steps,String name,long before,long max) {
        long after=Math.min(before,max);steps.add(new Step(name,before,after,max,after<before,before>0&&after==max));return after;
    }
}
