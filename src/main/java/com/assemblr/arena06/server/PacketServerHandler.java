package com.assemblr.arena06.server;

import com.assemblr.arena06.common.packet.Packet;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.Map;
import java.util.Queue;

public class PacketServerHandler extends SimpleChannelInboundHandler<Packet> {
    
    private final Object clientLock;
    private final BiMap<Integer, Channel> clients;
    private final Queue<Map<String, Object>> output;
    
    public PacketServerHandler(Object clientLock, BiMap<Integer, Channel> clients, Queue<Map<String, Object>> output) {
        this.clientLock = clientLock;
        this.clients = clients;
        this.output = output;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) throws Exception {
        Integer clientId = clients.inverse().get(ctx.channel());
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
