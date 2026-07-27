package it.andreademartini.trading.config;

public enum MarketDataMode {
    LIVE(1),
    FROZEN(2),
    DELAYED(3),
    DELAYED_FROZEN(4);

    private final int ibkrCode;

    MarketDataMode(int ibkrCode) {
        this.ibkrCode = ibkrCode;
    }

    public int getIbkrCode() {
        return ibkrCode;
    }
}
