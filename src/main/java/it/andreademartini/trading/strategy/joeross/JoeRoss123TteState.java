package it.andreademartini.trading.strategy.joeross;

public enum JoeRoss123TteState {
    SEARCHING_POINT_1,
    POINT_1_FOUND,
    POINT_2_FOUND,
    WAITING_POINT_3_CORRECTION,
    TTE_BAR_1,
    TTE_BAR_2,
    TTE_BAR_3,
    WAITING_TTE_TRIGGER,
    TTE_TRIGGERED,
    TRADE_OPEN,
    TTE_EXPIRED,
    TTE_MISSED,
    SETUP_INVALIDATED
}
