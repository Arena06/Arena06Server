package com.assemblr.arena06.server;

import com.assemblr.arena06.common.data.Player;
import com.assemblr.arena06.common.data.Sprite;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

public class ServerMain {
    
    private final PacketServer server;
    
    private final Thread runner;
    private boolean running = false;
    
    private long mapSeed = System.currentTimeMillis();
    
    private int nextSprite = 1;
    private Map<Integer, Integer> clients = new HashMap<Integer, Integer>();
    private Map<Integer, Sprite> sprites = new HashMap<Integer, Sprite>();
    
    public static void main(String[] args) throws Exception {
        int port = 30155;
        
        for (String arg : args) {
            String[] flag = arg.split("=", 2);
            if (flag.length != 2) continue;
            if (flag[0].equalsIgnoreCase("port")) {
                port = Integer.parseInt(flag[1]);
            }
        }
        
        ServerMain main = new ServerMain(port);
        main.start();
    }
    
    public ServerMain(int port) {
        server = new PacketServer(port);
        
        runner = new Thread(new Runnable() {
            public void run() {
                long lastUpdate = System.currentTimeMillis();
                while (running) {
                    long elapsed = System.currentTimeMillis() - lastUpdate;
                    if (elapsed < 16) { // 60 FPS
                        try {
                            Thread.sleep(16 - elapsed);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                    
                    long now = System.currentTimeMillis();
                    update((now - lastUpdate) / 1000.0);
                    lastUpdate = now;
                }
            }
        });
    }
    
    public void start() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    server.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
        running = true;
        runner.start();
    }
    
    public void stop() {
        running = false;
    }
    
    public void update(double delta) {
        Map<String, Object> packet;
        while ((packet = server.getIncomingPackets().poll()) != null) {
            int clientId = (Integer) packet.get("client-id");
            if (packet.get("type").equals("login")) {
                Player player = new Player(null);
                player.updateState((Map<String, Object>) packet.get("data"));
                int id = addSprite(player);
                clients.put(clientId, id);
                server.sendData(clientId, ImmutableMap.<String, Object>of(
                    "type", "login",
                    "id", id,
                    "data", player.serializeState(),
                    "map-seed", mapSeed
                ));
                server.sendBroadcast(ImmutableMap.<String, Object>of(
                    "type", "sprite",
                    "action", "create",
                    "id", id,
                    "data", ImmutableList.<Object>of(Player.class.getName(), player.serializeState())
                ), clientId);
            } else if (packet.get("type").equals("logout")) {
                server.removeClient(clientId);
                int id = clients.remove(clientId);
                server.sendBroadcast(ImmutableMap.<String, Object>of(
                    "type", "sprite",
                    "action", "remove",
                    "id", id
                ));
            } else if (packet.get("type").equals("request")) {
                if (packet.get("request").equals("sprite-list")) {
                    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builder();
                    for (Map.Entry<Integer, Sprite> entry : sprites.entrySet()) {
                        data.put(entry.getKey().toString(), ImmutableList.<Object>of(
                                 entry.getValue().getClass().getName(), entry.getValue().serializeState()));
                    }
                    server.sendData(clientId, ImmutableMap.<String, Object>of(
                        "type", "request",
                        "request", "sprite-list",
                        "data", data.build()
                    ));
                }
            } else if (packet.get("type").equals("sprite")) {
                if (packet.get("action").equals("update")) {
                    sprites.get((Integer) packet.get("id")).updateState((Map<String, Object>) packet.get("data"));
                    server.sendBroadcast(ImmutableMap.<String, Object>of(
                        "type", "sprite",
                        "action", "update",
                        "id", packet.get("id"),
                        "data", packet.get("data")
                    ), clientId);
                }
            }
        }
    }
    
    private int addSprite(Sprite s) {
        sprites.put(nextSprite, s);
        nextSprite++;
        return nextSprite - 1;
    }
    
}
