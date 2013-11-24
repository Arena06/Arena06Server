package com.assemblr.arena06.server;

import java.util.Map;

public class ServerMain {
    
    private final PacketServer server = new PacketServer(30155);
    
    private final Thread runner;
    private boolean running = false;
    
    public static void main(String[] args) throws Exception {
        ServerMain main = new ServerMain();
        main.start();
    }
    
    public ServerMain() {
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
            System.out.println(packet);
        }
    }
    
}
