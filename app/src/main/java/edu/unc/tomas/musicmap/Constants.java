package edu.unc.tomas.musicmap;

/**
 * Created by tomasroy on 11/23/17.
 */

public class Constants {
    public static final String SPOTIFY_CLIENT_ID = "5e3c9d6a7d104f828efb2a54c233f3ef";
    public static final String AUTH_REDIRECT_URI = "musicmap://authenticationSuccess";
    public static final String SPOTIFY_API = "https://api.spotify.com";
    public static final int AUTH_REQUEST_CODE = 8124;
    public static final int LOCATION_PERMISSION_CODE = 3424;
    public static final int POLL_DELAY = 10000;
    public static final String DB = "MusicMapListeningHistory";
    public static final String STATUS_BROADCAST = "edu.unc.tomas.musicmap.statusBroadcast";
    public static final String DATA_BROADCAST = "edu.unc.tomas.musicmap.dataBroadcast";
    public static final String LOCATION_BROADCAST = "edu.unc.tomas.musicmap.locationBroadcast";
}
