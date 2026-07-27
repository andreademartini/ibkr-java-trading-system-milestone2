package it.andreademartini.trading.web;

import it.andreademartini.trading.strategy.SignalAction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ManualSignalRequest(
        @NotBlank String symbol,
        @NotNull SignalAction action,
        @Positive double price,
        @Min(1) long quantity,
        String reason,
        boolean execute) {
}
