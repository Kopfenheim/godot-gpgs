# Google Play Game Services (Android) for Godot Game Engine
This is a Google Play Game Services module for the Godot Game Engine. This module is meant to be used for games deployed to an Android device. This module allows you to use nearly all the features provided by the Google Play Games Service API for Android including:
- Log-in with Google
- Information about the signed in player (including their profile pictures)
- Achievements
- Leaderboards
- Saved Games (Snapshots)
- Real Time Multiplayer
- Device network status

This module uses Google Play Services' powerful Tasks API to execute various operations asynchronously. To handle the results from these asynchronous operations, a lot of relevant callback functions are also provided and can be used in GDScript.

**NOTE:** This module is compatible with Godot 3.0 but has not been tested with the Mono version. So, using this module for developing Godot games using C# could lead to unforeseen errors.
