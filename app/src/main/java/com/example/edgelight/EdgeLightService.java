package com.example.edgelight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.Build;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;

public class EdgeLightService extends Service {

    public static boolean isRunning = false;
    private WindowManager windowManager;
    private EdgeLightView edgeLightView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        startForegroundNotification();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int color = Color.parseColor("#FF007F");
        boolean isMulti = true;

        if (intent != null) {
            color = intent.getIntExtra("COLOR", Color.parseColor("#FF007F"));
            isMulti = intent.getBooleanExtra("MULTI", true);
        }

        if (edgeLightView == null) {
            edgeLightView = new EdgeLightView(this, color, isMulti);

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

            // Poora Fullscreen (Notch aur Camera ke upar bhi)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            windowManager.addView(edgeLightView, params);
        } else {
            edgeLightView.updateColor(color, isMulti);
        }

        return START_STICKY;
    }

    private void startForegroundNotification() {
        String channelId = "edge_light_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Edge Light Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("Edge Light Chalu Hai")
                .setContentText("Fullscreen Cinematic Neon active hai")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (edgeLightView != null && windowManager != null) {
            windowManager.removeView(edgeLightView);
            edgeLightView = null;
        }
    }

    private static class EdgeLightView extends View {
        private final Paint outerGlowPaint, midGlowPaint, corePaint;
        private final RectF rectF = new RectF();
        private final Matrix gradientMatrix = new Matrix();
        private SweepGradient sweepGradient;

        private int singleColor;
        private boolean isMultiColor;
        private float angle = 0f;
        private float pulsePhase = 0f;
        private final float density;

        // Cinematic Cyberpunk & Aurora Neon Colors
        private final int[] cinematicColors = new int[]{
                Color.parseColor("#FF007F"), // Neon Pink
                Color.parseColor("#7928CA"), // Deep Purple
                Color.parseColor("#00F0FF"), // Electric Cyan
                Color.parseColor("#0066FF"), // Cinema Blue
                Color.parseColor("#FF2A00"), // Neon Red-Orange
                Color.parseColor("#FF007F")
        };

        public EdgeLightView(Context context, int color, boolean multi) {
            super(context);
            this.singleColor = color;
            this.isMultiColor = multi;
            this.density = context.getResources().getDisplayMetrics().density;

            outerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            outerGlowPaint.setStyle(Paint.Style.STROKE);
            outerGlowPaint.setStrokeWidth(20f * density);

            midGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            midGlowPaint.setStyle(Paint.Style.STROKE);
            midGlowPaint.setStrokeWidth(10f * density);

            corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            corePaint.setStyle(Paint.Style.STROKE);
            corePaint.setStrokeWidth(3.5f * density);
        }

        public void updateColor(int color, boolean multi) {
            this.singleColor = color;
            this.isMultiColor = multi;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            sweepGradient = new SweepGradient(w / 2f, h / 2f, cinematicColors, null);
            float pad = 3f * density;
            rectF.set(pad, pad, w - pad, h - pad);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cornerRadius = 36f * density;
            pulsePhase += 0.06f;
            float pulse = (float) (0.72 + 0.28 * Math.sin(pulsePhase));

            if (isMultiColor && sweepGradient != null) {
                angle = (angle + 3.2f) % 360f;
                gradientMatrix.setRotate(angle, getWidth() / 2f, getHeight() / 2f);
                sweepGradient.setLocalMatrix(gradientMatrix);

                outerGlowPaint.setShader(sweepGradient);
                midGlowPaint.setShader(sweepGradient);
                corePaint.setShader(sweepGradient);
            } else {
                outerGlowPaint.setShader(null);
                midGlowPaint.setShader(null);
                corePaint.setShader(null);

                outerGlowPaint.setColor(singleColor);
                midGlowPaint.setColor(singleColor);
                corePaint.setColor(Color.WHITE);
            }

            // 3-Layer Cinematic Neon Glow
            outerGlowPaint.setAlpha((int) (85 * pulse));
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, outerGlowPaint);

            midGlowPaint.setAlpha((int) (180 * pulse));
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, midGlowPaint);

            corePaint.setAlpha((int) (255 * pulse));
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, corePaint);

            postInvalidateOnAnimation();
        }
    }
}
