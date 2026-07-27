package it.andreademartini.trading.ibkr;

public class IbkrConnectionException extends RuntimeException {

    public IbkrConnectionException(String message) {
        super(message);
    }

    public IbkrConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
