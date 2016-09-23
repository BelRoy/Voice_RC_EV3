package com.devqt.voicercev3;


import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

public class SettingsActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle saveInstabceState){
        super.onCreate(saveInstabceState);
        setContentView(R.layout.activity_settings);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new FragmentSettings())
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){

        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if (id == R.id.setting){

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
