package values;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import edu.unc.tomas.musicmap.Constants;
import edu.unc.tomas.musicmap.R;

public class MusicListFragment extends Fragment {

    SQLiteDatabase db;
    LinearLayout list;
    final String baseQuery = "SELECT GUID, ID, Time, Latitude, Longitude, Name, Artist, Album, AlbumArt FROM Listens";

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Get database
        db = getActivity().openOrCreateDatabase(Constants.DB, Context.MODE_PRIVATE, null);

        // Initialize list
        list = (LinearLayout) getView().findViewById(R.id.musicList);

        // Create full table initially
        createTableFromDatabase();

        // Setup data receiver
        IntentFilter dataFilter = new IntentFilter(Constants.DATA_BROADCAST);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(dataReceiver, dataFilter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_music_list, container, false);
    }

    // Setup Spotify Poll Receiver
    private BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Integer GUID = intent.getIntExtra("GUID", 0);
            if (db != null) {
                String itemQuery = baseQuery+" WHERE GUID = "+GUID.toString();
                Cursor cursor = db.rawQuery(itemQuery, null);
                if (cursor.getCount() > 0) {
                    createItemWithCursor(cursor, 0, true);
                }
            }
        }
    };

    private void createTableFromDatabase () {

        // Initialize and make query
        String listQuery = baseQuery+" ORDER BY Time DESC";
        Cursor cursor = db.rawQuery(listQuery, null);

        // Iterate through query
        for (int i=0; i < cursor.getCount(); i++) {
            createItemWithCursor(cursor, i, false);
        }

    }

    private void createItemWithCursor (Cursor cursor, int index, boolean addToTop) {

        // Get current row
        cursor.moveToPosition(index);

        // Initialize variables
        String name = cursor.getString(cursor.getColumnIndex("Name"));
        String artist = cursor.getString(cursor.getColumnIndex("Artist"));
        String album = cursor.getString(cursor.getColumnIndex("Album"));
        String albumArt = cursor.getString(cursor.getColumnIndex("AlbumArt"));
        int time = cursor.getInt(cursor.getColumnIndex("Time"));

        // Make a text view if activity is ready
        if (getActivity() != null) {
            MusicListItem item = new MusicListItem(getActivity());
            item.populate(name, artist, album, albumArt, time);
            if (addToTop) list.addView(item, 0);
            else list.addView(item);
        }

    };

}
