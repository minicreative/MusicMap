package edu.unc.tomas.musicmap;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class LocationService extends Service {

    public LocationService() {
    }

    private LocationManager locationManager = null;
    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 10f;
    Location lastLocation;

    private class LocationListener implements android.location.LocationListener {

        public LocationListener(String provider) {
            lastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            lastLocation.set(location);
            locationBroadcast();
        }

        @Override
        public void onProviderDisabled(String provider) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    LocationListener[] locationListeners = new LocationListener[] {
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        initializeLocationManager();
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    locationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i("LocationService", "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d("LocationService", "network provider does not exist, " + ex.getMessage());
        }
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    locationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i("LocationService", "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d("LocationService", "gps provider does not exist " + ex.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            for (int i = 0; i < locationListeners.length; i++) {
                try {
                    locationManager.removeUpdates(locationListeners[i]);
                } catch (Exception ex) {
                    Log.i("LocationService", "fail to remove location listeners, ignore", ex);
                }
            }
        }
    }

    private void initializeLocationManager() {
        if (locationManager == null) {
            locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    // Location Broadcast: notifies listeners with updated location
    private void locationBroadcast () {
        Intent locationIntent = new Intent(Constants.LOCATION_BROADCAST);
        locationIntent.putExtra("latitude", lastLocation.getLatitude());
        locationIntent.putExtra("longitude", lastLocation.getLongitude());
        LocalBroadcastManager.getInstance(this).sendBroadcast(locationIntent);
    };
}
