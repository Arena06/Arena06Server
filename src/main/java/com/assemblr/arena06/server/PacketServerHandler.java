package com.assemblr.arena06.server;

import com.assemblr.arena06.common.net.AddressedData;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.Map;
import java.util.Queue;

public class PacketServerHandler extends SimpleChannelInboundHandler<AddressedData> {
    
    private final Queue<Map<String, Object>> output;
    
    public PacketServerHandler(Queue<Map<String, Object>> output) {
        this.output = output;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AddressedData msg) throws Exception {
        System.out.println("Data recieved: " + msg.getData());
        Map<String, Object> response = ImmutableMap.<String, Object>of("status", "success");
        ctx.write(new AddressedData(response, null, msg.getSender()));
        output.add(msg.getData());
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
