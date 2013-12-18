/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.assemblr.arena06.server.spritemanager;

import com.assemblr.arena06.common.chat.ChatBroadcaster;
import com.assemblr.arena06.common.data.Sprite;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Henry
 */
public class SpriteCollisionManager {
    public void checkContact(Map<Integer, Sprite> sprites, List<Integer> dirtySprites, List<Integer> spritesPendingRemoveal, ChatBroadcaster chatter) {
        Map<Integer, Sprite> spritesRemaining = new HashMap<Integer, Sprite>(sprites);
        for (Map.Entry<Integer, Sprite> sprite : sprites.entrySet()) {
            Integer id = sprite.getKey();
            Sprite s1 = sprite.getValue();
            for (Map.Entry<Integer, Sprite> interactor : spritesRemaining.entrySet()) {
                Sprite s2 = interactor.getValue();
                if (s1.getBounds().intersects(s2.getBounds())) {
                    s1.onContact(id, s2, interactor.getKey(), dirtySprites, spritesPendingRemoveal, chatter);
                    s2.onContact(interactor.getKey(), s1, id, dirtySprites,  spritesPendingRemoveal, chatter);
                }
            }
            spritesRemaining.remove(id);
            
        }
    }
}
