package it.andreademartini.trading.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "backtest")
public class BacktestProperties {

    @NotNull
    private SameBarExecutionPolicy sameBarExecutionPolicy =
            SameBarExecutionPolicy.STOP_FIRST;

    public SameBarExecutionPolicy getSameBarExecutionPolicy() {
        return sameBarExecutionPolicy;
    }

    public void setSameBarExecutionPolicy(
            SameBarExecutionPolicy sameBarExecutionPolicy) {
        this.sameBarExecutionPolicy = sameBarExecutionPolicy;
    }
}
