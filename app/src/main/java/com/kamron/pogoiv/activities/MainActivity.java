package com.kamron.pogoiv.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.kamron.pogoiv.GoIVSettings;
import com.kamron.pogoiv.Pokefly;
import com.kamron.pogoiv.R;
import com.kamron.pogoiv.ScreenGrabber;
import com.kamron.pogoiv.databinding.ActivityMainBinding;

import timber.log.Timber;

/**
 * goiv-lite setup activity: gates the overlay + screen-capture permissions,
 * then starts/stops the Pokefly overlay service.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    public static final String ACTION_START_POKEFLY = "com.kamron.pogoiv.ACTION_START_POKEFLY";
    public static final String ACTION_RESTART_POKEFLY = "com.kamron.pogoiv.ACTION_RESTART_POKEFLY";

    private ActivityMainBinding binding;
    private ScreenGrabber screen;
    private DisplayMetrics rawDisplayMetrics;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                updateUi();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    runStartButtonLogic(); // Overlay granted: continue starting.
                }
            });

    private final ActivityResultLauncher<Intent> screenCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    MediaProjectionManager projectionManager =
                            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    MediaProjection projection = projectionManager.getMediaProjection(
                            result.getResultCode(), result.getData());
                    screen = ScreenGrabber.init(projection, rawDisplayMetrics);
                    startService(Pokefly.createBeginIntent(this));
                } else {
                    updateUi();
                }
            });

    private final BroadcastReceiver pokeflyStateChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Timber.tag(TAG);

        binding.startStopButton.setOnClickListener(v -> runStartButtonLogic());

        measureDisplay();
        runActionOnIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(pokeflyStateChanged,
                new IntentFilter(Pokefly.ACTION_UPDATE_UI));
        updateUi();
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pokeflyStateChanged);
        super.onPause();
    }

    @Override
    protected final void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        runActionOnIntent(intent);
    }

    private void runActionOnIntent(Intent intent) {
        if (intent != null && ACTION_START_POKEFLY.equals(intent.getAction()) && !Pokefly.isRunning()) {
            runStartButtonLogic();
        }
    }

    /** Measures the display so the screen grabber captures at full resolution. */
    private void measureDisplay() {
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        rawDisplayMetrics = new DisplayMetrics();
        Display display = windowManager.getDefaultDisplay();
        display.getRealMetrics(rawDisplayMetrics);
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    public void runStartButtonLogic() {
        if (!hasOverlayPermission()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else if (!Pokefly.isRunning()) {
            startGoIV();
        } else {
            stopGoIV();
        }
        updateUi();
    }

    private void startGoIV() {
        binding.startStopButton.setText(R.string.main_starting);
        binding.startStopButton.setEnabled(false);

        // Start the Pokefly service, then request screen capture; Pokefly begins
        // once the MediaProjection is handed over via createBeginIntent.
        Intent intent = Pokefly.createStartIntent(this, GoIVSettings.getInstance(this).getLevel());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent());
    }

    private void stopGoIV() {
        startService(Pokefly.createStopIntent(this));
        if (screen != null) {
            screen.exit();
            screen = null;
        }
        updateUi();
    }

    private void updateUi() {
        boolean running = Pokefly.isRunning();
        binding.startStopButton.setEnabled(true);
        if (!hasOverlayPermission()) {
            binding.startStopButton.setText(R.string.main_permission);
            binding.statusText.setText(R.string.main_permission);
        } else if (running) {
            binding.startStopButton.setText(R.string.main_stop);
            binding.statusText.setText(R.string.main_stop);
        } else {
            binding.startStopButton.setText(R.string.main_start);
            binding.statusText.setText(R.string.main_start);
        }
    }
}
