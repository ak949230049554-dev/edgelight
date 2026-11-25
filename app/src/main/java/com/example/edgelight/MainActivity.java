package com.example.edgelight;

import android.Manifest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.MotionEvent;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final int CURRENT_VERSION = 3;
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/ak949230049554-dev/edgelight/main/update.json";

    private Button btnPermission, btnToggle;
    private TextView statusText, updateRedDot;
    private LinearLayout btnTopUpdate;

    private int selectedColor = Color.RED;
    private boolean isMultiColor = true;

    private boolean isUpdateAvailable = false;
    private int latestVersionFound = 1;
    private String updateTitle = "Naya Update Available Hai!";
    private String updateMessage = "Naye features ke liye app update karein.";
    private String updateDownloadUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        btnPermission = findViewById(R.id.btnPermission);
        btnToggle = findViewById(R.id.btnToggle);
        statusText = findViewById(R.id.statusText);
        btnTopUpdate = findViewById(R.id.btnTopUpdate);
        updateRedDot = findViewById(R.id.updateRedDot);

        btnTopUpdate.setOnClickListener(v -> {
            if (isUpdateAvailable) {
                showUpdateDialog(latestVersionFound, updateTitle, updateMessage, updateDownloadUrl);
            } else {
                Toast.makeText(this, "App pehle se updated hai! Koi naya update nahi hai.", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnRed).setOnClickListener(v -> setLightColor(Color.RED, false, "Red"));
        findViewById(R.id.btnGreen).setOnClickListener(v -> setLightColor(Color.GREEN, false, "Green"));
        findViewById(R.id.btnBlue).setOnClickListener(v -> setLightColor(Color.parseColor("#0066FF"), false, "Blue"));
        findViewById(R.id.btnYellow).setOnClickListener(v -> setLightColor(Color.YELLOW, false, "Yellow"));
        findViewById(R.id.btnCyan).setOnClickListener(v -> setLightColor(Color.CYAN, false, "Cyan"));
        findViewById(R.id.btnPink).setOnClickListener(v -> setLightColor(Color.parseColor("#FF00CC"), false, "Pink"));
        findViewById(R.id.btnMulti).setOnClickListener(v -> setLightColor(Color.RED, true, "Sab Rang (Multi)"));

        btnPermission.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        btnToggle.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Pehle Permission ON karein!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }

            if (EdgeLightService.isRunning) {
                Intent stopIntent = new Intent(this, EdgeLightService.class);
                stopService(stopIntent);
                Toast.makeText(this, "Light band ho gayi aur app clear ho gaya", Toast.LENGTH_SHORT).show();
                btnToggle.postDelayed(this::updateUI, 200);
            } else {
                startEdgeService();
                btnToggle.postDelayed(this::updateUI, 200);
            }
        });

        checkForUpdate();
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                URL url = new URL(UPDATE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    int latestVersion = json.optInt("version", 1);
                    String title = json.optString("title", "Naya Update Available Hai!");
                    String message = json.optString("message", "Naye features ke liye app update karein.");
                    String downloadUrl = json.optString("download_url", "");

                    if (latestVersion > CURRENT_VERSION) {
                        isUpdateAvailable = true;
                        latestVersionFound = latestVersion;
                        updateTitle = title;
                        updateMessage = message;
                        updateDownloadUrl = downloadUrl;

                        SharedPreferences prefs = getSharedPreferences("EdgeLightPrefs", MODE_PRIVATE);
                        int skippedVersion = prefs.getInt("skipped_version", 0);

                        runOnUiThread(() -> {
                            // Upar Update button par Laal Bindu dikhao
                            updateRedDot.setVisibility(View.VISIBLE);

                            // Agar user ne skip nahi kiya hai, to popup aur notification bhi dikhao
                            if (latestVersion != skippedVersion) {
                                showUpdateDialog(latestVersion, title, message, downloadUrl);
                                showUpdateNotification(title, message, downloadUrl);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void showUpdateDialog(int latestVersion, String title, String message, String downloadUrl) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("Update Karein", (dialog, which) -> openDownloadLink(downloadUrl))
                .setNeutralButton("Baad Me", null)
                .setNegativeButton("Hata Dein (Skip)", (dialog, which) -> {
                    SharedPreferences prefs = getSharedPreferences("EdgeLightPrefs", MODE_PRIVATE);
                    prefs.edit().putInt("skipped_version", latestVersion).apply();
                    Toast.makeText(this, "Popup hata diya gaya. Baad me upar Update se kar sakte hain.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showUpdateNotification(String title, String message, String downloadUrl) {
        String channelId = "edge_update_channel";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "App Updates", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 2, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        manager.notify(2, notification);
    }

    private void openDownloadLink(String url) {
        if (url != null && !url.isEmpty()) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        }
    }

    private void setLightColor(int color, boolean multi, String name) {
        selectedColor = color;
        isMultiColor = multi;
        Toast.makeText(this, "Selected: " + name, Toast.LENGTH_SHORT).show();

        if (EdgeLightService.isRunning) {
            startEdgeService();
        }
    }

    private void startEdgeService() {
        Intent serviceIntent = new Intent(this, EdgeLightService.class);
        serviceIntent.putExtra("COLOR", selectedColor);
        serviceIntent.putExtra("MULTI", isMultiColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);

        if (hasPerm) {
            btnPermission.setText("Permission: ALLOWED ✓");
            btnPermission.setEnabled(false);
        } else {
            btnPermission.setText("1. Permission ON Karein");
            btnPermission.setEnabled(true);
        }

        if (EdgeLightService.isRunning) {
            statusText.setText("Status: LIGHT CHALU HAI");
            btnToggle.setText("LIGHT OFF KAREIN (Clean Exit)");
        } else {
            statusText.setText("Status: BAND HAI");
            btnToggle.setText("LIGHT ON KAREIN");
        }
    }private ToneGenerator toneGen;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            try {
                if (toneGen == null) {
                    toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
                }
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 45);
            } catch (Exception ignored) {}
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) {
            toneGen.release();
            toneGen = null;
        }
    }
}
