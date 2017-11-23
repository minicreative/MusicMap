package edu.unc.tomas.musicmap;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import values.MusicListFragment;
import values.MusicMapFragment;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 8124;
    private static final String CLIENT_ID = "f797993a6d4046a697e87452ac86afe8";
    private static final String REDIRECT_URI = "musicmap://authenticationSuccess";
    public String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        chooseFragment("map");
        updateStatus("Not connected to Spotify", false);
        authenticateSpotify();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result is not cancelled & comes from the correct activity
        if (resultCode != RESULT_CANCELED && requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);

            // Display error message on unsuccessful response
            if (response.getError() != null) authenticationError();

            // Save access token on successful response
            else if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                updateStatus("Connected to Spotify!", false);
                accessToken = response.getAccessToken();
            }
        }
    }

    // Bottom Navigation listener
    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_list:
                    chooseFragment("list");
                    return true;
                case R.id.navigation_map:
                    chooseFragment("map");
                    return true;
            }
            return false;
        }

    };

    // Choose Fragment: changes the view fragment
    protected void chooseFragment(String fragmentName) {
        android.support.v4.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        switch(fragmentName) {
            case "list":
                transaction.replace(R.id.rootLayout, new MusicListFragment());
                break;
            case "map":
                transaction.replace(R.id.rootLayout, new MusicMapFragment());
                break;
            default:
                transaction.replace(R.id.rootLayout, new MusicMapFragment());
        }
        transaction.commit();
    };

    // Authentication Spotify: authenticates the current user
    protected void authenticateSpotify() {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-playback-state", "user-read-currently-playing"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
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
                Log.v("DEBUG", "trying to cancel");
                dialog.cancel();
            }
        });
        alert.show();
    }

    // Status Click: handles click on status bar
    public void statusClick(View view) {
        if (accessToken == null) authenticateSpotify();
    };

}
