package org.godotengine.godot.gpgs;

import android.app.Activity;
import android.content.Intent;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.games.Games;
import com.google.android.gms.tasks.OnSuccessListener;

public class Achievements {
    private static final String TAG = "gpgs";

    private static final int RC_ACHIEVEMENT_UI = 9003;

    private Activity activity = null;
    private int instance_id = 0;
    private GoogleSignInAccount signedInAccount = null;

    public Achievements(Activity activity, GoogleSignInAccount signedInAccount, int instance_id) {
        this.signedInAccount = signedInAccount;
        this.activity = activity;
        this.instance_id = instance_id;
    }

    public boolean showAchievementsUI(){
        if (signedInAccount != null){
            Games.getAchievementsClient(activity,signedInAccount)
                    .getAchievementsIntent()
                    .addOnSuccessListener(new OnSuccessListener<Intent>() {
                        @Override
                        public void onSuccess(Intent intent) {
                            activity.startActivityForResult(intent, RC_ACHIEVEMENT_UI);
                        }
                    });
            return true;
        }
        return false;
    }

    public void unlockAchievement(String achievementID){
        if (signedInAccount != null)
            Games.getAchievementsClient(activity, signedInAccount).unlock(achievementID);
    }

    public void incrementAchievement(String achievementID, int incrementBy){
        if (signedInAccount != null)
            Games.getAchievementsClient(activity,signedInAccount).increment(achievementID, incrementBy);
    }
}
