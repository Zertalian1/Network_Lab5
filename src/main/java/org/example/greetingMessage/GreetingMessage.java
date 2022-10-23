package org.example.greetingMessage;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;


public class GreetingMessage {
    private final byte socksVersion;
    private final Set<AuthMethod> authMethods = new HashSet<>();

    public GreetingMessage(byte socksVersion, AuthMethod authMethod) {
        this.socksVersion = socksVersion;
        this.authMethods.add(authMethod);
    }

    // парсим приветствие от клиента
    public GreetingMessage(ByteBuffer buffer) throws BufferUnderflowException, IllegalArgumentException {
        buffer.flip();

        socksVersion = buffer.get();
        byte authMethodsNum = buffer.get();

        if(authMethodsNum < 0){
            throw new RuntimeException();
        }
        for (int i = 0; i < authMethodsNum; ++i) {
            AuthMethod authMethod = AuthMethod.getByValue(buffer.get());

            if (authMethod != null) {
                authMethods.add(authMethod);
            }
        }
        buffer.compact();
    }

    public byte[] toByteResponse() {
        byte[] byteArray = new byte[2];
        byteArray[0] = socksVersion;
        AuthMethod authMethod = (AuthMethod) authMethods.toArray()[0];
        byteArray[1] = authMethod.getValue();
        return byteArray;
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public boolean hasAuthMethod(AuthMethod method) {
        return authMethods.contains(method);
    }
}