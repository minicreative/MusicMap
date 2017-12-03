package values;

import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

import java.text.SimpleDateFormat;
import java.util.Date;

import edu.unc.tomas.musicmap.R;

public class MusicListItem extends LinearLayout {

    private Context context;
    private TextView titleView;
    private TextView subtitleView;
    private TextView timeView;
    private ImageView imageView;

    public MusicListItem(Context context) {
        super(context);
        this.context = context;
        inflate(context, R.layout.music_list_item, this);
        titleView = (TextView) findViewById(R.id.item_title);
        subtitleView = (TextView) findViewById(R.id.item_subtitle);
        timeView = (TextView) findViewById(R.id.item_time);
        imageView = (ImageView) findViewById(R.id.item_image);
    }

    public void populate (String name, String artist, String album, String albumArt, Integer time) {

        // Set basic text
        titleView.setText(name);
        subtitleView.setText(artist + " - " + album);

        // Set text
        SimpleDateFormat format = new SimpleDateFormat("MMMM d h:mma");
        String timeString = format.format(new Date((long)time*1000));
        timeView.setText(timeString);

        // Load image with UIL
        ImageLoader imageLoader = ImageLoader.getInstance();
        imageLoader.displayImage(albumArt, imageView);
    }

}
