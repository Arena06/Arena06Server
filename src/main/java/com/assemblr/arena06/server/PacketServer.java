package com.assemblr.arena06.server;

import com.assemblr.arena06.common.net.DataDecoder;
import com.assemblr.arena06.common.net.DataEncoder;
import com.assemblr.arena06.common.net.PacketDecoder;
import com.assemblr.arena06.common.net.PacketEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketServer {
    
    public static void main(String[] args) throws Exception {
        PacketServer server = new PacketServer(30155);
        server.run();
    }
    
    private final int port;
    
    private final Queue<Map<String, Object>> incomingPackets = new ConcurrentLinkedQueue<Map<String, Object>>();
    
    public PacketServer(int port) {
        this.port = port;
    }
    
    public void run() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioDatagramChannel.class)
             .option(ChannelOption.SO_BROADCAST, true)
             .handler(new ChannelInitializer<DatagramChannel>() {
                @Override
                protected void initChannel(DatagramChannel c) throws Exception {
                    c.pipeline().addLast(
                            new PacketEncoder(), new DataEncoder(),
                            new PacketDecoder(), new DataDecoder(),
                            new PacketServerHandler(getIncomingPackets()));
                }
            });
            b.bind(port).sync().channel().closeFuture().await();
        } finally {
            group.shutdownGracefully();
        }
    }
    
    public Queue<Map<String, Object>> getIncomingPackets() {
        return incomingPackets;
    }
    
}
