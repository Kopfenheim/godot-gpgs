package org.godotengine.godot.gpgs;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesCallbackStatusCodes;
import com.google.android.gms.games.InvitationsClient;
import com.google.android.gms.games.RealTimeMultiplayerClient;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.InvitationCallback;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.OnRealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateCallback;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.godotengine.godot.GodotLib;
import org.godotengine.godot.GodotPlayGameServices;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RealTimeMultiplayer {
	private static final String TAG = "gpgs";

	private static final int RC_SELECT_PLAYERS = 9006;
	private static final int RC_WAITING_ROOM = 9007;
	private static final int RC_INVITATION_INBOX = 9008;

	private static final String[] GODOT_CALLBACK_FUNCTIONS = new String[] {
			"_on_play_game_services_rtm_participant_icon_requested", //(String participantID, String folder, String fileName)

			"_on_play_game_services_rtm_room_client_created", //(boolean success, String roomID)
			"_on_play_game_services_rtm_room_client_joined", //(boolean success, String roomID)
			"_on_play_game_services_rtm_room_client_left_room", //(String roomID)
			"_on_play_game_services_rtm_room_all_participants_connected", //(boolean success, String roomID)

			"_on_play_game_services_rtm_room_status_room_connecting", //(String roomID)
			"_on_play_game_services_rtm_room_status_auto_matching", //(String roomID)
			"_on_play_game_services_rtm_room_status_peer_invited_to_room", //(String concatenatedIDs)
			"_on_play_game_services_rtm_room_status_peer_declined_invitation", //(String concatenatedIDs)
			"_on_play_game_services_rtm_room_status_peer_joined", //(String concatenatedIDs)
			"_on_play_game_services_rtm_room_status_peer_left", //(String concatenatedIDs)
			"_on_play_game_services_rtm_room_status_connected_to_room", //(String roomID, String myParticipantID)
			"_on_play_game_services_rtm_room_status_disconnected_from_room", //(String roomID)
			"_on_play_game_services_rtm_room_status_peers_connected", //(String concatenatedIDs)
			"_on_play_game_services_rtm_room_status_peers_disconnected", //(String concatenatedIDs)
			"_on_play_game_services_rtm_room_status_p2p_connected", //(String participantID)
			"_on_play_game_services_rtm_room_status_p2p_disconnected", //(String participantID)

			"_on_play_game_services_rtm_invitation_received", //(String invitationID)
			"_on_play_game_services_rtm_invitation_ui_invitation_accepted", //(String invitationID)

			"_on_play_game_services_rtm_waiting_room_ui_finished", //()
			"_on_play_game_services_rtm_waiting_room_ui_closed", //()
			"_on_play_game_services_rtm_waiting_room_ui_left_room", //()

			"_on_play_game_services_rtm_reliable_message_sent", //(String recipientId, String tokenID)
			"_on_play_game_services_rtm_reliable_message_confirmed", //(String recipientId, String tokenID)

			"_on_play_game_services_rtm_message_received", //(String senderId, String data, boolean isReliable)
	};

	private Activity activity = null;
	private int instance_id = 0;
	private GoogleSignInAccount signedInAccount = null;

	private RealTimeMultiplayerClient rtmClient = null;
	private InvitationsClient invitationsClient = null;
	private GodotCache imageCache = null;
	private RoomConfig currentRoomConfig = null;
	private Room tempRoom = null;
	private Room connectedRoom = null;
	private boolean ignoreWaitingRoomUIOutput = false;
	private String myParticipantId = "";

	public RealTimeMultiplayer(Activity activity, GoogleSignInAccount signedInAccount, final int instance_id){
		imageCache = new GodotCache(activity, instance_id);
		this.signedInAccount = signedInAccount;
		this.activity = activity;
		this.instance_id = instance_id;

		rtmClient = Games.getRealTimeMultiplayerClient(activity, signedInAccount);
		invitationsClient = Games.getInvitationsClient(activity, signedInAccount);

		invitationsClient.registerInvitationCallback(new InvitationCallback() {
			@Override
			public void onInvitationReceived(@NonNull Invitation invitation) {
				Log.d(TAG, "Invitation received");

				callGodot(17, new Object[]{invitation.getInvitationId()}, "invitation id: " + invitation.getInvitationId());
			}

			@Override
			public void onInvitationRemoved(@NonNull String invitationId) {
				// Invitation removed.
				Log.d(TAG, "Invitation removed");
			}
		});
	}

	public void startQuickGame(int minAutoMatchPlayers, int maxAutoMatchPlayers, int playerRoleBitmask){
		if (signedInAccount != null){
			Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(minAutoMatchPlayers, maxAutoMatchPlayers, playerRoleBitmask);

			// build the room configurations
			currentRoomConfig = RoomConfig.builder(roomUpdateCallback)
					.setOnMessageReceivedListener(messageReceivedListener)
					.setRoomStatusUpdateCallback(roomStatusUpdateCallback)
					.setAutoMatchCriteria(autoMatchCriteria)
					.build();

			rtmClient.create(currentRoomConfig);
			Log.d(TAG, "Starting quick match");
		}
	}

	public void showInvitePlayerUI(int minPlayers, int maxPlayers, boolean allowAutoMatch){
		if (signedInAccount != null) {
			rtmClient.getSelectOpponentsIntent(minPlayers, maxPlayers, allowAutoMatch)
					.addOnSuccessListener(new OnSuccessListener<Intent>() {
						@Override
						public void onSuccess(Intent intent) {
							activity.startActivityForResult(intent, RC_SELECT_PLAYERS);
						}
					});
			Log.d(TAG, "Starting player invitation UI");
		}
	}

	public void showWaitingRoomUI(int maxPlayersToStartGame){
		if (tempRoom != null){
			Log.d(TAG, "Launching Waiting room UI activity");
			ignoreWaitingRoomUIOutput = false;
			rtmClient.getWaitingRoomIntent(tempRoom, maxPlayersToStartGame)
					.addOnSuccessListener(new OnSuccessListener<Intent>() {
						@Override
						public void onSuccess(Intent intent) {
							activity.startActivityForResult(intent, RC_WAITING_ROOM);
						}
					});
		}else{
			Log.d(TAG, "Can not start waiting room UI. A room has not been created yet");
		}
	}

	public void showInvitationInbox(){
		invitationsClient.getInvitationInboxIntent()
				.addOnSuccessListener(new OnSuccessListener<Intent>() {
					@Override
					public void onSuccess(Intent intent) {
						activity.startActivityForResult(intent, RC_INVITATION_INBOX);
					}
				});
	}

	public void hideWaitingRoomUI(){
		ignoreWaitingRoomUIOutput = true;
		activity.finishActivity(RC_WAITING_ROOM);
	}

	public void leaveRoom(){
		if (connectedRoom != null)
			rtmClient.leave(currentRoomConfig, connectedRoom.getRoomId());
		else if (tempRoom != null && currentRoomConfig != null)
			rtmClient.leave(currentRoomConfig, tempRoom.getRoomId());
	}

	public void checkForInvitation(){
		Games.getGamesClient(activity, signedInAccount).getActivationHint().addOnSuccessListener(new OnSuccessListener<Bundle>() {
			@Override
			public void onSuccess(Bundle bundle) {
				if (bundle != null){
					Invitation invitation = bundle.getParcelable(Multiplayer.EXTRA_INVITATION);
					if (invitation != null){
						callGodot(17, new Object[]{invitation.getInvitationId()}, "invitation id: " + invitation.getInvitationId());
					}
				}else{
					Log.d(TAG, "Could not check for Invitations. The returned bundle was null");
				}
			}
		});
	}

	public void joinInvitation(String invitationID){
		if (signedInAccount != null){
			currentRoomConfig = RoomConfig.builder(roomUpdateCallback)
					.setInvitationIdToAccept(invitationID)
					.setOnMessageReceivedListener(messageReceivedListener)
					.setRoomStatusUpdateCallback(roomStatusUpdateCallback)
					.build();
			rtmClient.join(currentRoomConfig);
		}
	}

	public String getAllParticipantIDs(){
		if (connectedRoom != null){
			return concatenateStringArrayList(connectedRoom.getParticipantIds(), GodotPlayGameServices.STRING_DATA_DELIMITER);
		}
		return "";
	}

	public String getParticipantDisplayName(String participantID){
		if (connectedRoom != null){
			try{
				String displayName = connectedRoom.getParticipant(participantID).getDisplayName();
				Log.d(TAG, "Display name for participant id of " + participantID + " is: " + displayName);
				return displayName;
			}catch (IllegalStateException e){
				Log.e(TAG, "Incorrect Participant ID", e);
			}
		}
		return "";
	}

	public boolean requestParticipantIcon(String participantID, boolean hiRes){
		if (connectedRoom != null){
			try{
				Participant participant = connectedRoom.getParticipant(participantID);
				if (hiRes){
					Uri hiResIconUri = participant.getHiResImageUri();
					if (hiResIconUri != null){
						imageCache.sendURIImage(
								hiResIconUri,
								participantID+"_hi_res_icon.png",
								GODOT_CALLBACK_FUNCTIONS[0],
								participantID);
					}else{
						Log.d(TAG, "Hi Res Icon is unavailable for participant ID: " + participantID);
						return false;
					}
				}else{
					Uri iconUri = participant.getIconImageUri();
					if (iconUri != null){
						imageCache.sendURIImage(
								iconUri,
								participantID+"_icon.png",
								GODOT_CALLBACK_FUNCTIONS[0],
								participantID);
					}else{
						Log.d(TAG, "Icon is unavailable for participant ID: " + participantID);
						return false;
					}
				}
				return true;
			}catch (IllegalStateException e){
				Log.e(TAG, "Incorrect Participant ID", e);
			}
		}else{ Log.d(TAG, "currentRoom is null"); }
		return false;
	}

	public int getParticipantStatus(String participantID){
		if (connectedRoom != null){
			try{
				return connectedRoom.getParticipant(participantID).getStatus();
			}catch (IllegalStateException e){
				Log.e(TAG, "Incorrect Participant ID", e);
			}
		}
		return -1;
	}

	public boolean isParticipantConnected(String participantID){
		if (connectedRoom != null){
			try{
				return connectedRoom.getParticipant(participantID).isConnectedToRoom();
			}catch (IllegalStateException e){
				Log.e(TAG, "Incorrect Participant ID", e);
			}
		}
		return false;
	}

	public void sendReliableData(String data, String participantIDs){
		if (connectedRoom != null){
			String[] ids = participantIDs.split(GodotPlayGameServices.STRING_DATA_DELIMITER);
			for (String id : ids){
				sendReliableDataToParticipant(data, id);
			}
		}
	}

	public void sendReliableDataToAll(String data){
		if (connectedRoom != null){
			ArrayList<String> ids = connectedRoom.getParticipantIds();
			for (String id : ids){
				sendReliableDataToParticipant(data, id);
			}
		}
	}

	public void sendUnreliableData(String data, String participantIDs){
		if (connectedRoom != null){
			try {
				String[] ids = participantIDs.split(GodotPlayGameServices.STRING_DATA_DELIMITER);
				List<String> idList = Arrays.asList(ids);
				rtmClient.sendUnreliableMessage(data.getBytes("UTF-8"), connectedRoom.getRoomId(), idList);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
	}

	public void sendUnreliableDataToAll(String data){
		if (connectedRoom != null){
			try {
				rtmClient.sendUnreliableMessageToOthers(data.getBytes("UTF-8"), connectedRoom.getRoomId());
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
	}

	public void onMainActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == RC_SELECT_PLAYERS){
			if (resultCode != Activity.RESULT_OK){ return; }

			final ArrayList<String> invitees = intent.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);

			for (String invitee : invitees){
				Log.d(TAG, "Invited player id: " + invitee);
			}

			int minAutoPlayers = intent.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
			int maxAutoPlayers = intent.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);

			// build the room configurations
			RoomConfig.Builder roomBuilder = RoomConfig.builder(roomUpdateCallback)
					.setOnMessageReceivedListener(messageReceivedListener)
					.setRoomStatusUpdateCallback(roomStatusUpdateCallback)
					.addPlayersToInvite(invitees);
			if (minAutoPlayers > 0)
				roomBuilder.setAutoMatchCriteria(RoomConfig.createAutoMatchCriteria(minAutoPlayers, maxAutoPlayers, 0));

			currentRoomConfig = roomBuilder.build();
			rtmClient.create(currentRoomConfig);
		}
		else if (requestCode == RC_WAITING_ROOM) {
			// In case the waiting room UI activity is closed through code
			if (ignoreWaitingRoomUIOutput) { return; }

			if (resultCode == Activity.RESULT_OK) {
				callGodot(19, new Object[]{ }, "");
			} else if (resultCode == Activity.RESULT_CANCELED) {
				callGodot(20, new Object[]{ }, "");
			} else if (resultCode == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
				leaveRoom();
				callGodot(21, new Object[]{ }, "");
			}
		}
		else if (requestCode == RC_INVITATION_INBOX) {
			if (resultCode != Activity.RESULT_OK) {	// Canceled or some error.
				return;
			}

			Invitation invitation = intent.getExtras().getParcelable(Multiplayer.EXTRA_INVITATION);
			if (invitation != null) {
				callGodot(18, new Object[]{invitation.getInvitationId()}, "invitation id: " + invitation.getInvitationId());
			}
		}
	}

	private RoomUpdateCallback roomUpdateCallback = new RoomUpdateCallback() {
		@Override
		public void onRoomCreated(int statusCode, @Nullable Room room) {
			//Called when the client attempts to create a real-time room
			if (room != null && statusCode == GamesCallbackStatusCodes.OK){
				tempRoom = room;
				callGodot(1, new Object[]{true, room.getRoomId()}, "successfully created room with roomID: "+room.getRoomId());
			}else{
				callGodot(1, new Object[]{false, ""}, "failed to create room");
			}
		}

		@Override
		public void onJoinedRoom(int statusCode, @Nullable Room room) {
			//Called when the client attempts to join a real-time room
			if (room != null && statusCode == GamesCallbackStatusCodes.OK){
				Log.d(TAG, "Room joined with room ID: " + room.getRoomId());
				callGodot(2, new Object[]{true, room.getRoomId()}, "successfully joined room with roomID: "+room.getRoomId());
			}else{
				callGodot(2, new Object[]{false, ""}, "failed to join room");
			}
		}

		@Override
		public void onLeftRoom(int statusCode, @NonNull String roomID) {
			//Called when the client attempts to leaves the real-time room
			connectedRoom = null;
			tempRoom = null;
			callGodot(3, new Object[]{roomID}, "left room with roomID: "+roomID);
		}

		@Override
		public void onRoomConnected(int statusCode, @Nullable Room room) {
			//Called when all the participants in a real-time room are fully connected
			if (room != null && statusCode == GamesCallbackStatusCodes.OK){
				callGodot(4, new Object[]{true, room.getRoomId()}, "successfully joined room with roomID: "+room.getRoomId());
			}else{
				callGodot(4, new Object[]{false, ""}, "failed to create room");
			}
		}
	};

	private RoomStatusUpdateCallback roomStatusUpdateCallback = new RoomStatusUpdateCallback() {
		@Override
		public void onRoomConnecting(@Nullable Room room) {
			//Called when one or more participants have joined the room and have started the process of establishing peer connections.
			callGodot(5, new Object[]{ room.getRoomId() }, "roomID: " + room.getRoomId() );
		}

		@Override
		public void onRoomAutoMatching(@Nullable Room room) {
			//Called when the server has started the process of auto-matching.
			callGodot(6, new Object[]{ room.getRoomId() }, "roomID: " + room.getRoomId() );
		}

		@Override
		public void onPeerInvitedToRoom(@Nullable Room room, @NonNull List<String> participantIds) {
			//Called when one or more peers are invited to a room.
			String ids = concatenateStringList(participantIds, GodotPlayGameServices.STRING_DATA_DELIMITER);
			callGodot(7, new Object[]{ ids }, "participantIDs: " + ids );
		}

		@Override
		public void onPeerDeclined(@Nullable Room room, @NonNull List<String> participantIds) {
			//Called when one or more peers decline the invitation to a room.
			String ids = concatenateStringList(participantIds, GodotPlayGameServices.STRING_DATA_DELIMITER);
			callGodot(8, new Object[]{ ids }, "participantIDs: " + ids );
		}

		@Override
		public void onPeerJoined(@Nullable Room room, @NonNull List<String> participantIds) {
			//Called when one or more peer participants join a room.
			String ids = concatenateStringList(participantIds, GodotPlayGameServices.STRING_DATA_DELIMITER);
			callGodot(9, new Object[]{ ids }, "participantIDs: " + ids );
		}

		@Override
		public void onPeerLeft(@Nullable Room room, @NonNull List<String> participantIds) {
			//Called when one or more peer participant leave a room.
			String ids = concatenateStringList(participantIds, GodotPlayGameServices.STRING_DATA_DELIMITER);
			callGodot(10, new Object[]{ ids }, "participantIDs: " + ids );
		}

		@Override
		public void onConnectedToRoom(@Nullable final Room room) {
			//Called when the client is connected to the connected set in a room.
			Games.getPlayersClient(activity, signedInAccount).getCurrentPlayerId().addOnSuccessListener(new OnSuccessListener<String>() {
				@Override
				public void onSuccess(String playerId) {
					connectedRoom = room;
					myParticipantId = room.getParticipantId(playerId);
					callGodot(11, new Object[]{ room.getRoomId(), myParticipantId }, "roomID: " + room.getRoomId() + "myParticipantIDs: " + myParticipantId );
				}
			});
		}

		@Override
		public void onDisconnectedFromRoom(@Nullable Room room) {
			//Called when the client is disconnected from the connected set in a room.
			connectedRoom = null;
			tempRoom = null;
			callGodot(12, new Object[]{ room.getRoomId() }, "roomID: " + room.getRoomId() );
		}

		@Override
		public void onPeersConnected(@Nullable Room room, @NonNull List<String> participantIds) {
			//Called when one or more peer participants are connected to a room.
			String ids = concatenateStringList(participantIds, GodotPlayGameServices.STRING_DATA_DELIMITER);
			callGodot(13, new Object[]{ ids }, "participantIDs: " + ids );
		}

		@Override
		public void onPeersDisconnected(@Nullable Room room, @NonNull List<String> participantIds) {
			//Called when one or more peer participants are disconnected from a room.
			String ids = concatenateStringList(participantIds, GodotPlayGameServices.STRING_DATA_DELIMITER);
			callGodot(14, new Object[]{ ids }, "participantIDs: " + ids );
		}

		@Override
		public void onP2PConnected(@NonNull String participantId) {
			//Called when the client is successfully connected to a peer participant.
			callGodot(15, new Object[]{ participantId }, "participantID: " + participantId );
		}

		@Override
		public void onP2PDisconnected(@NonNull String participantId) {
			//Called when client gets disconnected from a peer participant.
			callGodot(16, new Object[]{ participantId }, "participantID: " + participantId );
		}
	};

	private OnRealTimeMessageReceivedListener messageReceivedListener = new OnRealTimeMessageReceivedListener() {
		@Override
		public void onRealTimeMessageReceived(@NonNull RealTimeMessage realTimeMessage) {
			Log.d(TAG, "receive called");
			try {
				String senderID = realTimeMessage.getSenderParticipantId();
				if (!senderID.equals(myParticipantId)){
					String data = new String(realTimeMessage.getMessageData(), "UTF-8");
					boolean isReliable = realTimeMessage.isReliable();
					callGodot(24, new Object[]{ senderID, data, isReliable }, "" );
				}
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
	};

	private String concatenateStringList(List<String> list, String delimiter){
		String lastElement = list.get(list.size()-1);
		StringBuilder builder = new StringBuilder();

		for (String element : list){
			if (element.equals(lastElement)) builder.append(element);
			else { builder.append(element); builder.append(delimiter); }
		}
		Log.d(TAG, "List concatenated: " + builder.toString());
		return builder.toString();
	}

	private String concatenateStringArrayList(ArrayList<String> list, String delimiter){
		String lastElement = list.get(list.size()-1);
		StringBuilder builder = new StringBuilder();

		for (String element : list){
			if (element.equals(lastElement)) builder.append(element);
			else { builder.append(element); builder.append(delimiter); }
		}
		Log.d(TAG, "List concatenated: " + builder.toString());
		return builder.toString();
	}

	private void sendReliableDataToParticipant(String data, final String participantID){
		if (connectedRoom != null){
			try {
				byte[] dataBytes = data.getBytes("UTF-8");
				rtmClient.sendReliableMessage(dataBytes, connectedRoom.getRoomId(), participantID,
						new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
							@Override
							public void onRealTimeMessageSent(int statusCode, int tokenId, String recipientId) {
								callGodot(23, new Object[]{ recipientId, tokenId }, "" );
							}
						})
						.addOnCompleteListener(new OnCompleteListener<Integer>() {
							@Override
							public void onComplete(@NonNull Task<Integer> task) {
								callGodot(22, new Object[]{ participantID, task.getResult() }, "" );
							}
						});
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
	}

	private void callGodot(int functionID, Object[] data, String logText){
		Log.d(TAG, GODOT_CALLBACK_FUNCTIONS[functionID] + ", " + logText);
		GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[functionID], data);
	}
}