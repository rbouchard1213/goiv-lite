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

        // Global crash catcher: show the actual error in a Toast for debugging
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String msg = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            StackTraceElement[] stack = throwable.getStackTrace();
            String location = stack.length > 0 ? " at " + stack[0].getClassName() + "." + stack[0].getMethodName() + ":" + stack[0].getLineNumber() : "";
            String fullMsg = "CRASH: " + msg + location;
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(() -> android.widget.Toast.makeText(getApplicationContext(), fullMsg, android.widget.Toast.LENGTH_LONG).show());
            // Give the Toast time to show
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }
}
