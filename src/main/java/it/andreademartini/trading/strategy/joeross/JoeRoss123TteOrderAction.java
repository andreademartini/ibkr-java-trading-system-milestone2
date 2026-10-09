package it.andreademartini.trading.strategy.joeross;

public record JoeRoss123TteOrderAction(
        Type type,
        JoeRoss123TteCandidate candidate,
        JoeRoss123TteTradeSetup triggeredSetup,
        String reason) {

    public enum Type {
        UPSERT_CANDIDATE,
        CANCEL_CANDIDATE,
        TRIGGERED
    }

    public static JoeRoss123TteOrderAction upsert(JoeRoss123TteCandidate candidate) {
        return new JoeRoss123TteOrderAction(Type.UPSERT_CANDIDATE, candidate, null, null);
    }

    public static JoeRoss123TteOrderAction cancel(String reason) {
        return new JoeRoss123TteOrderAction(Type.CANCEL_CANDIDATE, null, null, reason);
    }

    public static JoeRoss123TteOrderAction triggered(JoeRoss123TteTradeSetup setup) {
        return new JoeRoss123TteOrderAction(Type.TRIGGERED, setup.tte(), setup, null);
    }
}
