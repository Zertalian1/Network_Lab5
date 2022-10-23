package org.example.channelReader;

import org.example.Client.SocksClient;
import org.example.Client.SocksClientState;
import org.example.chanelJoin.ChannelJoin;
import org.example.chanelJoin.ChannelRole;
import org.example.connectionMsg.AddressType;
import org.example.connectionMsg.ConnectionMsg;
import org.example.connectionMsg.RequestCode;
import org.example.connectionMsg.ResponseCode;
import org.example.dnsResolver.DnsResolver;
import org.example.greetingMessage.AuthMethod;
import org.example.greetingMessage.GreetingMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import static java.nio.channels.SelectionKey.*;
import static org.example.Client.SocksClientState.*;

public class ChannelReader {
    private final byte SOCKS_VERSION;
    private final int proxyPort;
    private final DnsResolver dnsResolver;

    public ChannelReader(byte socks_version, int proxyPort, DnsResolver dnsResolver) {
        SOCKS_VERSION = socks_version;
        this.proxyPort = proxyPort;
        this.dnsResolver = dnsResolver;
    }

    public void read(SelectionKey selectionKey) throws IOException {
        // Достаём сокет
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        // Достаём клиента
        ChannelJoin socketChannelAtt = (ChannelJoin) selectionKey.attachment();
        SocksClient socksClient = socketChannelAtt.getSocksClient();
        if (socketChannelAtt.isClient()) {  // читаем данные клиента
            readFromClient(socketChannel, socksClient, selectionKey);
        } else if (socketChannelAtt.isDestination()) {
            readFromDestination(socketChannel, socksClient, selectionKey);
        } else if(socketChannelAtt.isDNS()){
            dnsResolver.readDNSMessage(selectionKey);
        }
    }


    // выглядит не дописанным
    private void readFromDestination(SocketChannel socketChannel, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        if (socksClient.getSocksClientState() == SocksClientState.CLOSED) {
            socksClient.closeDestSide();
            return;
        }

        try {
            long bytesCount = socketChannel.read(socksClient.getDestToClientBuffer());
            if (bytesCount == -1) {
                socksClient.closeDestSide();
                socksClient.getClientSelectionKey().interestOps(socksClient.getClientSelectionKey().interestOps() & ~OP_READ);
                return;
            }
            if (socksClient.getDestToClientBuffer().remaining() == 0) {
                selectionKey.interestOps(selectionKey.interestOps() & ~OP_READ);
            }
            if (bytesCount > 0) {
                socksClient.getClientSelectionKey().interestOps(
                        socksClient.getClientSelectionKey().interestOps() | OP_WRITE);
            }
        } catch (IOException ignored) {
        }
    }

    private void readFromClient(SocketChannel socketChannel, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        if (socksClient.getSocksClientState() == SocksClientState.CLOSED) {
            socksClient.closeClientSide();  // клиент сдох
            return;
        }
        try {
            long bytesCount = socketChannel.read(socksClient.getClientToDestBuffer());
            if (bytesCount == -1) {
                socksClient.closeClientSide();  // клиент сдох
                if (socksClient.getDestSelectionKey() != null) {    // Убираем ключ чтения
                    socksClient.getDestSelectionKey().interestOps(socksClient.getDestSelectionKey().interestOps() & ~OP_READ);
                }
                return;
            }
            // -1 будет только, если закрыть сокет, но зачем читать, если ничего нет
            processReadState(bytesCount > 0, socketChannel, socksClient, selectionKey);

        } catch (IOException ignored) {}
    }

    private void processReadState(boolean haveData, SocketChannel socketChannel, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        SocksClientState state = socksClient.getSocksClientState();
        // у нас не просто подключение, а сначала адская пересылка сообщений, а потом подключение
        if (state == RECV_INIT_GREETING) {      // Приветствие клиента (начало обмена сообщениями для подключения)
            greet(socketChannel, socksClient);
        } else if (state == RECV_CONN_REQ) {    // Запрос на подключение клиента (посылка после авторизации)
            processConnectionRequest(socketChannel, socksClient, selectionKey);
        } else if (state == ACTIVE) {   // клиент подключён
            if (socksClient.getClientToDestBuffer().remaining() == 0) {
                selectionKey.interestOps(selectionKey.interestOps() & ~OP_READ);
            }
            if (haveData) {
                socksClient.getDestSelectionKey().interestOps(
                        socksClient.getDestSelectionKey().interestOps() | OP_WRITE);
            }
        }
    }

