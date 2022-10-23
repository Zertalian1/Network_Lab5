package org.example.connectionMsg;

public enum ResponseCode {
    REQUEST_GRANTED     ((byte)0x00),
    HOST_UNREACHABLE    ((byte)0x04),
    CMD_NOT_SUPPORTED   ((byte)0x07);

    private final byte value;

    ResponseCode(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
