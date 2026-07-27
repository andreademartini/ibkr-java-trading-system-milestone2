package it.andreademartini.trading.ibkr;

public record IbkrError(
        int requestId,
        Integer code,
        String message,
        String advancedRejectJson) {
}
