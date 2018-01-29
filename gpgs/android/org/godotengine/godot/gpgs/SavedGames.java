package org.godotengine.godot.gpgs;

import org.godotengine.godot.GodotLib;
import org.godotengine.godot.gpgs.GodotCache;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

public class SavedGames {
    private static final String TAG = "gpgs";

    private static final String SNAPSHOT_NAME_PREFIX = "snapshot-";
    private static final int RC_SAVED_GAMES = 9009;

    private static final String[] GODOT_CALLBACK_FUNCTIONS = new String[] {
            "_on_play_game_services_saved_game_loading_started", //()
            "_on_play_game_services_saved_game_loaded", //(String data, boolean loadedWithoutError)
            "_on_play_game_services_saved_game_ready_to_save", //(String snapshotName, String suggestedImagePath)
            "_on_play_game_services_saved_game_saved", //(boolean savedWithoutError)
    };

    private Activity activity = null;
    private int instance_id = 0;
    private GoogleSignInAccount signedInAccount = null;

    private SnapshotsClient snapshotsClient;
    private boolean savingFile = false;
    private int conflictResolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED;
    private GodotCache imageCache;

    public SavedGames(Activity activity, GoogleSignInAccount signedInAccount, int instance_id) {
        this.signedInAccount = signedInAccount;
        this.activity = activity;
        this.instance_id = instance_id;
        imageCache = new GodotCache(activity, instance_id);
    }

    // If allowAddButton is true then depending on the user selection, a previous save can be overwritten or a new save can be created
    // If allowAddButton is false then depending on the user selection, a previous save can be loaded
    public void showSavedGamesUI(String title, boolean allowAddButton, boolean allowDelete, int maxSavedGamesToShow){
        savingFile = allowAddButton;
        snapshotsClient = Games.getSnapshotsClient(activity, signedInAccount);
        Task<Intent> intentTask = snapshotsClient.getSelectSnapshotIntent(title, allowAddButton, allowDelete, maxSavedGamesToShow);

        intentTask.addOnSuccessListener(new OnSuccessListener<Intent>() {
            @Override
            public void onSuccess(Intent intent) {
                activity.startActivityForResult(intent, RC_SAVED_GAMES);
            }
        });
    }

    public void onMainActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == RC_SAVED_GAMES && intent != null){
            if (intent.hasExtra(SnapshotsClient.EXTRA_SNAPSHOT_METADATA)){
                // If the user selects an existing saved game file
                SnapshotMetadata snapshotMetadata = intent.getParcelableExtra(SnapshotsClient.EXTRA_SNAPSHOT_METADATA);
                String snapshotName = snapshotMetadata.getUniqueName();

                if (savingFile){
                    String suggestedImagePath = GodotCache.CACHE_FOLDER + "/" + snapshotName + "_img.png";
                    GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[2], new Object[] { snapshotName, suggestedImagePath });
                }else{
                    Log.d(TAG, "Loading existing save. unique id: " + snapshotName);
                    GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[0], new Object[] { });
                    requestLoadSnapshot(snapshotName);
                }
            }else if (intent.hasExtra(SnapshotsClient.EXTRA_SNAPSHOT_NEW)){
                // If the user selects to create a new saved game
                if (savingFile){
                    Log.d(TAG, "Creating new save");
                    String snapshotName = SNAPSHOT_NAME_PREFIX + new BigInteger(281, new Random()).toString(13);
                    String suggestedImagePath = GodotCache.CACHE_FOLDER + "/" + snapshotName + "_img.png";
                    GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[2], new Object[] { snapshotName, suggestedImagePath });
                }
            }
        }
    }

    public void requestWriteSnapshot(String snapshotName, final String data, final String description, final String imageFileName){
        snapshotsClient.open(snapshotName, true, conflictResolutionPolicy)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "ERROR while opening snapshot for saving: ", e);
                        GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[3], new Object[] { false });
                    }
                })
                .continueWith(new Continuation<SnapshotsClient.DataOrConflict<Snapshot>, Object>() {
                    @Override
                    public Object then(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) throws Exception {
                        Snapshot snapshot = task.getResult().getData();

                        snapshot.getSnapshotContents().writeBytes(data.getBytes("UTF-8"));

                        Bitmap coverImage = imageCache.getBitmap(imageFileName);
                        SnapshotMetadataChange metadata;
                        if (coverImage != null){
                            metadata = new SnapshotMetadataChange.Builder()
                                    .setCoverImage(coverImage)
                                    .setDescription(description)
                                    .build();
                        }else{
                            metadata = new SnapshotMetadataChange.Builder()
                                    .setDescription(description)
                                    .build();
                        }

                        snapshotsClient.commitAndClose(snapshot, metadata);
                        return null;
                    }
                })
                .addOnSuccessListener(new OnSuccessListener<Object>() {
                    @Override
                    public void onSuccess(Object s) {
                        GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[3], new Object[] { true });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "ERROR while writing to snapshot for saving: ", e);
                        GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[3], new Object[] { false });
                    }
                });
    }

    public void requestLoadSnapshot(String snapshotName){
        snapshotsClient.open(snapshotName, true, conflictResolutionPolicy)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "ERROR while opening snapshot for loading: ", e);
                        GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[1], new Object[] { "", false });
                    }
                })
                .continueWith(new Continuation<SnapshotsClient.DataOrConflict<Snapshot>, String>() {
                    @Override
                    public String then(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) throws Exception {
                        Snapshot snapshot = task.getResult().getData();

                        try{
                            return new String(snapshot.getSnapshotContents().readFully(),"UTF-8");
                        } catch (IOException e){
                            Log.e(TAG, "ERROR while opening snapshot for loading: ", e);
                            GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[1], new Object[] { "", false });
                        }

                        return null;
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[1], new Object[] { task.getResult(), true });
                    }
                });
    }

    public void setConflictResolutionPolicy(int value){
        conflictResolutionPolicy = value;
    }
}
