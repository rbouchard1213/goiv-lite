package com.kamron.pogoiv;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

import timber.log.Timber;

public class PoGoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }
}
