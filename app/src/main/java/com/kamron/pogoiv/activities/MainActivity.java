package com.kamron.pogoiv.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import androidx.appcompat.app.AlertDialog;
import android.widget.NumberPicker;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.kamron.pogoiv.GoIVSettings;
import com.kamron.pogoiv.Pokefly;
import com.kamron.pogoiv.R;
import com.kamron.pogoiv.databinding.ActivityMainBinding;
import com.kamron.pogoiv.scanlogic.Data;

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
    private DisplayMetrics rawDisplayMetrics;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                updateUi();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    runStartButtonLogic(); // Overlay granted: continue starting.
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Nothing depends on the result: if denied, the system just
                // hides the running notification and its buttons.
            });

    private final ActivityResultLauncher<Intent> screenCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    try {
                        // Re-measure to be safe (defensive against null/stale metrics)
                        measureDisplay();
                        // Hand the consent straight to Pokefly: on Android 14+ the
                        // MediaProjection itself may only be created once the
                        // mediaProjection foreground service is running, so the
                        // service creates it (and the ScreenGrabber) itself.
                        Intent startIntent = Pokefly.createStartIntent(this,
                                GoIVSettings.getInstance(this).getLevel(),
                                result.getResultCode(), result.getData(),
                                rawDisplayMetrics.widthPixels, rawDisplayMetrics.heightPixels,
                                rawDisplayMetrics.densityDpi);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(startIntent);
                        } else {
                            startService(startIntent);
                        }
                    } catch (Exception e) {
                        android.widget.Toast.makeText(this,
                                "Startup failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show();
                        updateUi();
                    }
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
        binding.trainerLevelButton.setOnClickListener(v -> showTrainerLevelPicker(false));
        updateTrainerLevelButton();

        // Android 13+: without POST_NOTIFICATIONS the running notification stays
        // hidden. Ask here, on open — asking inside startGoIV() made this popup
        // collide with the screen-capture consent popup and cancel it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

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
            // The trainer level anchors every scan's level estimate and was never
            // asked for in the lite build (scans silently assumed level 1). Ask on
            // the first start, like the classic app did.
            if (!GoIVSettings.getInstance(this).isLevelSet()) {
                showTrainerLevelPicker(true);
            } else {
                startGoIV();
            }
        } else {
            stopGoIV();
        }
        updateUi();
    }

    private void updateTrainerLevelButton() {
        GoIVSettings settings = GoIVSettings.getInstance(this);
        if (settings.isLevelSet()) {
            binding.trainerLevelButton.setText(
                    getString(R.string.trainer_level_button, settings.getLevel()));
        } else {
            binding.trainerLevelButton.setText(R.string.trainer_level_unset);
        }
    }

    private void showTrainerLevelPicker(boolean startAfter) {
        GoIVSettings settings = GoIVSettings.getInstance(this);
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(Data.MINIMUM_TRAINER_LEVEL);
        picker.setMaxValue(Data.MAXIMUM_TRAINER_LEVEL);
        picker.setValue(settings.getLevel());
        picker.setWrapSelectorWheel(false);
        new AlertDialog.Builder(this)
                .setTitle(R.string.trainer_info)
                .setMessage(R.string.trainer_level_explain)
                .setView(picker)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    settings.setLevel(picker.getValue());
                    updateTrainerLevelButton();
                    if (startAfter) {
                        startGoIV();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startGoIV() {
        binding.startStopButton.setText(R.string.main_starting);
        binding.startStopButton.setEnabled(false);

        // Android 14+: a mediaProjection foreground service may only be started
        // AFTER the user grants screen-capture consent, so request consent first.
        // The service starts from the consent result handler once granted.
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent());
    }

    private void stopGoIV() {
        startService(Pokefly.createStopIntent(this));
        // Pokefly owns the ScreenGrabber now and exits it in onDestroy.
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
