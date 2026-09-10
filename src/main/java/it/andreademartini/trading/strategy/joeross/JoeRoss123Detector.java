package it.andreademartini.trading.strategy.joeross;

import it.andreademartini.trading.strategy.TradeDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds an ordered 1-2-3 from already-confirmed, immutable swing points. */
public final class JoeRoss123Detector {

    private final TradeDirection direction;
    private SwingPoint point1;
    private SwingPoint point2;
    private SwingPoint point3;

    public JoeRoss123Detector(TradeDirection direction) {
        this.direction = direction;
    }

    public List<JoeRossEvent> onSwing(SwingPoint swing) {
        List<JoeRossEvent> events = new ArrayList<>();
        if (point3 != null) {
            return events;
        }

        SwingType point1Type = direction == TradeDirection.LONG
                ? SwingType.LOW : SwingType.HIGH;
        SwingType point2Type = direction == TradeDirection.LONG
                ? SwingType.HIGH : SwingType.LOW;

        if (point1 == null) {
            if (swing.type() == point1Type) {
                point1 = swing;
                events.add(event(JoeRossEventType.JOE_ROSS_POINT1_FOUND, swing));
            }
            return events;
        }

        if (point2 == null) {
            if (swing.type() == point1Type && moreExtremePoint1(swing)) {
                point1 = swing;
                events.add(event(JoeRossEventType.JOE_ROSS_POINT1_FOUND, swing));
            } else if (swing.type() == point2Type
                    && swing.barIndex() > point1.barIndex()) {
                point2 = swing;
                events.add(event(JoeRossEventType.JOE_ROSS_POINT2_FOUND, swing));
            }
            return events;
        }

        if (swing.type() == point2Type && moreExtremePoint2(swing)) {
            point2 = swing;
            events.add(event(JoeRossEventType.JOE_ROSS_POINT2_FOUND, swing));
            return events;
        }

        if (swing.type() == point1Type && swing.barIndex() > point2.barIndex()) {
            if (validPoint3(swing)) {
                point3 = swing;
                events.add(event(JoeRossEventType.JOE_ROSS_POINT3_FOUND, swing));
                events.add(event(JoeRossEventType.JOE_ROSS_123_COMPLETED, swing));
            } else {
                point1 = swing;
                point2 = null;
                events.add(event(JoeRossEventType.SETUP_INVALIDATED, swing));
                events.add(event(JoeRossEventType.JOE_ROSS_POINT1_FOUND, swing));
            }
        }
        return events;
    }

    public Optional<JoeRoss123Pattern> pattern() {
        if (point1 == null || point2 == null || point3 == null) {
            return Optional.empty();
        }
        return Optional.of(new JoeRoss123Pattern(direction, point1, point2, point3));
    }

    public JoeRossSetupState state() {
        if (point3 != null) {
            return JoeRossSetupState.WAITING_POINT_2_BREAKOUT;
        }
        if (point2 != null) {
            return JoeRossSetupState.POINT_2_FOUND;
        }
        if (point1 != null) {
            return JoeRossSetupState.POINT_1_FOUND;
        }
        return JoeRossSetupState.SEARCHING_POINT_1;
    }

    public void reset() {
        point1 = null;
        point2 = null;
        point3 = null;
    }

    private boolean moreExtremePoint1(SwingPoint swing) {
        return direction == TradeDirection.LONG
                ? swing.price() < point1.price()
                : swing.price() > point1.price();
    }

    private boolean moreExtremePoint2(SwingPoint swing) {
        return direction == TradeDirection.LONG
                ? swing.price() > point2.price()
                : swing.price() < point2.price();
    }

    private boolean validPoint3(SwingPoint swing) {
        return direction == TradeDirection.LONG
                ? swing.price() > point1.price() && swing.price() < point2.price()
                : swing.price() < point1.price() && swing.price() > point2.price();
    }

    private JoeRossEvent event(JoeRossEventType type, SwingPoint swing) {
        return new JoeRossEvent(
                type,
                direction,
                swing.barIndex(),
                swing.timestamp(),
                swing.price(),
                "confirmedAtBar=" + swing.confirmationBarIndex());
    }
}
