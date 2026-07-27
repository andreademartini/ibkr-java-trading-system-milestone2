package it.andreademartini.trading.orders;

public enum OrderSide {
    BUY,
    SELL;

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
