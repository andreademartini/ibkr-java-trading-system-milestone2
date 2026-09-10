package it.andreademartini.trading.strategy.joeross;

public record JoeRossOrderAction(
        Type type,
        JoeRossOrderCandidate candidate,
        JoeRossTradeSetup triggeredSetup,
        String reason) {

    public enum Type {
        UPSERT_CANDIDATE,
        CANCEL_CANDIDATE,
        TRIGGERED
    }

    public static JoeRossOrderAction upsert(JoeRossOrderCandidate candidate) {
        return new JoeRossOrderAction(Type.UPSERT_CANDIDATE, candidate, null, null);
    }

    public static JoeRossOrderAction cancel(String reason) {
        return new JoeRossOrderAction(Type.CANCEL_CANDIDATE, null, null, reason);
    }

    public static JoeRossOrderAction triggered(JoeRossTradeSetup setup) {
        return new JoeRossOrderAction(Type.TRIGGERED, setup.tte(), setup, null);
    }
}
