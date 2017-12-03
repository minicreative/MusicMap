package values;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import java.text.SimpleDateFormat;
import java.util.Date;

import edu.unc.tomas.musicmap.Constants;
import edu.unc.tomas.musicmap.R;

public class MusicMapFragment extends Fragment implements OnMapReadyCallback {

    ImageLoader imageLoader;
    SQLiteDatabase db;
    MapView mapView;
    GoogleMap map;
    boolean currentLocationInitialized = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Get database
        db = getActivity().openOrCreateDatabase(Constants.DB, Context.MODE_PRIVATE, null);

        // Setup image loader
        imageLoader = ImageLoader.getInstance();

        // Setup location broadcast receiver
        IntentFilter locationFilter = new IntentFilter(Constants.LOCATION_BROADCAST);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(locationReceiver, locationFilter);

        // Setup data receiver
        IntentFilter dataFilter = new IntentFilter(Constants.DATA_BROADCAST);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(dataReceiver, dataFilter);

        // Setup root view
        View rootView = inflater.inflate(R.layout.fragment_music_map, container, false);

        // Setup map view
        mapView = rootView.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.onResume();
        try {
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        mapView.getMapAsync(this);

        return rootView;
    }

    public void onMapReady(GoogleMap googleMap) {

        // Provide map to fragment
        map = googleMap;

        // Show a 'my location' button
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
        }

        // Set map style
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(), R.raw.style_json));

        // Print all items
        createAllItemsInDatabase();
    }

    // Adds markers to the map based on bounds
    private void createAllItemsInDatabase() {

        // Initialize and make query (load asc so most recent is on top)
        String listQuery = Constants.LISTEN_QUERY+" ORDER BY Time ASC";
        Cursor cursor = db.rawQuery(listQuery, null);

        // Iterate through query
        for (int i=0; i < cursor.getCount(); i++) {
            createItemWithCursor(cursor, i);
        }

    };

    // Adds a single listen to the map based on bounds
    private void createItemWithCursor (Cursor cursor, int index) {

        // Get current row
        cursor.moveToPosition(index);

        // Initialize position
        Double latitude = cursor.getDouble(cursor.getColumnIndex("Latitude"));
        Double longitude = cursor.getDouble(cursor.getColumnIndex("Longitude"));
        final LatLng position = new LatLng(latitude, longitude);

        // Initialize song information
        final String name = cursor.getString(cursor.getColumnIndex("Name"));
        final String artist = cursor.getString(cursor.getColumnIndex("Artist"));
        final String album = cursor.getString(cursor.getColumnIndex("Album"));
        String albumArt = cursor.getString(cursor.getColumnIndex("AlbumArt"));

        // Get and format time
        int time = cursor.getInt(cursor.getColumnIndex("Time"));
        SimpleDateFormat format = new SimpleDateFormat("MMM d h:mma");
        final String timeString = format.format(new Date((long)time*1000));

        // Initialize z-index using database ID
        final Float zIndex = new Float(cursor.getInt(cursor.getColumnIndex("GUID")));

        // Add marker to map if location is not null (0, 0)
        if (Math.abs(latitude) > 0 && Math.abs(longitude) > 0) {

            // Create new imageLoader callback
            imageLoader.loadImage(albumArt, new SimpleImageLoadingListener() {
                @Override
                public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {

                    // Create options
                    MarkerOptions options = new MarkerOptions();

                    // Add position, title and snippet
                    options.position(position);
                    options.title(name);
                    options.snippet(artist + " - " + album + ", " + timeString);
                    options.zIndex(zIndex);

                    // Add icon
                    BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(loadedImage);
                    options.icon(bitmapDescriptor);

                    // Add marker to map
                    map.addMarker(options);
                }
            });
        }
    };

    // Setup Location Receiver
    private BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Zoom to current location initially
            if (!currentLocationInitialized && map != null) {
                Double latitude = intent.getDoubleExtra("latitude", 0);
                Double longitude = intent.getDoubleExtra("longitude", 0);
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 17);
                map.animateCamera(cameraUpdate);
                currentLocationInitialized = true;
            }
        }
    };

    // Setup Spotify Poll Receiver
    private BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Integer GUID = intent.getIntExtra("GUID", 0);
            if (map != null && db != null) {
                String itemQuery = Constants.LISTEN_QUERY+" WHERE GUID = "+GUID.toString();
                Cursor cursor = db.rawQuery(itemQuery, null);
                if (cursor.getCount() > 0) {
                    createItemWithCursor(cursor, 0);
                }
            }
        }
    };
}
