package com.assemblr.arena06.server;

import com.assemblr.arena06.common.net.AddressedData;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;

public class PacketServerHandler extends SimpleChannelInboundHandler<AddressedData> {
    
    private int nextClient = 1;
    private final BiMap<Integer, InetSocketAddress> clients;
    private final Queue<Map<String, Object>> output;
    
    public PacketServerHandler(BiMap<Integer, InetSocketAddress> clients, Queue<Map<String, Object>> output) {
        this.clients = clients;
        this.output = output;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AddressedData msg) throws Exception {
        Integer clientId = clients.inverse().get(msg.getSender());
        if (clientId == null) {
            clientId = nextClient;
            clients.put(clientId, msg.getSender());
            nextClient++;
        }
        output.add(ImmutableMap.<String, Object>builder()
                .putAll(msg.getData())
                .put("client-id", clientId)
                .build());
    }
    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }
    
}
