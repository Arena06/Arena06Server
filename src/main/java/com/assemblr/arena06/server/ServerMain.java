package com.assemblr.arena06.server;

import com.assemblr.arena06.common.data.Player;
import com.assemblr.arena06.common.data.Sprite;
import com.assemblr.arena06.common.data.UpdateableSprite;
import com.assemblr.arena06.server.spritemanager.SpriteUpdater;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerMain {
    
    private final PacketServer server;
    
    private final Thread runner;
    private boolean running = false;
    
    private boolean inRound = false;
    private double nextMatchCountDown = -1;
    private boolean regenMapNextRound = false;
    
    private final double timeBetweenFullUpdates = 100;
    private double timeElapsedBetweenFullUpdates;
    
    private Map<Player, Integer> winners = new HashMap<Player, Integer>();
    
    private int nextSprite = 1;
    private Map<Integer, Integer> clients = new HashMap<Integer, Integer>();
    private SpriteUpdater spriteUpdater;
    
    private BiMap<Integer, Player> players = HashBiMap.create();
    private List<Integer> livingPlayers = new ArrayList<Integer>();
    
    public static void main(String[] args) throws Exception {
        int port = 30155;
        
        for (String arg : args) {
            String[] flag = arg.split("=", 2);
            if (flag.length != 2) continue;
            if (flag[0].equalsIgnoreCase("port")) {
                port = Integer.parseInt(flag[1]);
            }
        }
        System.out.println("Starting server on port: " + port);
        ServerMain main = new ServerMain(port);
        main.start();
    }
    
    public ServerMain(int port) {
        server = new PacketServer(port);
        spriteUpdater = new SpriteUpdater(server, this, System.currentTimeMillis());
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
        timeElapsedBetweenFullUpdates += delta;
        if (timeElapsedBetweenFullUpdates >= timeBetweenFullUpdates) {
            broadcastSpriteList();
            timeElapsedBetweenFullUpdates = 0;
        }
        Map<String, Object> packet;
        while ((packet = server.getIncomingPackets().poll()) != null) {
            processPacket(packet);
        }
        spriteUpdater.updateAllSprites(delta);
        if (nextMatchCountDown > 0) {
            nextMatchCountDown -= delta;
            if (nextMatchCountDown <= 0) {
                startNewRound();
                nextMatchCountDown = -1;
            }
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
        } else if (type.equals("login")) {
            Player player = new Player();
            player.updateState((Map<String, Object>) packet.get("data"));
            player.setAlive(false);
            int id = addSprite(player);
            
            clients.put(clientId, id);
            players.put(id, player);
            if (!inRound) {
                
                if (players.size() == 2) {
                    nextMatchCountDown = 5;
                }
            }
            server.sendData(clientId, ImmutableMap.<String, Object>of(
                "type", "login",
                "id", id,
                "data", player.serializeState(),
                "map-seed", spriteUpdater.getMapSeed()
            ));
            server.sendBroadcast(ImmutableMap.<String, Object>of(
                "type", "sprite",
                "action", "create",
                "id", id,
                "data", ImmutableList.<Object>of(Player.class.getName(), player.serializeState())
            ), clientId);
            server.sendChatBroadcast("~ " + player.getName() + " entered the game", id);
            System.out.println("player " + player.getName() + " logged in (" + server.getClientAddress(clientId) + ")");
        } else if (type.equals("logout")) {
            killPlayer(clients.get(clientId));
            server.removeClient(clientId);
            Integer id = clients.remove(clientId);
            if (id == null) return;
            Player player = (Player) getSprites().remove(id);
            if (player == null) return;
            players.remove(id);
            System.out.println("player " + player.getName() + " disconnected");
            server.sendBroadcast(ImmutableMap.<String, Object>of(
                "type", "sprite",
                "action", "remove",
                "id", id
            ));
            server.sendChatBroadcast("~ " + player.getName() + " left the game", id);
            if (clients.size() <= 1) {
                inRound = false;
            }
        } else if (type.equals("request")) {
            String request = (String) packet.get("request");
            if (request == null) return;
            if (request.equals("sprite-list")) {
                sendSpriteList(clientId);
            }
        } else if (type.equals("sprite")) {
            String action = (String) packet.get("action");
            if (action.equals("update")) {
                Sprite s = getSprites().get((Integer) packet.get("id"));
                if (s == null) return;
                s.updateState((Map<String, Object>) packet.get("data"));
                if (s instanceof Player) {
                    Player player = (Player) s;
                    if (player.isClientIsCurrent()) {
                        server.sendBroadcast(ImmutableMap.<String, Object>of(
                                "type", "sprite",
                                "action", "update",
                                "id", packet.get("id"),
                                "data", packet.get("data")
                        ), clientId);
                    }
                } else {
                    server.sendBroadcast(ImmutableMap.<String, Object>of(
                            "type", "sprite",
                            "action", "update",
                            "id", packet.get("id"),
                            "data", packet.get("data")
                    ), clientId);
                }
            } else if (action.equals("create")) {
                    List<Object> spriteData = (List<Object>) packet.get("data");
                    try {
                        Class<? extends Sprite> spriteClass = (Class<? extends Sprite>) Class.forName((String) spriteData.get(0));
                        Sprite sprite = spriteClass.newInstance();
                        sprite.updateState((Map<String, Object>) spriteData.get(1));
                        int id = addSprite(sprite);
                        server.sendBroadcast(ImmutableMap.<String, Object>of(
                                "type", "sprite",
                                "action", "create",
                                "id", id,
                                "data", ImmutableList.<Object>of(spriteClass.getName(), sprite.serializeState(true))
                        ));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    
            } else if (action.equals("validate")) {
                if (getSprites().get((Integer) packet.get("id")) instanceof Player) {
                    ((Player) getSprites().get((Integer) packet.get("id"))).setClientIsCurrent(true);
                }
            }
        } else if (type.equals("chat")) {
            Integer playerId = clients.get(clientId);
            if (playerId == null) return;
            Player player = (Player) getSprites().get(playerId);
            if (player == null) return;
            String message = (String) packet.get("message");
            if (message.startsWith("/")) {
                String[] split = message.substring(1).split(" ");
                String[] args = Arrays.copyOfRange(split, 1, split.length);
                handleCommand(split[0], args, clientId, player);
            } else {
                server.sendChatBroadcast("[" + player.getName() + "]  " + message);
            }
        }
    }
    
    private void handleCommand(String command, String[] args, int clientId, Player sender) {
        if (command.equalsIgnoreCase("list")) {
            StringBuilder message = new StringBuilder();
            for (int i : clients.values()) {
                Player player = (Player) getSprites().get(i);
                message.append("- ").append(player.getName()).append("\n");
            }
            server.sendChat(clientId, message.toString());
        } else if (command.equalsIgnoreCase("map")) {
            if (args.length == 0) return;
            String subcommand = args[0];
            if (subcommand.equalsIgnoreCase("regen")) {
                server.sendChatBroadcast("~ Reloading map");
                spriteUpdater.setMapSeed(System.currentTimeMillis());
                regenMapNextRound = true;
                nextMatchCountDown = 5;
                
            }
        } else if (command.equalsIgnoreCase("me")) {
            server.sendChatBroadcast("* " + sender.getName() + " " + Joiner.on(" ").skipNulls().join(args));
        } else if (command.equalsIgnoreCase("kill")) {
            killPlayer(clientId);
        } else if (command.equalsIgnoreCase("match")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("restart")) {
                server.sendChatBroadcast("~ Starting a new round");
                nextMatchCountDown = 5;
            }
        } else if (command.equalsIgnoreCase("scores") || command.equalsIgnoreCase("scoreboard")) {
            if (winners.isEmpty()) {
                server.sendChat(clientId, "~ No matches have been won");
            } else {
                String temp = "~ Scoreboard ~\n";
                for (Map.Entry<Player, Integer> winner : ImmutableSortedMap.copyOf(winners, Ordering.natural().reverse().onResultOf(Functions.forMap(winners)).compound(Ordering.arbitrary())).entrySet()) {
                    temp += "- " + winner.getKey().getName() + ":  " + winner.getValue() + "\n";
                }
                server.sendChat(clientId, temp);
            }
        }
    }
    
    private void sendSpriteList(int clientId) {
        ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builder();
        for (Map.Entry<Integer, Sprite> entry : getSprites().entrySet()) {
            data.put(entry.getKey().toString(), ImmutableList.<Object>of(
                    entry.getValue().getClass().getName(), entry.getValue().serializeState(true)));
        }
        server.sendData(clientId, ImmutableMap.<String, Object>of(
                "type", "request",
                "request", "sprite-list",
                "data", data.build()
        ));
    }
    
    private void broadcastSpriteList() {
        ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builder();
        for (Map.Entry<Integer, Sprite> entry : getSprites().entrySet()) {
            data.put(entry.getKey().toString(), ImmutableList.<Object>of(
                    entry.getValue().getClass().getName(), entry.getValue().serializeState()));
        }
        server.sendBroadcast(ImmutableMap.<String, Object>of(
                "type", "request",
                "request", "sprite-list",
                "data", data.build(),
                "level", "force"
        ));
    }
    
    
    public int addSprite(Sprite s) {
        getSprites().put(nextSprite, s);
        if (s instanceof UpdateableSprite) {
            getUpdateableSprites().put(nextSprite, (UpdateableSprite)s);
        }
        nextSprite++;
        return nextSprite - 1;
    }
    
    private void startNewRound() {
        if (players.size() == 1) {
            inRound = false;
            Player onlyPlayer = players.entrySet().iterator().next().getValue();
            livingPlayers.remove(players.entrySet().iterator().next().getKey());
            onlyPlayer.kill();
            onlyPlayer.setPosition(onlyPlayer.getPosition());//This line is required to make the player position dirty and let it get bradcasted in the next line
            broadcastSpriteList();
            return;
        }
        for (Map.Entry<Integer, Sprite> entry : (new HashMap<Integer, Sprite>(spriteUpdater.getSprites()).entrySet())) {
            if (!(entry.getValue() instanceof Player)) {
                spriteUpdater.getSprites().remove(entry.getKey());
                if (entry.getValue() instanceof UpdateableSprite) {
                    spriteUpdater.getUpdateableSprites().remove(entry.getKey());
                }
            }
        } 
        if (regenMapNextRound) {
            spriteUpdater.setMapSeed(System.currentTimeMillis());
            server.sendBroadcast(ImmutableMap.<String, Object>of(
                        "type", "map",
                        "action", "load",
                        "seed", spriteUpdater.getMapSeed()
                ));
            regenMapNextRound = false;
        }
        inRound = true;
        server.sendChatBroadcast("~ Starting a new round");
        livingPlayers.clear();
        livingPlayers.addAll(players.keySet());
        for (Integer id : livingPlayers) {
            Player player  = (Player) getSprites().get(id);
            player.setAlive(true);
            player.setStartingAmmo();
        }
        spriteUpdater.randomizePlayerPositions(livingPlayers);
        spriteUpdater.putRandomAmoPickups(7);
        broadcastSpriteList();
    }
    
    public void killPlayer(int id) {
        Player player = ((Player) getSprites().get(id));
        player.kill();
        player.setClientIsCurrent(false);
        livingPlayers.remove(players.inverse().get(player));
        server.sendBroadcast(ImmutableMap.<String, Object>of(
                "type", "sprite",
                "action", "update",
                "id", id,
                "data", getSprites().get(id).serializeState()
        ));
        if (livingPlayers.size() == 1) {
            server.sendChatBroadcast("~ Player " + ((Player) (getSprites().get(livingPlayers.get(0)))).getName() + " has won the match");
            nextMatchCountDown = 5;
            
            Player winner = (Player) getSprites().get(livingPlayers.get(0));
            winners.put(winner, winners.containsKey(winner) ? winners.get(winner) + 1 : 1);
        }
    }

    public Map<Integer, Sprite> getSprites() {
        return spriteUpdater.getSprites();
    }
    

    public Map<Integer, UpdateableSprite> getUpdateableSprites() {
        return spriteUpdater.getUpdateableSprites();
    }
   
    
}
