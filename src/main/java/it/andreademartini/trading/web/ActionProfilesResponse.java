package it.andreademartini.trading.web;

import it.andreademartini.trading.config.MarketDataProvider;
import it.andreademartini.trading.config.StrategyType;

import java.util.List;

public record ActionProfilesResponse(
        List<MarketDataProvider> providerOptions,
        List<StrategyType> strategyOptions,
        List<ActionStockConfig> stocks,
        List<ActionProfileConfig> profiles) {
}
