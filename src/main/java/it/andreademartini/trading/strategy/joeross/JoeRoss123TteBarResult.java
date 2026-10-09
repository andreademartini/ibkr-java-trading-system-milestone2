package it.andreademartini.trading.strategy.joeross;

import java.util.List;

public record JoeRoss123TteBarResult(
        JoeRoss123TteState state,
        List<JoeRossEvent> events,
        List<JoeRoss123TteOrderAction> orderActions) {

    public JoeRoss123TteBarResult {
        events = events == null ? List.of() : List.copyOf(events);
        orderActions = orderActions == null ? List.of() : List.copyOf(orderActions);
    }
}
