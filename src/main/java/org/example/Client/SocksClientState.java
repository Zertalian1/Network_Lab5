package org.example.Client;

public enum SocksClientState {
    RECV_INIT_GREETING,
    SEND_GREETING_RESP,
    RECV_CONN_REQ,
    CONNECTING_TO_DEST,
    SEND_CONN_RESP,
    ACTIVE,
    CLOSED
}
