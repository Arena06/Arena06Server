package com.assemblr.arena06.server;

import com.assemblr.arena06.common.data.Player;
import com.assemblr.arena06.common.data.Sprite;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
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
            processPacket(packet);
        }
    }
    
    private void processPacket(Map<String, Object> packet) {
        int clientId = (Integer) packet.get("client-id");
        String type = (String) packet.get("type");
        if (type == null) return;
        
        if (type.equals("handshake")) {
            server.sendData(clientId, ImmutableMap.<String, Object>of(
                "type", "handshake"
            ));
        } else if (type.equals("keep-alive")) {
        } else if (type.equals("login")) {
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
            System.out.println("player " + player.getName() + " logged in (" + server.getClientAddress(clientId) + ")");
        } else if (type.equals("logout")) {
            server.removeClient(clientId);
            Integer id = clients.remove(clientId);
            if (id == null) return;
            Player player = (Player) sprites.remove(id);
            if (player == null) return;
            System.out.println("player " + player.getName() + " disconnected");
            server.sendBroadcast(ImmutableMap.<String, Object>of(
                "type", "sprite",
                "action", "remove",
                "id", id
            ));
        } else if (type.equals("request")) {
            String request = (String) packet.get("request");
            if (request == null) return;
            if (request.equals("sprite-list")) {
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
        } else if (type.equals("sprite")) {
            String action = (String) packet.get("action");
            if (action.equals("update")) {
                Sprite s = sprites.get((Integer) packet.get("id"));
                if (s == null) return;
                s.updateState((Map<String, Object>) packet.get("data"));
                server.sendBroadcast(ImmutableMap.<String, Object>of(
                    "type", "sprite",
                    "action", "update",
                    "id", packet.get("id"),
                    "data", packet.get("data")
                ), clientId);
            }
        } else if (type.equals("chat")) {
            Integer playerId = clients.get(clientId);
            if (playerId == null) return;
            Player player = (Player) sprites.get(playerId);
            if (player == null) return;
            String message = (String) packet.get("message");
            if (message.startsWith("/")) {
                String[] split = message.substring(1).split(" ");
                String[] args = Arrays.copyOfRange(split, 1, split.length);
                handleCommand(split[0], args, clientId, player);
            } else {
                server.sendBroadcast(ImmutableMap.<String, Object>of(
                    "type", "chat",
                    "timestamp", System.currentTimeMillis(),
                    "content", "[" + player.getName() + "]  " + message
                ));
            }
        }
    }
    
    private void handleCommand(String command, String[] args, int clientId, Player sender) {
        if (command.equalsIgnoreCase("list")) {
            StringBuilder message = new StringBuilder();
            for (int i : clients.values()) {
                Player player = (Player) sprites.get(i);
                message.append("- ").append(player.getName()).append("\n");
            }
            server.sendData(clientId, ImmutableMap.<String, Object>of(
                "type", "chat",
                "timestamp", System.currentTimeMillis(),
                "content", message.toString()
            ));
        } else if (command.equalsIgnoreCase("map")) {
            if (args.length == 0) return;
            String subcommand = args[0];
            if (subcommand.equalsIgnoreCase("regen")) {
                mapSeed = System.currentTimeMillis();
                server.sendBroadcast(ImmutableMap.<String, Object>of(
                    "type", "map",
                    "action", "load",
                    "seed", mapSeed
                ));
            }
        } else if (command.equalsIgnoreCase("me")) {
            server.sendBroadcast(ImmutableMap.<String, Object>of(
                "type", "chat",
                "timestamp", System.currentTimeMillis(),
                "content", "* " + sender.getName() + " " + Joiner.on(" ").skipNulls().join(args)
            ));
        }
    }
    
    private int addSprite(Sprite s) {
        sprites.put(nextSprite, s);
        nextSprite++;
        return nextSprite - 1;
    }
    
}
