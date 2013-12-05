package com.assemblr.arena06.server.spritemanager;

import com.assemblr.arena06.common.data.Bullet;
import com.assemblr.arena06.common.data.Player;
import com.assemblr.arena06.common.data.Sprite;
import com.assemblr.arena06.common.data.UpdateableSprite;
import com.assemblr.arena06.common.data.map.TileType;
import com.assemblr.arena06.common.data.map.generators.MapGenerator;
import com.assemblr.arena06.common.data.map.generators.RoomGenerator;
import com.assemblr.arena06.server.PacketServer;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpriteUpdater {
    private Map<Integer, Sprite> sprites = new HashMap<Integer, Sprite>();
    private Map<Integer, UpdateableSprite> updateableSprites = new HashMap<Integer, UpdateableSprite>();
    private final PacketServer server;
    private double mapSeed;
    private TileType[][] map;
    private MapGenerator mapGenerator = new RoomGenerator();
    private final SpriteCollisionManager spriteCollisionManager = new SpriteCollisionManager();
    
    public SpriteUpdater(PacketServer server, long mapSeed) {
        this.server = server;
        this.mapSeed = mapSeed;
        map = mapGenerator.generateMap(mapSeed);
    }
    public void updateAllSprites(double delta) {
        for (Map.Entry<Integer, UpdateableSprite> sprite : updateableSprites.entrySet()) {
            System.out.println("dsa");
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
        
        spriteCollisionManager.checkContact(sprites, spritesPendingRemoveal, server);
        
        for (Integer id :spritesPendingRemoveal) {
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
        spritesPendingRemoveal.clear();
        
    }
    
    private List<Integer> spritesPendingRemoveal = new ArrayList<Integer>();
    
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
}
