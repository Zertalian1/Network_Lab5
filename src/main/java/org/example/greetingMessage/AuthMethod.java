package org.example.greetingMessage;

import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public enum AuthMethod {    // ответ сервера, о выбранном способе аутентификации
    NO_AUTHENTICATION       ((byte)0x00),
    GSSAPI                  ((byte)0x01),
    USERNAME_PASSWORD       ((byte)0x02),
    NO_ACCEPTABLE_METHOD    ((byte)0xFF);

    private final byte value;

    private static final Map<Byte, AuthMethod> valuesToCommands = Stream.of(values())
            .collect(toMap(AuthMethod::getValue, e -> e));

    public static AuthMethod getByValue(byte value) {
        return valuesToCommands.get(value);
    }

    AuthMethod(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}