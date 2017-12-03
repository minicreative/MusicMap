package edu.unc.tomas.musicmap;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SpotifyPollService extends Service {

    // VARIABLES ===================================================================================

    // Handler & runnable variables
    private Handler handler;
    private Runnable runnable;

    // Request Variables
    private Cache cache;
    private Network network;
    private RequestQueue queue;

    // Location variables
    private LocationManager locationManager = null;
    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 10f;
    Location lastLocation;

    // Database Variables
    SQLiteDatabase db;

    // Functionality variables
    private String accessToken;
    private Boolean wasLastListening = false;
    private String lastPlayedSongID = "";

    // MAIN SERVICE FUNCTIONS ======================================================================

    // Constructor
    public SpotifyPollService() {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Set access token
        if (intent != null) accessToken = intent.getStringExtra("accessToken");

        // Initialize location updates
        initializeLocationUpdates();

        // Initialize database
        db = this.openOrCreateDatabase(Constants.DB, Context.MODE_PRIVATE, null);

        // Start polling handler
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                poll();
                handler.postDelayed(this, Constants.POLL_DELAY);
            }
        };
        handler.postDelayed(runnable, 100);

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // POLLING FUNCTIONS ===========================================================================

    // Poll: Checks Spotify for status of music player
    private void poll() {

        if (accessToken == null) return;

        // Setup the request URL
        String url = Constants.SPOTIFY_API + "/v1/me/player/currently-playing";

        // Start the request queue
        cache = new DiskBasedCache(this.getCacheDir(), 1024 * 1024);
        network = new BasicNetwork(new HurlStack());
        queue = new RequestQueue(cache, network);
        queue.start();

        // Make request to Shopify API
        JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        handleAPIResponse(response);
                        queue.stop();
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.getMessage() != null) Log.v("REQUEST ERROR", error.getMessage());
                        if (error.networkResponse != null) {
                            if (error.networkResponse.data != null) {
                                Log.v("REQUEST ERROR", new String(error.networkResponse.data));
                            }
                        }
                        statusBroadcast("Could not get data from Spotify", true);
                        queue.stop();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Authorization", "Bearer " + accessToken);
                params.put("Content-Type", "application/json");
                return params;
            }
        };

        queue.add(request);
    }

    // Handle API Response: handles response from Spotify API
    private void handleAPIResponse(JSONObject response) {
        // Handle response in JSON Exception catch block
        try {

            // If user is playing a song...
            if (response.getBoolean("is_playing")) {

                // Update listening boolean
                wasLastListening = true;

                // Get song object
                JSONObject songObject = response.getJSONObject("item");

                // Get currently playing song
                String currentlyPlayingSongID = songObject.getString("id");

                // If a new song is playing...
                if (!currentlyPlayingSongID.equals(lastPlayedSongID)) {

                    // Set last played song
                    lastPlayedSongID = currentlyPlayingSongID;

                    // Get JSON from song object
                    JSONObject albumObject = songObject.getJSONObject("album");
                    JSONArray albumArtArray = albumObject.getJSONArray("images");
                    JSONArray artistsArray = songObject.getJSONArray("artists");

                    // Initialize song information
                    String id = songObject.getString("id");
                    String name = songObject.getString("name");
                    String album = albumObject.getString("name");
                    String artist = "";
                    for (int i = 0; i < artistsArray.length(); i++) {
                        JSONObject artistObject = artistsArray.getJSONObject(i);
                        artist += artistObject.getString("name");
                        if (i < artistsArray.length() - 1) {
                            artist += ", ";
                        }
                    }
                    String albumArt = "";
                    int minAlbumArtWidth = 10000000;
                    for (int i = 0; i < albumArtArray.length(); i++) {
                        JSONObject albumArtObject = albumArtArray.getJSONObject(i);
                        if (albumArtObject.getInt("width") < minAlbumArtWidth) {
                            albumArt = albumArtObject.getString("url");
                        }
                    }

                    // Add song to database
                    addTrackToDatabase(id, name, artist, album, albumArt);

                    // Broadcast status
                    String trackStatus = "Now Playing: " + name + " - " + artist;
                    statusBroadcast(trackStatus, false);
                }
            }

            // If user is not playing a song
            else {

                // If user was last playing a song, update state and broadcast
                if (wasLastListening) {
                    statusBroadcast("No song is playing", false);
                    wasLastListening = false;
                }
            }

        } catch (JSONException e) {
            Log.v("JSON", "JSON Error");
        }
    }

    // DATABASE FUNCTIONS ==========================================================================

    // Add Track to Database: adds track information to the database
    private void addTrackToDatabase(String id, String name, String artist, String album, String albumArt) {

        // Get time
        Long time = new Date().getTime() / 1000;

        // Get latitude and longitude
        Double latitude = new Double(0);
        Double longitude = new Double(0);
        if (lastLocation != null) {
            latitude = lastLocation.getLatitude();
            longitude = lastLocation.getLongitude();
        }

        // Make new GUID
        Cursor listenCountCursor = db.rawQuery("SELECT ID FROM Listens", null);
        Integer GUID = listenCountCursor.getCount();

        // Insert track into database
        String insertContent = GUID.toString();
        insertContent += ", '" + id + "'";
        insertContent += ", " + time.toString();
        insertContent += ", " + latitude.toString();
        insertContent += ", " + longitude.toString();
        insertContent += ", '" + prepareForSQL(name) + "'";
        insertContent += ", '" + prepareForSQL(artist) + "'";
        insertContent += ", '" + prepareForSQL(album) + "'";
        insertContent += ", '" + albumArt + "'";
        String insertQuery = "INSERT INTO Listens VALUES (" + insertContent + ");";
        db.execSQL(insertQuery);

        // Broadcast data update
        dataBroadcast(GUID);
    }

    // Prepare For SQL: ensures String won't break SQL query
    private String prepareForSQL(String string) {
        string = string.replace("'", "''");
        return string;
    }

    // LOCATION FUNCTIONS ==========================================================================

    // Location Listener: listens to updates from Android location
    private class LocationListener implements android.location.LocationListener {
        public LocationListener() {}
        @Override
        public void onLocationChanged(Location location) {
            lastLocation = location;
            locationBroadcast();
        }
        @Override
        public void onProviderDisabled(String provider) {}
        @Override
        public void onProviderEnabled(String provider) {}
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    private void initializeLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, new LocationListener());
        }

    }

    // BROADCASTS ==================================================================================

    // Status Broadcast: notifies listeners to update status
    private void statusBroadcast(String message, Boolean error) {
        Intent statusIntent = new Intent(Constants.STATUS_BROADCAST);
        statusIntent.putExtra("message", message);
        statusIntent.putExtra("error", error);
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent);
    }

    // Data Broadcast: notifies listeners to updates with database
    private void dataBroadcast(Integer GUID) {
        Intent dataIntent = new Intent(Constants.DATA_BROADCAST);
        dataIntent.putExtra("GUID", GUID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(dataIntent);
    }

    // Location Broadcast: notifies listeners with updated location
    private void locationBroadcast() {
        Intent locationIntent = new Intent(Constants.LOCATION_BROADCAST);
        locationIntent.putExtra("latitude", lastLocation.getLatitude());
        locationIntent.putExtra("longitude", lastLocation.getLongitude());
        LocalBroadcastManager.getInstance(this).sendBroadcast(locationIntent);
    }
}