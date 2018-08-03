package org.godotengine.godot;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.GoogleApiClient;

import org.godotengine.godot.gpgs.Client;
import org.godotengine.godot.gpgs.Network;
import org.godotengine.godot.gpgs.Achievements;
import org.godotengine.godot.gpgs.Leaderboard;
import org.godotengine.godot.gpgs.RealTimeMultiplayer;
import org.godotengine.godot.gpgs.SavedGames;
import org.godotengine.godot.gpgs.GodotCache;

public class GodotPlayGameServices extends Godot.SingletonBase {

    private static final String TAG = "gpgs";
	public static final String STRING_DATA_DELIMITER = ",";

    private static final int REQUEST_RESOLVE_ERROR = 1001;

    public static final String GODOT_SUB_FOLDER = "files";
    public static final String CACHE_FOLDER = "gpgs_lib_cache";

    private Activity activity = null;
    private int[] instanceIDs = new int[]{
    		0, // For connection, network and player info callbacks
			0, // For achievements callbacks
			0, // For leaderboards callbacks
			0, // For saved games callbacks
			0, // For real time multiplayer callbacks
	};
    private GoogleSignInAccount signedInAccount = null;

    private Client client;
    private Network network;
    private Achievements achievements;
    private Leaderboard leaderboard;
    private SavedGames savedGames;
    private RealTimeMultiplayer realTimeMultiplayer;

    private boolean savedGamesEnabled = false;


    // This function is called by Godot to initialize the singleton
    static public Godot.SingletonBase initialize(Activity activity) {
        return new GodotPlayGameServices(activity);
    }

    // Constructor
    public GodotPlayGameServices(Activity activity) {
        this.activity = activity;
        registerClass("GodotPlayGameServices", new String[] {
                "init", "clearCache","keepScreenOn","getDelimiter",

                "signInInteractive", "signInSilent", "signOut",

                "isOnline", "isWifiConnected", "isMobileConnected",

                "getCurrentPlayerID","getCurrentPlayerDisplayName","getCurrentPlayerTitle",
                "getCurrentPlayerLevel","getCurrentPlayerXP","getCurrentPlayerMaxXP","getCurrentPlayerMinXP",
                "requestCurrentPlayerIcon","requestCurrentPlayerBanner",

                "showAchievementsUI","unlockAchievement","incrementAchievement",

                "showLeaderboardUI","submitScore",

                "showSavedGamesUI","requestWriteSnapshot","requestLoadSnapshot",

				"rtmStartQuickGame","rtmShowInvitePlayerUI","rtmShowWaitingRoomUI","rtmHideWaitingRoomUI",
				"rtmLeaveRoom","rtmGetAllParticipantIDs","rtmGetParticipantDisplayName",
				"rtmRequestParticipantIcon","rtmGetParticipantStatus","rtmIsParticipantConnected",
				"rtmShowInvitationInbox","rtmJoinInvitation","rtmCheckForInvitation",
				"rtmSendReliableData","rtmSendReliableDataToAll","rtmSendUnreliableData",
				"rtmSendUnreliableDataToAll"
        });
    }

	/**
	 * @param instanceIDsStr 	The instance IDs of the scripts where the various groups of callback
	 *                       	functions are scattered in Godot (In Godot: get_instance_id()). This
	 *                       	allows the callback functions in GDScript to be distributed across
	 *                       	various scripts which should help make them more manageable.
	 *                    		The data in the string must be arranged in the following order and
	 *                    		each data entry must be separated by the defined delimiter
	 *                    		(use getDelimiter() in Godot to get the expected delimiter):
									0: For connection, network and player info callbacks
									1: For achievements callbacks
									2: For leaderboards callbacks
									3: For saved games callbacks
									4: For real time multiplayer callbacks
	 * @param useSavedGames Specifies whether or not play game services will be compiled with saved games functionality or not
	 */
	public void init(final String instanceIDsStr, boolean useSavedGames) {
		setInstanceIDsFromString(instanceIDsStr, STRING_DATA_DELIMITER);
		client = new Client(activity, instanceIDs[0], this, useSavedGames);
		network = new Network(activity);
		savedGamesEnabled = useSavedGames;
	}

	public void clearCache(){
		GodotCache.clearCache(activity);
	}

