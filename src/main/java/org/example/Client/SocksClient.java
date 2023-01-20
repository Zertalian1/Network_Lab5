package org.example.Client;

import org.example.connectionMsg.ConnectionMsg;
import org.example.greetingMessage.GreetingMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class SocksClient {
    private static final int BUF_SIZE = 1024;

    private InetSocketAddress destAddress;
    private SocksClientState socksClientState;

    private final SocketChannel clientSocketChannel;
    private SocketChannel destSocketChannel;

    // Ключи для определения состояния буферов
    private final SelectionKey clientSelectionKey;
    private SelectionKey destSelectionKey;

    //  Буфер на приём/отправку сообщений
    private final ByteBuffer clientToDestBuffer = ByteBuffer.allocate(BUF_SIZE);
    private final ByteBuffer destToClientBuffer = ByteBuffer.allocate(BUF_SIZE);

    private boolean closeUponSending;

    public SocksClient(SocketChannel clientSocketChannel, SelectionKey clientSelectionKey) {
        this.clientSocketChannel = clientSocketChannel;
        this.clientSelectionKey = clientSelectionKey;
        socksClientState = SocksClientState.RECV_INIT_GREETING;
        closeUponSending = false;
    }

    public GreetingMessage getClientGreeting() throws IllegalArgumentException {
        clientToDestBuffer.mark();
        try {
            return new GreetingMessage(clientToDestBuffer);

        } catch (BufferUnderflowException bue) {
            clientToDestBuffer.reset();
            return null;
        }
    }

    public ConnectionMsg getClientConnectionRequest() throws IllegalArgumentException {
        clientToDestBuffer.mark();
        try {
            return new ConnectionMsg(clientToDestBuffer);

        } catch (BufferUnderflowException bue) {
            clientToDestBuffer.reset();
            return null;
        }
    }

    public void closeClientSide() throws IOException {
        clientSelectionKey.cancel();
        clientSocketChannel.close();
        setSocksClientState(SocksClientState.CLOSED);
    }

    public void closeDestSide() throws IOException {
        destSelectionKey.cancel();
        destSocketChannel.close();
        setSocksClientState(SocksClientState.CLOSED);
    }

    public boolean isCloseUponSending() {
        return closeUponSending;
    }

    public void setCloseUponSending(boolean closeUponSending) {
        this.closeUponSending = closeUponSending;
    }

    public InetSocketAddress getDestAddress() {
        return destAddress;
    }

    public void setDestAddress(InetSocketAddress destAddress) {
        this.destAddress = destAddress;
    }

    public ByteBuffer getClientToDestBuffer() {
        return clientToDestBuffer;
    }

    public ByteBuffer getDestToClientBuffer() {
        return destToClientBuffer;
    }

    public SelectionKey getClientSelectionKey() {
        return clientSelectionKey;
    }

    public SelectionKey getDestSelectionKey() {
        return destSelectionKey;
    }

    public void setDestSocketChannel(SocketChannel destSocketChannel) {
        this.destSocketChannel = destSocketChannel;
    }

    public void setDestSelectionKey(SelectionKey destSelectionKey) {
        this.destSelectionKey = destSelectionKey;
    }

    public SocksClientState getSocksClientState() {
        return socksClientState;
    }

    public void setSocksClientState(SocksClientState socksClientState) {
        this.socksClientState = socksClientState;
    }
}
