package it.andreademartini.trading.strategy.joeross;

import java.util.List;

public record JoeRossBarResult(
        JoeRossSetupState state,
        List<JoeRossEvent> events,
        List<JoeRossOrderAction> orderActions) {

    public JoeRossBarResult {
        events = events == null ? List.of() : List.copyOf(events);
        orderActions = orderActions == null ? List.of() : List.copyOf(orderActions);
    }
}
