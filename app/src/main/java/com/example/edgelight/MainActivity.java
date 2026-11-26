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
import android.app.DownloadManager;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final int CURRENT_VERSION = 5;
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/ak949230049554-dev/edgelight/main/update.json";

    private Button btnPermission, btnToggle;
    private TextView statusText, updateRedDot;
    private LinearLayout btnTopUpdate;

    private int selectedColor = Color.RED;
    private boolean isMultiColor = true;

    private boolean isUpdateAvailable = false;
    private int latestVersionFound = 5;
    private String updateTitle = "Naya Update Available Hai!";
    private String updateMessage = "Naye features ke liye app update karein.";
    private String updateDownloadUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        java.io.File oldApk = new java.io.File(getCacheDir(), "EdgeLight_Update.apk");
        if (oldApk.exists()) {
            oldApk.delete();
        }
// --- 1-Click Neon Presets Bar (Version 5) ---
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(16, 16, 16, 30);

        String[] presetNames = {"Blood Red", "Cyber Blue", "Toxic Green"};
        int[] presetColors = {
                Color.parseColor("#FF003C"),
                Color.parseColor("#00F0FF"),
                Color.parseColor("#39FF14")
        };

        for (int i = 0; i < presetNames.length; i++) {
            final int chosenColor = presetColors[i];
            final String chosenName = presetNames[i];

            Button btn = new Button(this);
            btn.setText(chosenName);
            btn.setTextColor(Color.BLACK);
            btn.setBackgroundColor(chosenColor);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            );
            lp.setMargins(8, 0, 8, 0);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                getSharedPreferences("EdgeLightPrefs", MODE_PRIVATE)
                        .edit()
                        .putInt("edge_color", chosenColor)
                        .apply();
                Toast.makeText(this, chosenName + " Activated!", Toast.LENGTH_SHORT).show();
            });

            presetRow.addView(btn);
        }

        android.widget.FrameLayout.LayoutParams barParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
        );
        addContentView(presetRow, barParams);
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
                .setPositiveButton("Update Karein", (dialog, which) -> downloadAndInstallApk(downloadUrl))
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

        Intent intent = new Intent(this, MainActivity.class);
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
    private boolean isDownloading = false;

    private void downloadAndInstallApk(final String apkUrl) {
        if (apkUrl == null || apkUrl.isEmpty()) return;

        if (isDownloading) {
            android.widget.Toast.makeText(this, "Update pehle se download ho raha hai...", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                android.widget.Toast.makeText(this, "Pehle 'Allow from this source' ON karein, fir dubara Update dabayein", android.widget.Toast.LENGTH_LONG).show();
                Intent permIntent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + getPackageName())
                );
                startActivity(permIntent);
                return;
            }
        }

        isDownloading = true;

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(50, 40, 50, 40);

        final android.widget.TextView percentText = new android.widget.TextView(this);
        percentText.setText("Updating: 0%");
        percentText.setTextSize(16);
        percentText.setPadding(0, 0, 0, 20);

        final android.widget.ProgressBar progressBar = new android.widget.ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);

        box.addView(percentText);
        box.addView(progressBar);

        final AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("App Update Ho Raha Hai")
                .setView(box)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                String finalUrl = apkUrl;
                java.net.HttpURLConnection conn = null;
                for (int i = 0; i < 5; i++) {
                    conn = (java.net.HttpURLConnection) new java.net.URL(finalUrl).openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    int status = conn.getResponseCode();
                    if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP
                            || status == java.net.HttpURLConnection.HTTP_MOVED_PERM
                            || status == java.net.HttpURLConnection.HTTP_SEE_OTHER
                            || status == 307 || status == 308) {
                        finalUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                    } else {
                        break;
                    }
                }

                int fileLength = conn.getContentLength();
                final java.io.File apkFile = new java.io.File(getCacheDir(), "EdgeLight_Update.apk");
                if (apkFile.exists()) {
                    apkFile.delete();
                }

                java.io.InputStream input = conn.getInputStream();
                java.io.FileOutputStream output = new java.io.FileOutputStream(apkFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                int lastPercent = -1;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);
                    if (fileLength > 0) {
                        final int progress = (int) (total * 100 / fileLength);
                        if (progress != lastPercent) {
                            lastPercent = progress;
                            runOnUiThread(() -> {
                                progressBar.setProgress(progress);
                                percentText.setText("Updating: " + progress + "%");
                            });
                        }
                    } else {
                        final long kb = total / 1024;
                        runOnUiThread(() -> percentText.setText("Downloading: " + kb + " KB"));
                    }
                }

                output.flush();
                output.close();
                input.close();
                conn.disconnect();

                runOnUiThread(() -> {
                    progressBar.setProgress(100);
                    percentText.setText("Updating: 100%");
                    progressDialog.dismiss();
                    isDownloading = false;

                    android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                            MainActivity.this,
                            getPackageName() + ".provider",
                            apkFile
                    );
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(installIntent);
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    isDownloading = false;
                    android.widget.Toast.makeText(MainActivity.this, "Update fail: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
