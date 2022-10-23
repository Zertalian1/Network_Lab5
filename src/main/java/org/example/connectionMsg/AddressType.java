package org.example.connectionMsg;

public enum AddressType {
    IPV4_ADDRESS    ((byte) 0x01),
    DOMAIN_NAME     ((byte) 0x03),
    IPV6_ADDRESS    ((byte) 0x04);

    private final byte value;

    AddressType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public int getSize(String domain) {
        switch (this) {
            case IPV4_ADDRESS:
                return 4;
            case IPV6_ADDRESS:
                return 16;
            case DOMAIN_NAME:
                if (domain == null) {
                    throw new IllegalArgumentException();
                }
                return domain.length() + 1;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static AddressType getByValue(byte value) {
        for (AddressType type : values()) {
            if (type.getValue() == value) {
                return type;
            }
        }

        throw new IllegalArgumentException();
    }
}
