package it.andreademartini.trading.ibkr;

public record IbkrConnectionTestResult(
        boolean success,
        String host,
        int port,
        int clientId,
        Integer nextValidId,
        int serverVersion,
        String twsConnectionTime,
        String message) {
}
