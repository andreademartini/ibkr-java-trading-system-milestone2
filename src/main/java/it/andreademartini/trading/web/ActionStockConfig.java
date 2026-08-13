package it.andreademartini.trading.web;

import java.util.List;

public record ActionStockConfig(
        String id,
        String symbol,
        String name,
        String securityType,
        String exchange,
        String currency,
        String primaryExchange,
        List<ActionProfileConfig> strategies) {
}