    private void greet(SocketChannel socketChannel, SocksClient socksClient) throws IOException {
        try {
            GreetingMessage greeting = socksClient.getClientGreeting();

            if (greeting.getSocksVersion() != SOCKS_VERSION) {
                socksClient.closeClientSide();
                return;
            }

            GreetingMessage response;
            if (greeting.hasAuthMethod(AuthMethod.NO_AUTHENTICATION)) {
                response = new GreetingMessage(SOCKS_VERSION, AuthMethod.NO_AUTHENTICATION);
            } else {
                response = new GreetingMessage(SOCKS_VERSION, AuthMethod.NO_ACCEPTABLE_METHOD);
                socksClient.setCloseUponSending(true);
            }

            socksClient.getDestToClientBuffer().put(response.toByteResponse());
            socksClient.getClientSelectionKey().interestOps(OP_WRITE);
            socksClient.setSocksClientState(SocksClientState.SEND_GREETING_RESP);

        } catch (IllegalArgumentException iae) {
            socksClient.closeClientSide();
        }
    }

    private void processConnectionRequest(SocketChannel socketChannel, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        try {
            ConnectionMsg request = socksClient.getClientConnectionRequest();
            if (request.getSocksVersion() != SOCKS_VERSION) {
                System.out.println("Unsupported version of socks protocol from client " + socketChannel.getRemoteAddress());
                socksClient.closeClientSide();
                return;
            }

            if (request.getRequestCommand() == RequestCode.ESTABLISH_STREAM_CONNECTION) {
                createConnection(request, socksClient, selectionKey);
            } else {

                sendResponse(socksClient,
                        new ConnectionMsg(SOCKS_VERSION, AddressType.IPV4_ADDRESS, InetAddress.getLocalHost(), proxyPort),
                        ResponseCode.CMD_NOT_SUPPORTED);
            }
        } catch (IllegalArgumentException iae) {
            socksClient.closeClientSide();
        }
    }

    private void createConnection(ConnectionMsg request, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        InetAddress address;

        if (request.getAddressType() == AddressType.DOMAIN_NAME) {
            address = dnsResolver.resolve(request.getDomain());     // ищем адрес через левую штуку
            if(address == null){
                dnsResolver.makeDNSRequest(request.getDomain(),
                        selectionKey);                              // sendHostUnreachable?
                return;
            }
        } else {
            address = request.getAddress();
        }

        InetSocketAddress inetSocketAddress = new InetSocketAddress(address, request.getPort());
        connect(socksClient, inetSocketAddress);
    }

    private void sendHostUnreachable(SocksClient socksClient) throws UnknownHostException {
        sendResponse(socksClient,
                new ConnectionMsg(SOCKS_VERSION, AddressType.IPV4_ADDRESS, InetAddress.getLocalHost(), proxyPort),
                ResponseCode.HOST_UNREACHABLE);
    }

    public static void connect(SocksClient socksClient, InetSocketAddress inetSocketAddress) throws IOException {
        socksClient.getClientSelectionKey().interestOps(0);
        socksClient.setSocksClientState(SocksClientState.CONNECTING_TO_DEST);

        socksClient.setDestAddress(inetSocketAddress);

        SocketChannel destSocketChannel = SocketChannel.open();
        destSocketChannel.configureBlocking(false);
        destSocketChannel.connect(inetSocketAddress);

        SelectionKey destSelectionKey = destSocketChannel.register(socksClient.getClientSelectionKey().selector(), OP_CONNECT);
        destSelectionKey.attach(new ChannelJoin(socksClient, ChannelRole.DESTINATION));

        socksClient.setDestSelectionKey(destSelectionKey);
        socksClient.setDestSocketChannel(destSocketChannel);

    }

    private void sendResponse(SocksClient socksClient, ConnectionMsg response, ResponseCode code) {
        socksClient.setCloseUponSending(true);
        socksClient.getClientSelectionKey().interestOps(OP_WRITE);
        socksClient.setSocksClientState(SocksClientState.SEND_CONN_RESP);
        socksClient.getDestToClientBuffer().put(response.getResponseBytes(code));
    }

}
