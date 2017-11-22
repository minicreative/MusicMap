package edu.unc.tomas.musicmap;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import values.MusicListFragment;
import values.MusicMapFragment;

public class NavigationActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        chooseFragment("map");
    }

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

}
