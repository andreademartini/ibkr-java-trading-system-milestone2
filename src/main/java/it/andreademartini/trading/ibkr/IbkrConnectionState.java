package it.andreademartini.trading.ibkr;

public enum IbkrConnectionState {
    DISCONNECTED,
    CONNECTING,
    SOCKET_CONNECTED,
    READY,
    RECONNECT_WAIT
}
