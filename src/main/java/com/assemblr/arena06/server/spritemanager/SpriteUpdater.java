package com.assemblr.arena06.server.spritemanager;

import com.assemblr.arena06.common.data.AmmoPickup;
import com.assemblr.arena06.common.data.Bullet;
import com.assemblr.arena06.common.data.Player;
import com.assemblr.arena06.common.data.Sprite;
import com.assemblr.arena06.common.data.UpdateableSprite;
import com.assemblr.arena06.common.data.map.TileType;
import com.assemblr.arena06.common.data.map.generators.MapGenerator;
import com.assemblr.arena06.common.data.map.generators.RoomGenerator;
import com.assemblr.arena06.server.PacketServer;
import com.assemblr.arena06.server.ServerMain;
import com.google.common.collect.ImmutableMap;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SpriteUpdater {
    private Map<Integer, Sprite> sprites = new HashMap<Integer, Sprite>();
    private Map<Integer, UpdateableSprite> updateableSprites = new HashMap<Integer, UpdateableSprite>();
    private final PacketServer server;
    private final ServerMain mainServer;
    private long mapSeed;
    private TileType[][] map;
    private MapGenerator mapGenerator = new RoomGenerator();
    private final SpriteCollisionManager spriteCollisionManager = new SpriteCollisionManager();
    
    public SpriteUpdater(PacketServer packetServer, ServerMain mainServer, long mapSeed) {
        this.server = packetServer;
        this.mainServer = mainServer;
        this.mapSeed = mapSeed;
        map = mapGenerator.generateMap(mapSeed);
    }
    public void updateAllSprites(double delta) {
        for (Map.Entry<Integer, UpdateableSprite> sprite : updateableSprites.entrySet()) {
            UpdateableSprite updateableSprite = sprite.getValue();
            updateableSprite.update(delta);
            //this is where collision detection and players getting shot will happen
            if (updateableSprite instanceof Bullet) {
                Bullet b = (Bullet) updateableSprite;
                if (shouldBeDestroyed(b)) {
                    spritesPendingRemoveal.add(sprite.getKey());
                }
            }
        }
        
        spriteCollisionManager.checkContact(sprites, dirtySprites, spritesPendingRemoveal, server);
        for (Integer id : dirtySprites) {
                server.sendBroadcast(ImmutableMap.<String, Object>of(
                        "type", "sprite",
                        "action", "update",
                        "id", id,
                        "data", getSprites().get(id).serializeState()
                ));
                if (getSprites().get(id) instanceof  Player) {
                    ((Player)getSprites().get(id)).setClientIsCurrent(false);
                }
        }
        dirtySprites.clear();
        for (Integer id : spritesPendingRemoveal) {
            if (sprites.get(id) instanceof Player) {
                mainServer.killPlayer(id);
            } else {
                server.sendBroadcast(ImmutableMap.<String, Object>of(
                        "type", "sprite",
                        "action", "remove",
                        "id", id
                ));

                sprites.remove(id);
                if (updateableSprites.containsKey(id)) {
                    updateableSprites.remove(id);
                }
            }
            
            
        }
        spritesPendingRemoveal.clear();
        
    }
    
    private List<Integer> spritesPendingRemoveal = new ArrayList<Integer>();
    private List<Integer> dirtySprites = new ArrayList<Integer>();
    
    private boolean shouldBeDestroyed(Bullet b) {
        return map[b.getTileX()][b.getTileY()] == TileType.WALL || map[b.getTileX()][b.getTileY()] == TileType.NONE
                || map[b.getTileXRightSide()][b.getTileY()] == TileType.WALL || map[b.getTileXRightSide()][b.getTileY()] == TileType.NONE
                || map[b.getTileX()][b.getTileYBottom()] == TileType.WALL || map[b.getTileX()][b.getTileYBottom()] == TileType.NONE
                || map[b.getTileXRightSide()][b.getTileYBottom()] == TileType.WALL || map[b.getTileXRightSide()][b.getTileYBottom()] == TileType.NONE;
    }
    /**
     * @return the sprites
     */
    public Map<Integer, Sprite> getSprites() {
        return sprites;
    }

    /**
     * @return the updateableSprites
     */
    public Map<Integer, UpdateableSprite> getUpdateableSprites() {
        return updateableSprites;
    }
    
    Random random = new Random();
    public void randomizePlayerPositions(List<Integer> players) {
        for (int i : players) {
            Player player = (Player) sprites.get(i);
            do {
                player.setPosition(new Point2D.Double(random.nextInt(map.length) * MapGenerator.TILE_SIZE, random.nextInt(map[0].length) * MapGenerator.TILE_SIZE));
            } while (map[(int) Math.round(player.getPosition().x / MapGenerator.TILE_SIZE)][(int) Math.round(player.getPosition().y / MapGenerator.TILE_SIZE)] != TileType.FLOOR);
            System.out.println("player pos: " + player.getPosition());
        }
    }
    
    public void setMapSeed(long mapSeed) {
        this.mapSeed = mapSeed;
        map = mapGenerator.generateMap(mapSeed);
    }
    
    public long getMapSeed() {
        return mapSeed;
    }
    
    public void putRandomAmoPickups(int number) {
        for (int i = 0; i < number; i++) {
            AmmoPickup ammo = new AmmoPickup();
            
        do {
                ammo.setPosition(new Point2D.Double(random.nextInt(map.length) * MapGenerator.TILE_SIZE, random.nextInt(map[0].length) * MapGenerator.TILE_SIZE));
            } while (map[(int) Math.round(ammo.getPosition().x / MapGenerator.TILE_SIZE)][(int) Math.round(ammo.getPosition().y / MapGenerator.TILE_SIZE)] != TileType.FLOOR);
        mainServer.addSprite(ammo);
        }
    }
}
