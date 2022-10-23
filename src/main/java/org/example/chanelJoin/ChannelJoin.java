package org.example.chanelJoin;

import org.example.Client.SocksClient;

public class ChannelJoin {
    private final ChannelRole socketChannelRole;
    private final SocksClient socksClient;

    public ChannelJoin(SocksClient socksClient, ChannelRole socketChannelSide) {
        this.socksClient = socksClient;
        this.socketChannelRole = socketChannelSide;
    }

    public SocksClient getSocksClient() {
        return socksClient;
    }

    public boolean isClient(){
        return socketChannelRole == ChannelRole.CLIENT;
    }

    public boolean isDestination(){
        return socketChannelRole == ChannelRole.DESTINATION;
    }

    public boolean isDNS(){
        return socketChannelRole == ChannelRole.DNS;
    }
}
