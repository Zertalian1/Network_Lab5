package org.example.connectionMsg;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ConnectionMsg {
    private final byte socksVersion;
    private final RequestCode requestCommand;
    private static final byte RESERVED = (byte) 0x00;
    private final AddressType addressType;
    private final InetAddress address;
    private final String domain;
    private final int port;

    public ConnectionMsg(byte socksVersion, AddressType addressType, InetAddress address, int port) {
        this.socksVersion = socksVersion;
        this.addressType = addressType;
        this.address = address;
        this.domain = null;
        this.port = port;
        requestCommand = null;
    }

    public byte[] getResponseBytes(ResponseCode responseCode){  // Ответный пакет от сервера на подключение клиента
        int size = addressType.getSize(domain) + 6;
        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.put(socksVersion);                                    // VER
        buffer.put(responseCode.getValue());                         // STATUS
        buffer.put(RESERVED);                                        // RSV
        buffer.put(addressType.getValue());                          // SOCKS5 address. TYPE

        if(addressType == AddressType.DOMAIN_NAME){
            assert domain != null;
            buffer.put(domain.getBytes(StandardCharsets.UTF_8));     // // SOCKS5 address. ADDR
        } else {
            assert address != null;
            buffer.put(address.getAddress());                        // SOCKS5 address. ADDR
        }

        buffer.putShort((short) port);                               // BNDPORT   // 2 байта
        return buffer.array();
    }

    public ConnectionMsg(ByteBuffer buffer) throws BufferUnderflowException, IllegalArgumentException {
        buffer.flip();
        socksVersion = buffer.get();
        requestCommand = RequestCode.getByValue(buffer.get());

        if(buffer.get() != RESERVED){
            throw new IllegalArgumentException();
        }
        addressType = AddressType.getByValue(buffer.get());

        if(addressType == AddressType.DOMAIN_NAME){
            byte domainNameLength = buffer.get();
            byte[] domainName = new byte[domainNameLength];
            buffer.get(domainName);

            domain = new String(domainName, StandardCharsets.UTF_8);
            address = null;
        }else {
            int size = addressType.getSize(null);
            byte[] rawInetAddress = new byte[size];
            buffer.get(rawInetAddress);

            try {
                address = InetAddress.getByAddress(rawInetAddress);
                domain = null;
            } catch (UnknownHostException uhe) {
                throw new IllegalArgumentException("Invalid address received");
            }
        }
        port = buffer.getShort();
        buffer.compact();
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    public RequestCode getRequestCommand() {
        if(requestCommand == null){
            throw new UnsupportedOperationException();
        }
        return requestCommand;
    }

    public InetAddress getAddress() {
        return address;
    }

    public String getDomain() {
        return domain;
    }

    public int getPort() {
        return port;
    }
}
