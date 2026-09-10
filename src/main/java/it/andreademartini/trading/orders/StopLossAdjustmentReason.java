package it.andreademartini.trading.orders;

public enum StopLossAdjustmentReason {
    INITIAL_STOP,
    TP1_TO_BREAK_EVEN,
    TP2_TO_TP1,
    TP3_TO_TP2,
    QUANTITY_UPDATE_AFTER_PARTIAL_FILL,
    POSITION_CLOSED,
    MANUAL_UPDATE
}
