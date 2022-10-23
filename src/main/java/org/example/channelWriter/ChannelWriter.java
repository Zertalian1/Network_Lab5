package org.example.channelWriter;

import org.example.Client.SocksClient;
import org.example.Client.SocksClientState;
import org.example.chanelJoin.ChannelJoin;
import org.example.dnsResolver.DnsResolver;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import static java.nio.channels.SelectionKey.*;

public class ChannelWriter {

    private final DnsResolver dnsResolver;

    public ChannelWriter(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }


    public void write(SelectionKey selectionKey) throws IOException {

        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        ChannelJoin channelJoin = (ChannelJoin) selectionKey.attachment();
        SocksClient socksClient = channelJoin.getSocksClient();

        if (channelJoin.isClient()) {
            writeToClient(socketChannel, socksClient, selectionKey);
        } else if (channelJoin.isDestination()) {
            writeToDestination(socksClient, socketChannel, selectionKey);
        } else if(channelJoin.isDNS()){
            dnsResolver.sendDNSRequest(selectionKey);
        }
    }

    private void writeToClient(SocketChannel socketChannel, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        socksClient.getDestToClientBuffer().flip();
        long bytesCount = socketChannel.write(socksClient.getDestToClientBuffer());

        SocksClientState state = socksClient.getSocksClientState();
        switch (state) {
            case SEND_GREETING_RESP, SEND_CONN_RESP -> {
                if (socksClient.isCloseUponSending()) {
                    socksClient.closeClientSide();
                    break;
                }
                processClientResponseState(socksClient, state);
            }
            case ACTIVE -> processClientActiveState(bytesCount > 0, socksClient, selectionKey);
            case CLOSED -> {
                if (socksClient.getDestToClientBuffer().remaining() == 0) {
                    socksClient.closeClientSide();
                }
            }
            default -> throw new IllegalArgumentException();
        }

        socksClient.getDestToClientBuffer().compact();
    }

    private void processClientResponseState(SocksClient socksClient, SocksClientState state) {
        if (socksClient.getDestToClientBuffer().remaining() == 0) {
            if (state == SocksClientState.SEND_CONN_RESP) {
                socksClient.setSocksClientState(SocksClientState.ACTIVE);
                socksClient.getDestSelectionKey().interestOps(OP_READ);
            } else {
                socksClient.setSocksClientState(SocksClientState.RECV_CONN_REQ);
            }
            socksClient.getClientSelectionKey().interestOps(OP_READ);
        }
    }

    private void processClientActiveState(boolean isSmtWrote, SocksClient socksClient, SelectionKey selectionKey) throws IOException {
        if (socksClient.getDestToClientBuffer().remaining() == 0) {
            selectionKey.interestOps(selectionKey.interestOps() & ~OP_WRITE);

            if (socksClient.getSocksClientState() == SocksClientState.CLOSED) {
                socksClient.closeDestSide();
                return;
            }
        }
        if (isSmtWrote) {
            socksClient.getDestSelectionKey().interestOps(
                    socksClient.getDestSelectionKey().interestOps() | OP_READ);
        }
    }

    private void writeToDestination(SocksClient socksClient, SocketChannel socketChannel, SelectionKey selectionKey) throws IOException {
        socksClient.getClientToDestBuffer().flip();
        long bytesCount = socketChannel.write(socksClient.getClientToDestBuffer());

        if (socksClient.getClientToDestBuffer().remaining() == 0) {
            selectionKey.interestOps(selectionKey.interestOps() & ~OP_WRITE);

            if (socksClient.getSocksClientState() == SocksClientState.CLOSED) {
                socksClient.closeDestSide();
                return;
            }
        }
        if (bytesCount > 0) {
            socksClient.getClientSelectionKey().interestOps(
                    socksClient.getClientSelectionKey().interestOps() | OP_READ);
        }
        socksClient.getClientToDestBuffer().compact();
    }
}