package edu.unc.tomas.musicmap;

import android.Manifest;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
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
import java.util.Timer;
import java.util.TimerTask;

public class SpotifyPollService extends IntentService {

    // Timer variables
    private Timer timer;
    private TimerTask timerTask;
    private Handler handler = new Handler();

    // Request Variables
    private Cache cache;
    private Network network;
    private RequestQueue queue;

    // Location variables
    LocationManager locationManager;
    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {

        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };

    // Database Variables
    SQLiteDatabase db;

    // Functionality variables
    private String accessToken;
    private Boolean wasLastListening = false;
    private String lastPlayedSongID = "";

    public SpotifyPollService() {
        super("SpotifyPollService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        accessToken = intent.getStringExtra("accessToken");
        db = this.openOrCreateDatabase(Constants.DB, Context.MODE_PRIVATE, null);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        startPolling();
    }

    private void handleAPIResponse(JSONObject response) {
        // Handle response in JSON Exception catch block
        try {

            // If user is playing a song...
            if (response.getBoolean("is_playing")) {

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
        ;
    }

    // Add Track to Database: adds track information to the database
    private void addTrackToDatabase(String id, String name, String artist, String album, String albumArt) {

        // Get time
        Long time = new Date().getTime() / 1000;

        // Get latitude and longitude
        Double latitude = new Double(0);
        Double longitude = new Double(0);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            } else {
                Log.v("LOCATION", "has permission but no location!");
            }
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
        insertContent += ", '" + name + "'";
        insertContent += ", '" + artist + "'";
        insertContent += ", '" + album + "'";
        insertContent += ", '" + albumArt + "'";
        String insertQuery = "INSERT INTO Listens VALUES (" + insertContent + ");";
        db.execSQL(insertQuery);

        Log.v("DATABASE", insertContent);

        // Broadcast data update
        dataBroadcast();
    };

    // Poll: Checks Spotify for status of music player
    private void poll () {

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
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Authorization", "Bearer "+accessToken);
                params.put("Content-Type", "application/json");
                return params;
            }
        };

        queue.add(request);
    };

    // Start Polling: starts polling timer
    private void startPolling() {
        timer = new Timer();
        timerTask = new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        poll();
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,500.0f, locationListener);
                    }
                });
            }
        };
        timer.schedule(timerTask, 0, Constants.POLL_DELAY);
    }

    // Stop Polling: stops polling timer
    private void stopPolling() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
    }

    // Status Broadcast: notifies listeners to update status
    private void statusBroadcast (String message, Boolean error) {
        Intent statusIntent = new Intent(Constants.STATUS_BROADCAST);
        statusIntent.putExtra("message", message);
        statusIntent.putExtra("error", error);
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent);
    }

    // Data Broadcast: notifies listeners to updates with database
    private void dataBroadcast () {
        Intent dataIntent = new Intent(Constants.DATA_BROADCAST);
        LocalBroadcastManager.getInstance(this).sendBroadcast(dataIntent);
    };
}
