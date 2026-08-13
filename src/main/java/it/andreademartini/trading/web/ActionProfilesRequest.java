package it.andreademartini.trading.web;

import java.util.List;

public record ActionProfilesRequest(
        List<ActionStockConfig> stocks,
        List<ActionProfileConfig> profiles) {
}
