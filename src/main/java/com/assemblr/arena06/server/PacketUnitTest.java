/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.assemblr.arena06.server;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Henry
 */
public class PacketUnitTest {

    public static void main(String[] args) {
        final PacketUnitTest put = new PacketUnitTest();
        System.out.println("Starting server: ");
        try {
            Thread serverrunner = new Thread(new Runnable() {

                public void run() {
                    try {
                        put.server.run();
                    } catch (Exception ex) {
                        Logger.getLogger(PacketUnitTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            );
            serverrunner.start();
            
        System.out.println("Server started");
        } catch (Exception e) {
            e.printStackTrace();
        }
        while (true) {
            put.update();
        }
    }
    int port = 30155;
    public PacketServer server = new PacketServer(port);

    public void update() {
        Map<String, Object> packet;
        while ((packet = server.getIncomingPackets().poll()) != null) {
            System.out.println("packet");
            processPacket(packet);
        }
    }

    public void processPacket(Map<String, Object> packet) {
        for (Map.Entry<String, Object> packetObj : packet.entrySet()) {
            String string = packetObj.getKey();
            Object object = packetObj.getValue();
            System.out.println("Packet: ");
            System.out.println(string + ": " + object);
            
        }
    }
}
