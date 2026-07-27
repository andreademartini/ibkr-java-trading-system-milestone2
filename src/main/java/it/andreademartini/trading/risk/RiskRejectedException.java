package it.andreademartini.trading.risk;

public class RiskRejectedException extends RuntimeException {

    public RiskRejectedException(String message) {
        super(message);
    }
}
