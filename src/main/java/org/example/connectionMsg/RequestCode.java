package org.example.connectionMsg;

import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public enum RequestCode {
    ESTABLISH_STREAM_CONNECTION ((byte) 0x01),
    ESTABLISH_PORT_BINDING      ((byte) 0x02),
    ASSOCIATE_UDP_PORT          ((byte) 0x03);

    private final byte value;

    private static final Map<Byte, RequestCode> valuesToCommands = Stream.of(values())
            .collect(toMap(RequestCode::getValue, e -> e));

    public static RequestCode getByValue(byte value) {
        return valuesToCommands.get(value);
    }

    RequestCode(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
