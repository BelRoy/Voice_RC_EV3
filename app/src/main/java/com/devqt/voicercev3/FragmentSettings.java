package com.devqt.voicercev3;

import android.os.Bundle;
import android.preference.PreferenceFragment;

public class FragmentSettings extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.preferences);
    }

}
