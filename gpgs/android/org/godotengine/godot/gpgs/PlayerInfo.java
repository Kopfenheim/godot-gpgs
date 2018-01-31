package org.godotengine.godot.gpgs;

import android.app.Activity;

import org.godotengine.godot.GodotLib;
import org.godotengine.godot.gpgs.GodotCache;

import com.google.android.gms.games.Player;

public class PlayerInfo {
    private static final String TAG = "gpgs";

    private static final String[] GODOT_CALLBACK_FUNCTIONS = new String[] {
            "_on_play_game_services_player_icon_requested", //(String playerID, String folder, String fileName)
            "_on_play_game_services_player_banner_requested", //(String playerID, String folder, String fileName)
    };

    // Godot instance id needed to provide callback functionality in GDScript
    private int instance_id = 0;

    private Activity activity = null;
    public Player player = null;
    private GodotCache imageCache;

    public PlayerInfo(Activity activity, int instance_id, Player player){
        this.activity = activity;
        this.instance_id = instance_id;
        this.player = player;
        imageCache = new GodotCache(activity, instance_id);
    }

    public boolean requestPlayerIcon(boolean hiRes){
        if (player != null){
            if (hiRes && player.hasHiResImage()){
                imageCache.sendURIImage(
                        player.getHiResImageUri(),
                        player.getPlayerId()+"_hi_res_icon.png",
                        GODOT_CALLBACK_FUNCTIONS[0],
                        player.getPlayerId());
            }else if (player.hasIconImage()){
                imageCache.sendURIImage(
                    player.getIconImageUri(),
                    player.getPlayerId()+"_icon.png",
                    GODOT_CALLBACK_FUNCTIONS[0],
                    player.getPlayerId());
            }

            return true;
        }
        return false;
    }

    public boolean requestPlayerBanner(boolean portrait){
        if (player != null){
            if (portrait){
                imageCache.sendURIImage(
                        player.getBannerImagePortraitUri(),
                        player.getPlayerId()+"_banner_portrait.png",
                        GODOT_CALLBACK_FUNCTIONS[1],
                        player.getPlayerId());
            }else{
                imageCache.sendURIImage(
                        player.getBannerImageLandscapeUri(),
                        player.getPlayerId()+"_banner_landscape.png",
                        GODOT_CALLBACK_FUNCTIONS[1],
                        player.getPlayerId());
            }
            return true;
        }
        return false;
    }
}
