package org.example.dnsResolver;

import org.example.chanelJoin.ChannelJoin;
import org.example.chanelJoin.ChannelRole;
import org.example.channelReader.ChannelReader;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DnsResolver {
    private final static int MAX_MESSAGE_LENGTH = 512;

    private final Map<Integer, SelectionKey> domainNameClientMap = new HashMap<>();

    private final InetSocketAddress address;
    private final DatagramChannel datagramChannel;
    private final SelectionKey dnsKey;

    ByteBuffer outputBuffer = null;
    private int messageID = 0;

    private final Resolver resolver;

    public DnsResolver(Selector selector, InetSocketAddress address) throws IOException {
        this.address = address;
        datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        dnsKey = datagramChannel.register(selector, SelectionKey.OP_READ, new ChannelJoin(null, ChannelRole.DNS));

        SimpleResolver simpleResolver = new SimpleResolver(address.getAddress());
        simpleResolver.setPort(address.getPort());
        resolver = simpleResolver;
    }

    private byte[] makeDNSMessage(String name) throws TextParseException {
        Message dnsMessage = new Message(++messageID);
        Header header = dnsMessage.getHeader();
        header.setOpcode(Opcode.QUERY);
        header.setFlag(Flags.RD);
        dnsMessage.addRecord(Record.newRecord(new Name(name), Type.A, DClass.IN), Section.QUESTION);
        return dnsMessage.toWire(MAX_MESSAGE_LENGTH);
    }

    public void makeDNSRequest(String domain, SelectionKey key) throws TextParseException {
        byte[] dnsMessage = makeDNSMessage(domain + ".");
        domainNameClientMap.put(messageID, key);

        if (outputBuffer != null) {
            outputBuffer = ByteBuffer.allocate(outputBuffer.capacity() + dnsMessage.length).put(outputBuffer).put(dnsMessage);
        } else {
            outputBuffer = ByteBuffer.wrap(dnsMessage);
        }
        dnsKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    public InetAddress resolve(String address) {
        try {
            Lookup lookup = new Lookup(address, Type.A);    // A = Address
            lookup.setResolver(resolver);

            Record[] result = lookup.run();
            if (result.length > 0) {
                return ((ARecord) result[0]).getAddress();
            } else {
                return null;
            }
        } catch (TextParseException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    public void sendDNSRequest(SelectionKey key) throws IOException {
        datagramChannel.send(outputBuffer, address);
        outputBuffer.compact();

        if (outputBuffer.position() == 0) {
            outputBuffer = null;
            key.interestOps(SelectionKey.OP_READ);
        }                                                                                                            
    }

    public void readDNSMessage(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();

        ByteBuffer inputBuffer = ByteBuffer.allocate(MAX_MESSAGE_LENGTH);
        channel.receive(inputBuffer);

        Message dnsMessage = new Message(inputBuffer);                           
        int id = dnsMessage.getHeader().getID();

        List<Record> answers = dnsMessage.getSection(Section.ANSWER);

        ARecord answer = null;
        for (Record record : answers) {
            if (record.getType() == Type.A) {
                answer = (ARecord) record;
                break;
            }
        }

        assert answer != null;
        ChannelReader.connect(((ChannelJoin) key.attachment()).getSocksClient(),
                new InetSocketAddress(answer.getAddress(), address.getPort()));
    }
}
