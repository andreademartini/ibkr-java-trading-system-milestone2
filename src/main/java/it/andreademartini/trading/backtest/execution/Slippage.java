package it.andreademartini.trading.backtest.execution;

import java.math.*;

/** Exact arithmetic order of the verified V2 model. Percent is per side, not a fraction. */
public final class Slippage {
    private Slippage() {}
    public static BigDecimal perShare(BigDecimal base,BigDecimal percent){
        return base.multiply(percent).divide(BigDecimal.valueOf(100),MathContext.DECIMAL64);
    }
}
