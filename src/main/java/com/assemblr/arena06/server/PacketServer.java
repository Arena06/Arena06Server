package com.assemblr.arena06.server;

import com.assemblr.arena06.common.chat.ChatBroadcaster;
import com.assemblr.arena06.common.net.AddressedData;
import com.assemblr.arena06.common.net.ChildChannelResolver;
import com.assemblr.arena06.common.net.ChildChannelTablePopulator;
import com.assemblr.arena06.common.net.DataDecoder;
import com.assemblr.arena06.common.net.DataEncoder;
import com.assemblr.arena06.common.net.PacketDecoder;
import com.assemblr.arena06.common.net.PacketEncoder;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketServer implements ChatBroadcaster {
    
    public static void main(String[] args) throws Exception {
        PacketServer server = new PacketServer(30155);
        server.run();
    }
    
    private final int port;
    private Channel channel;
    
    private final Object clientLock = new Object();
    private final BiMap<Integer, InetSocketAddress> clients = HashBiMap.<Integer, InetSocketAddress>create();
    private final Queue<Map<String, Object>> incomingPackets = new ConcurrentLinkedQueue<Map<String, Object>>();
    
    public PacketServer(int port) {
        this.port = port;
    }
    
    public void run() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            final Map<InetSocketAddress, Channel> resolutionTable = new HashMap<InetSocketAddress, Channel>();
            b.group(group)
             .channel(NioServerSocketChannel.class)
             .handler(new ChannelInitializer<NioServerSocketChannel>() {
                @Override
                protected void initChannel(NioServerSocketChannel c) throws Exception {
                    c.pipeline().addLast(new ChildChannelResolver(resolutionTable));
                }
             })
             .option(ChannelOption.SO_BROADCAST, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel c) throws Exception {
                    c.pipeline().addLast(
                            new LengthFieldPrepender(2),                          new PacketEncoder(), new DataEncoder(),
                            new LengthFieldBasedFrameDecoder(0xFFFF, 0, 2, 0, 2), new PacketDecoder(), new DataDecoder(),
                            new ChildChannelTablePopulator(resolutionTable),
                            new PacketServerHandler(clientLock, clients, getIncomingPackets()));
                }
            });
            channel = b.bind(port).sync().channel();
            channel.closeFuture().await();
        } finally {
            group.shutdownGracefully();
        }
    }
    
    public Queue<Map<String, Object>> getIncomingPackets() {
        return incomingPackets;
    }
    
    public InetSocketAddress getClientAddress(int clientId) {
        return clients.get(clientId);
    }
    
    public void removeClient(int clientId) {
        synchronized (clientLock) {
            clients.remove(clientId);
        }
    }
    
    public void sendBroadcast(Map<String, Object> data) {
        sendBroadcast(data, Collections.<Integer>emptySet());
    }
    
    public void sendBroadcast(Map<String, Object> data, int exclude) {
        sendBroadcast(data, ImmutableSet.<Integer>of(exclude));
    }
    
    public void sendBroadcast(Map<String, Object> data, Set<Integer> exclude) {
        while (channel == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
        for (BiMap.Entry<Integer, InetSocketAddress> entry : clients.entrySet()) {
            if (exclude.contains(entry.getKey())) continue;
            channel.write(new AddressedData(data, null, entry.getValue()));
        }
        channel.flush();
    }
    
    public void sendData(int clientId, Map<String, Object> data) {
        while (channel == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
        InetSocketAddress client = clients.get(clientId);
        if (client != null) {
            System.out.println("channel writeable: " + channel.isWritable());
            channel.writeAndFlush(new AddressedData(data, null, client));
        }
    }
    
    public void sendChat(int clientId, String message) {
        sendData(clientId, ImmutableMap.<String, Object>of(
            "type", "chat",
            "timestamp", System.currentTimeMillis(),
            "content", message
        ));
    }
    
    @Override
    public void sendChatBroadcast(String message) {
        sendBroadcast(ImmutableMap.<String, Object>of(
            "type", "chat",
            "timestamp", System.currentTimeMillis(),
            "content", message
        ));
    }
    
    public void sendChatBroadcast(String message, int exclude) {
        sendChatBroadcast(message, ImmutableSet.<Integer>of(exclude));
    }
    
    public void sendChatBroadcast(String message, Set<Integer> exclude) {
        sendBroadcast(ImmutableMap.<String, Object>of(
            "type", "chat",
            "timestamp", System.currentTimeMillis(),
            "content", message
        ), exclude);
    }
    
}
