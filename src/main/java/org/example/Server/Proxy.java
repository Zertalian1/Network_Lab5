package org.example.Server;

import org.example.Client.SocksClient;
import org.example.Client.SocksClientState;
import org.example.chanelJoin.ChannelJoin;
import org.example.chanelJoin.ChannelRole;
import org.example.channelReader.ChannelReader;
import org.example.channelWriter.ChannelWriter;
import org.example.connectionMsg.AddressType;
import org.example.connectionMsg.ConnectionMsg;
import org.example.connectionMsg.ResponseCode;
import org.example.dnsResolver.DnsResolver;

import org.xbill.DNS.SimpleResolver;

import java.net.UnknownHostException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import static java.nio.channels.SelectionKey.*;

public class Proxy implements Runnable{
    private static final String GOOGLE_DNS_ADDR = "8.8.8.8";
    private static final int GOOGLE_DNS_PORT = 53;
    private static final byte SOCKS_VERSION = 0x05;
    private static final int BACKLOG = 10;


    ChannelReader reader;
    ChannelWriter writer;
    private final int proxyPort; //порт, на котором прокси будет ждать входящих подключений от клиентов
    private DnsResolver dnsResolver;
    SimpleResolver simpleResolver;

    public Proxy(int port) {
        try {
            // сказано идти к гуглу
            // будет сделано
            simpleResolver = new SimpleResolver(GOOGLE_DNS_ADDR);
            simpleResolver.setPort(GOOGLE_DNS_PORT);
            this.proxyPort = port;
        } catch (UnknownHostException e) {
            throw new ExceptionInInitializerError();
        }
    }


    //https://habr.com/ru/post/70690/
    @Override
    public void run() {
        try (ServerSocketChannel serverSocket = ServerSocketChannel.open();
             Selector selector = Selector.open()) {
            dnsResolver = new DnsResolver(selector, new InetSocketAddress(GOOGLE_DNS_ADDR, GOOGLE_DNS_PORT));
            // Вешаемся на порт
            serverSocket.bind(new InetSocketAddress(proxyPort), BACKLOG);
            // Убираем блокировку
            serverSocket.configureBlocking(false);
            System.out.println(serverSocket.getLocalAddress().toString());
            // Регистрация в селекторе
            serverSocket.register(selector, OP_ACCEPT);
            reader = new ChannelReader(SOCKS_VERSION, proxyPort, dnsResolver);
            writer = new ChannelWriter(dnsResolver);
            while ( true) {
                selector.select();
                processSelectedKeys(selector);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    private void processSelectedKeys(Selector selector) throws IOException {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> iter = selectedKeys.iterator();
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            if (key.isValid() && key.isAcceptable()) {  // Принимаем соединение
               // System.out.println("accept");
                accept((ServerSocketChannel) key.channel(), key.selector());
            }
            if (key.isValid() && key.isConnectable()) { // Устанавливаем соединение
                // System.out.println("connecting");
                connect(key);
            }
            if (key.isValid() && key.isReadable()) {    // Читаем данные
              //  System.out.println("reading");
                reader.read(key);

            }
            if (key.isValid() && key.isWritable()) {    // Пишем Данные
              //  System.out.println("writing");
                writer.write(key);
            }
            iter.remove();
        }
    }

    private void accept(ServerSocketChannel serverSocketChannel, Selector selector) throws IOException {
        // Приняли
        SocketChannel clientSocketChannel = serverSocketChannel.accept();
        /*if(clientSocketChannel == null){
            System.out.println("clientSocketChannel is null fk");
            return;
        }*/
        // Неблокирующий
        clientSocketChannel.configureBlocking(false);
        // Регистрируем в селекторе
        SelectionKey clientSelectionKey = clientSocketChannel.register(selector, OP_READ);

        // К ключу можно прикрепить любой объект, чтобы в дальнейшем его отслеживать
        // В дальнейшем из ключа можно получить прикрепленный объект
        SocksClient socksClient = new SocksClient(clientSocketChannel, clientSelectionKey);
        clientSelectionKey.attach(new ChannelJoin(socksClient, ChannelRole.CLIENT));

    }

    private void connect(SelectionKey key) throws IOException {
        // Достаём сокет клиента
        SocketChannel destSocketChannel = (SocketChannel) key.channel();
        // Достаём клиента
        ChannelJoin socketChannelRef = (ChannelJoin) key.attachment();
        SocksClient socksClient = socketChannelRef.getSocksClient();

        ConnectionMsg connection = new ConnectionMsg(SOCKS_VERSION,
                AddressType.IPV4_ADDRESS,
                socksClient.getDestAddress().getAddress(),
                socksClient.getDestAddress().getPort());

        try {
            if (!destSocketChannel.finishConnect()) {
                throw new RuntimeException();
            }
            key.interestOps(0);
            socksClient.getClientSelectionKey().interestOps(OP_WRITE);
            socksClient.setSocksClientState(SocksClientState.SEND_CONN_RESP);
            System.out.println("SEND_CONN_RESP ");
            socksClient.getDestToClientBuffer().put(connection.getResponseBytes(ResponseCode.REQUEST_GRANTED));
        } catch (IOException e) {
            socksClient.getDestToClientBuffer().put(connection.getResponseBytes(ResponseCode.HOST_UNREACHABLE));
            socksClient.setCloseUponSending(true);
            socksClient.getClientSelectionKey().interestOps(OP_WRITE);
            socksClient.setSocksClientState(SocksClientState.SEND_CONN_RESP);
        }
    }
}
