package edu.unc.tomas.musicmap;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import values.MusicListFragment;
import values.MusicMapFragment;

public class MainActivity extends AppCompatActivity {

    SQLiteDatabase db;
    boolean connectedToSpotify = false;

    // SETUP =======================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Setup activity
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Section action bar
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        setSupportActionBar(toolbar);

        // Setup bottom bar
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        // Make sure database exists
        db = this.openOrCreateDatabase(Constants.DB, Context.MODE_PRIVATE, null);
        String ListensColumns = "GUID INT PRIMARY KEY";
        ListensColumns += ", ID TEXT";
        ListensColumns += ", Time INT";
        ListensColumns += ", Latitude REAL";
        ListensColumns += ", Longitude REAL";
        ListensColumns += ", Name TEXT";
        ListensColumns += ", Artist TEXT";
        ListensColumns += ", Album TEXT";
        ListensColumns += ", AlbumArt TEXT";
        db.execSQL("CREATE TABLE IF NOT EXISTS Listens ("+ ListensColumns + ");");

        // Setup image loader
        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(this).build();
        ImageLoader.getInstance().init(config);

        // Ask for location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.title_location_permission)
                            .setMessage(R.string.text_location_permission)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    //Prompt the user once explanation has been shown
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                            Constants.LOCATION_PERMISSION_CODE);
                                }
                            })
                            .create()
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            Constants.LOCATION_PERMISSION_CODE);
                }
        }

        // Setup status receiver
        IntentFilter statusFilter = new IntentFilter(Constants.STATUS_BROADCAST);
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, statusFilter);

        // Choose default fragment
        chooseFragment("map", false);

        // Authenticate Spotify
        updateStatus("Not connected to Spotify", false);
        authenticateSpotify();
    }

    // LISTENERS ===================================================================================

    // Setup Spotify Poll Receiver
    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Get data from intent
            String message = intent.getStringExtra("message");
            Boolean error = intent.getBooleanExtra("data", false);

            // Update status
            updateStatus(message, error);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Handle Spotify Authentication results
        if (requestCode == Constants.AUTH_REQUEST_CODE) {
            if (resultCode != RESULT_CANCELED) {
                AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);

                // Display error message on unsuccessful response
                if (response.getError() != null) authenticationError();

                    // Save access token on successful response
                else if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                    authenticationSuccess(response.getAccessToken());
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // Action Bar Listener
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                emptyListens();
                return true;
        }
        return false;
    }

    // Bottom Navigation listener
    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_list:
                    chooseFragment("list", false);
                    return true;
                case R.id.navigation_map:
                    chooseFragment("map", false);
                    return true;
            }
            return false;
        }

    };

    // VIEW FUNCTIONS ==============================================================================

    // Choose Fragment: changes the view fragment
    private MusicMapFragment mapFragment;
    private MusicListFragment listFragment;
    protected void chooseFragment(String fragmentName, boolean reload) {

        if (reload) {
            android.support.v4.app.FragmentTransaction clearTransaction = getSupportFragmentManager().beginTransaction();
            clearTransaction.remove(mapFragment);
            clearTransaction.remove(listFragment);
            mapFragment = null;
            listFragment = null;
            clearTransaction.commit();
        }

        android.support.v4.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        switch(fragmentName) {
            case "list":
                if (listFragment == null) {
                    listFragment = new MusicListFragment();
                    transaction.add(R.id.rootLayout, listFragment);
                }
                if (mapFragment != null) transaction.hide(mapFragment);
                transaction.show(listFragment);
                break;
            case "map":
                if (mapFragment == null) {
                    mapFragment = new MusicMapFragment();
                    transaction.add(R.id.rootLayout, mapFragment);
                }
                if (listFragment != null) transaction.hide(listFragment);
                transaction.show(mapFragment);
                break;
        }
        transaction.commit();
    };

    // Authentication Spotify: authenticates the current user
    protected void authenticateSpotify() {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(Constants.SPOTIFY_CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                Constants.AUTH_REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-playback-state", "user-read-currently-playing"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, Constants.AUTH_REQUEST_CODE, request);
    }

    // Update Status: updates status background and message
    public void updateStatus(String message, Boolean error) {

        // Get view elements
        TextView statusText = (TextView) findViewById(R.id.statusText);
        View status = (View) findViewById(R.id.status);

        // Set message
        if (statusText != null) statusText.setText(message);
        else statusText.setText("");

        // Set background color
        if (error) status.setBackgroundResource(R.color.error);
        else status.setBackgroundResource(R.color.gray);
    };

    // Authentication Error: handles an authentication error
    public void authenticationError() {

        updateStatus("Could not connect to Spotfy", true);

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Failed to connect to Spotify");
        alert.setMessage("Please ensure you have a working internet connection and then try again");
        alert.setCancelable(true);
        alert.setPositiveButton("Retry", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                authenticateSpotify();
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.cancel();
            }
        });
        alert.show();
    }

    // Authentication Success: handles successful authentication
    public void authenticationSuccess(String accessToken) {

        // Restart service
        Intent shopifyPollIntent = new Intent(this, SpotifyPollService.class);
        shopifyPollIntent.putExtra("accessToken", accessToken);
        this.stopService(shopifyPollIntent);
        this.startService(shopifyPollIntent);

        // Update status
        connectedToSpotify = true;
        updateStatus("Connected to Spotify!", false);
    };

    // Status Click: handles click on status bar
    public void statusClick(View view) {
        if (!connectedToSpotify) authenticateSpotify();
    };

    private void emptyListens () {
        db.execSQL("DELETE FROM Listens;");
        android.support.v4.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        chooseFragment("map", true);
    }

}