	public void keepScreenOn(final boolean keepOn){
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (keepOn) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
		});
	}

	public String getDelimiter(){
		return STRING_DATA_DELIMITER;
	}

    public void setClient(GoogleSignInAccount signedInAccount) {
        this.signedInAccount = signedInAccount;
        this.achievements = new Achievements(activity, signedInAccount, instanceIDs[1]);
        this.leaderboard = new Leaderboard(activity, signedInAccount, instanceIDs[2]);
        this.savedGames = new SavedGames(activity,signedInAccount,instanceIDs[3]);
        this.realTimeMultiplayer = new RealTimeMultiplayer(activity, signedInAccount, instanceIDs[4]);
    }

    public void removeClient(){
        this.signedInAccount = null;
        this.achievements = null;
        this.leaderboard = null;
        this.savedGames = null;
        this.realTimeMultiplayer = null;
    }

    @Override
    protected void onMainActivityResult(int request, int response, Intent intent) {
        client.onMainActivityResult(request, response, intent);
        if(savedGames != null){
          savedGames.onMainActivityResult(request, response, intent);
        }
        if(realTimeMultiplayer != null){
          realTimeMultiplayer.onMainActivityResult(request, response, intent);
        }
    }

    //region Connection methods -----------------------------------------------------------------------------

    /**
     * Sign In while showing the full UI
     *
     * @godot_callback _on_play_game_services_sign_in_success(signInType, playerId)
     * @godot_callback _on_play_game_services_player_info_failure
     * @godot_callback _on_play_game_services_sign_in_failure(signInType)
     * @return true if sign in process is started
     */
    public void signInInteractive() {
        if (client == null) return;
        client.signInInteractive();
    }

    /**
     * Sign In silently with minimal interference
     *
     * @godot_callback _on_play_game_services_sign_in_success(signInType, playerId)
     * @godot_callback _on_play_game_services_sign_in_failure(signInType)
     * @return true if sign in process is started
     */
    public void signInSilent() {
        if (client == null) return;
        client.signInSilent();
    }

    /**
     * Sign Out method
     *
     * @godot_callback _on_play_game_services_sign_out_success()
     * @godot_callback _on_play_game_services_sign_out_failure()
     * @return true if sign out process is started
     */
    public void signOut() {
        if (client == null) return;
        client.signOut();
    }

    //endregion

    //region Network Methods --------------------------------------------------------------------------------

    /**
     * Is the user connected to the internet
     *
     * @return true if user is online
     */
    public boolean isOnline() {
        if (network == null) return false;
        return network.isOnline();
    }

    /**
     * Is the user connected to the WiFi
     *
     * @return true if user is connected to WiFi
     */
    public boolean isWifiConnected() {
        if (network == null) return false;
        return network.isWifiConnected();
    }

    /**
     * Is the user connected to the Mobile network
     *
     * @return true if user is connected to Mobile network
     */
    public boolean isMobileConnected() {
        if (network == null) return false;
        return network.isMobileConnected();
    }

    //endregion

    //region Currently Signed In Player Information Methods -------------------------------------------------

    public String getCurrentPlayerID(){
        if (client != null && client.currentPlayer != null)
            return client.currentPlayer.player.getPlayerId();
        return "";
    }

    public String getCurrentPlayerDisplayName(){
        if (client != null && client.currentPlayer != null)
            return client.currentPlayer.player.getDisplayName();
        return "";
    }

    public String getCurrentPlayerTitle(){
        if (client != null && client.currentPlayer != null)
            return client.currentPlayer.player.getTitle();
        return "";
    }

    public int getCurrentPlayerLevel(){
        if (client != null && client.currentPlayer != null)
            return client.currentPlayer.player.getLevelInfo().getCurrentLevel().getLevelNumber();
        return 0;
    }

    public String getCurrentPlayerXP(){
        if (client != null && client.currentPlayer != null)
            return "" + client.currentPlayer.player.getLevelInfo().getCurrentXpTotal();
        return "";
    }

    public String getCurrentPlayerMaxXP(){
        if (client != null && client.currentPlayer != null)
            return "" + client.currentPlayer.player.getLevelInfo().getCurrentLevel().getMaxXp();
        return "";
    }

    public String getCurrentPlayerMinXP(){
        if (client != null && client.currentPlayer != null)
            return "" + client.currentPlayer.player.getLevelInfo().getCurrentLevel().getMinXp();
        return "";
    }

    /**
     * @godot_callback _on_play_game_services_player_icon_requested(id, folder, fileName)
     */
    public boolean requestCurrentPlayerIcon(boolean hiRes){
        if (client != null)
            return client.currentPlayer.requestPlayerIcon(hiRes);
        return false;
    }

    /**
     * @godot_callback _on_play_game_services_player_banner_portrait_requested(id, folder, fileName)
     * @godot_callback _on_play_game_services_player_banner_landscape_requested(id, folder, fileName)
     */
    public boolean requestCurrentPlayerBanner(boolean portrait){
        if (client != null)
            return client.currentPlayer.requestPlayerBanner(portrait);
        return false;
    }

    //endregion

    //region Achievements -----------------------------------------------------------------------------------

    public boolean showAchievementsUI() {
        return achievements != null && achievements.showAchievementsUI();
    }

    public void unlockAchievement(String achievementID){
        if (achievements != null) achievements.unlockAchievement(achievementID);
    }

    public void incrementAchievement(String achievementID, int incrementBy){
        if (achievements != null) achievements.incrementAchievement(achievementID, incrementBy);
    }

    //endregion

    //region Leaderboard ------------------------------------------------------------------------------------

    public boolean showLeaderboardUI(String leaderboardID){
        return leaderboard != null && leaderboard.showLeaderboardUI(leaderboardID);
    }

    public void submitScore(String leaderboardID, int score){
        if (leaderboard != null) leaderboard.submitScore(leaderboardID, score);
    }

    //endregion

    //region Saved Games (Snapshots) ------------------------------------------------------------------------

    public void showSavedGamesUI(String title, boolean allowAddButton, boolean allowDelete, int maxSavedGamesToShow){
        Log.d(TAG, "showSavedGamesUI()");
        if (savedGamesEnabled) {
            if (savedGames != null)
                savedGames.showSavedGamesUI(title, allowAddButton, allowDelete, maxSavedGamesToShow);
        }else {
            Log.d(TAG, "Saved Games not enabled. Need to pass in true for the second input of the singleton's init() function to use this functionality.");
        }
    }

    public void requestWriteSnapshot(String snapshotName, String data, String description, String imageFileName){
        if (savedGamesEnabled) {
            if (savedGames != null)
                savedGames.requestWriteSnapshot(snapshotName, data, description, imageFileName);
        }else {
            Log.d(TAG, "Saved Games not enabled. Need to pass in true for the second input of the singleton's init() function to use this functionality.");
        }
    }

    public void requestLoadSnapshot(String snapshotName){
        if (savedGamesEnabled) {
            if (savedGames != null)
                savedGames.requestLoadSnapshot(snapshotName);
        }else {
            Log.d(TAG, "Saved Games not enabled. Need to pass in true for the second input of the singleton's init() function to use this functionality.");
        }
    }

    //endregion

	//region Real Time Multiplayer --------------------------------------------------------------------------

	public void rtmStartQuickGame(int minAutoMatchPlayers, int maxAutoMatchPlayers, int playerRoleBitmask){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.startQuickGame(minAutoMatchPlayers, maxAutoMatchPlayers, playerRoleBitmask);
		}
	}

	public void rtmShowInvitePlayerUI(int minPlayers, int maxPlayers, boolean allowAutoMatch){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.showInvitePlayerUI(minPlayers, maxPlayers, allowAutoMatch);
		}
	}

	public void rtmShowWaitingRoomUI(int maxPlayersToStartGame){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.showWaitingRoomUI(maxPlayersToStartGame);
		}
	}

	public void rtmHideWaitingRoomUI(){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.hideWaitingRoomUI();
		}
	}

	public void rtmLeaveRoom(){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.leaveRoom();
		}
	}

	public String rtmGetAllParticipantIDs(){
		if (realTimeMultiplayer != null){
			return realTimeMultiplayer.getAllParticipantIDs();
		}
		return "";
	}

	public String rtmGetParticipantDisplayName(String participantID){
		if (realTimeMultiplayer != null){
			return realTimeMultiplayer.getParticipantDisplayName(participantID);
		}
		return "";
	}

	public boolean rtmRequestParticipantIcon(String participantID, boolean hiRes) {
		return realTimeMultiplayer != null && realTimeMultiplayer.requestParticipantIcon(participantID, hiRes);
	}

	public int rtmGetParticipantStatus(String participantID){
		if (realTimeMultiplayer != null){
			return realTimeMultiplayer.getParticipantStatus(participantID);
		}
		return -1;
	}

	public boolean rtmIsParticipantConnected(String participantID) {
		return realTimeMultiplayer != null && realTimeMultiplayer.isParticipantConnected(participantID);
	}

	public void rtmShowInvitationInbox(){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.showInvitationInbox();
		}
	}

	public void rtmJoinInvitation(String invitationID){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.joinInvitation(invitationID);
		}
	}

	public void rtmCheckForInvitation(){
		//TODO: test
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.checkForInvitation();
		}
	}

	public void rtmSendReliableData(String data, String participantIDs){
		//TODO: test
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.sendReliableData(data, participantIDs);
		}
	}

	public void rtmSendReliableDataToAll(String data){
		//TODO: test
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.sendReliableDataToAll(data);
		}
	}

	public void rtmSendUnreliableData(String data, String participantIDs){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.sendUnreliableData(data, participantIDs);
		}
	}

	public void rtmSendUnreliableDataToAll(String data){
		if (realTimeMultiplayer != null){
			realTimeMultiplayer.sendUnreliableDataToAll(data);
		}
	}


	//endregion

	//region Turn Based Multiplayer -------------------------------------------------------------------------

	//endregion

	//region HELPER FUNCTIONS -------------------------------------------------------------------------------

	/**
	 *
	 * @param ids a string with multiple possibly multiple (or a single) instance IDs. If this
	 *            contains fewer IDs in the string than needed, then the last ID in this string will
	 *            be repeated for the remaining entries in the array
	 * @param delimiter The string that separates the IDs in the ids string
	 */
	private void setInstanceIDsFromString(String ids, String delimiter){
		String[] splitIDs = ids.split(delimiter);

		for (int i = 0; i < instanceIDs.length; i++){
			if (i < splitIDs.length) instanceIDs[i] = Integer.parseInt(splitIDs[i]);
			else instanceIDs[i] = Integer.parseInt(splitIDs[splitIDs.length - 1]);

			Log.d(TAG, "Instance ID [" + i + "]: " + instanceIDs[i]);
		}
	}

	//endregion
}
