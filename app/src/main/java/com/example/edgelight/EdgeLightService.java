package com.example.edgelight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

public class EdgeLightService extends Service {

    public static boolean isRunning = false;

    private WindowManager windowManager;
    private EdgeView edgeView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLightOn = true;
    private boolean isScreenOn = true;

    private int currentColor = Color.RED;
    private boolean isMultiColor = true;
    private int colorIndex = 0;

    private final int[] multiColors = new int[]{
            Color.parseColor("#FF0000"),
            Color.parseColor("#00FF00"),
            Color.parseColor("#0066FF"),
            Color.parseColor("#FFFF00"),
            Color.parseColor("#00FFFF"),
            Color.parseColor("#FF00CC"),
            Color.parseColor("#FFFFFF")
    };

    private final Runnable blinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScreenOn) {
                isLightOn = !isLightOn;

                if (isLightOn && isMultiColor) {
                    currentColor = multiColors[colorIndex];
                    colorIndex = (colorIndex + 1) % multiColors.length;
                }

                if (edgeView != null) {
                    edgeView.updateColor(currentColor);
                    edgeView.invalidate();
                }
                handler.postDelayed(this, 500);
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
                handler.removeCallbacks(blinkRunnable);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
                handler.removeCallbacks(blinkRunnable);
                handler.post(blinkRunnable);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        startForegroundNotification();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        edgeView = new EdgeView(this);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        windowManager.addView(edgeView, params);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);

        handler.post(blinkRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            currentColor = intent.getIntExtra("COLOR", Color.RED);
            isMultiColor = intent.getBooleanExtra("MULTI", true);
            if (edgeView != null) {
                edgeView.updateColor(currentColor);
                edgeView.invalidate();
            }
        }
        return START_NOT_STICKY;
    }

    private void startForegroundNotification() {
        String channelId = "edge_light_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Edge Light Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("Edge Light Chalu Hai")
                .setContentText("OFF karne ke liye yahan tap karein")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    private class EdgeView extends View {
        private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public EdgeView(Context context) {
            super(context);
            outerPaint.setStyle(Paint.Style.STROKE);
            outerPaint.setStrokeWidth(14f);

            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(28f);

            updateColor(currentColor);
        }

        public void updateColor(int color) {
            outerPaint.setColor(color);
            int glowColor = Color.argb(100, Color.red(color), Color.green(color), Color.blue(color));
            glowPaint.setColor(glowColor);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (isLightOn) {
                rect.set(7f, 7f, getWidth() - 7f, getHeight() - 7f);
                canvas.drawRoundRect(rect, 55f, 55f, glowPaint);
                canvas.drawRoundRect(rect, 55f, 55f, outerPaint);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {}

        if (edgeView != null && windowManager != null) {
            windowManager.removeView(edgeView);
            edgeView = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
