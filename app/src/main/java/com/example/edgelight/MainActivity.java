package com.example.edgelight;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Button btnPermission, btnToggle;
    private TextView statusText;

    private int selectedColor = Color.RED;
    private boolean isMultiColor = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPermission = findViewById(R.id.btnPermission);
        btnToggle = findViewById(R.id.btnToggle);
        statusText = findViewById(R.id.statusText);

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
    }
}
