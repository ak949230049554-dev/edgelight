package com.example.edgelight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
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
            params.alpha = 0.70f;

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
        private boolean styleBlinkOn = true;

        // Cinematic Cyberpunk & Aurora Neon Colors
        private final int[] cinematicColors = new int[]{
                Color.parseColor("#FF007F"), // Neon Pink
                Color.parseColor("#7928CA"), // Deep Purple
                Color.parseColor("#00DFD8"), // Electric Cyan
                Color.parseColor("#00FF87"), // Emerald Mint
                Color.parseColor("#FF4D4D"), // Neon Red-Orange
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

            if (isShown()) {
                SharedPreferences prefs = getContext().getSharedPreferences("EdgePrefs", Context.MODE_PRIVATE);
                int borderStyle = prefs.getInt("borderStyle", 0); // 0=Normal, 1=Dil, 2=Star, 3=Fire

                if (borderStyle > 0) {
                    styleBlinkOn = !styleBlinkOn;

                    Paint stylePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    stylePaint.setStyle(Paint.Style.FILL);

                    if (borderStyle == 1) {
                        stylePaint.setColor(Color.parseColor("#FF1744")); // Neon Red Dil
                    } else if (borderStyle == 2) {
                        stylePaint.setColor(Color.parseColor("#FFD600")); // Gold Star
                    } else {
                        stylePaint.setColor(Color.parseColor("#FF6D00")); // Orange Fire
                    }
                    stylePaint.setAlpha(styleBlinkOn ? 255 : 75);

                    float scale = (styleBlinkOn ? 1.25f : 0.85f) * (density * 0.65f);
                    int w = getWidth();
                    int h = getHeight();
                    float margin = 20f * density;
                    int gap = (int) (42f * density);

                    // Upar aur neeche border
                    for (float x = margin * 1.5f; x < w - margin; x += gap) {
                        drawCustomShape(canvas, borderStyle, x, margin, scale, stylePaint);
                        drawCustomShape(canvas, borderStyle, x, h - margin, scale, stylePaint);
                    }
                    // Left aur right border
                    for (float y = margin * 3.2f; y < h - margin * 2.5f; y += gap) {
                        drawCustomShape(canvas, borderStyle, margin, y, scale, stylePaint);
                        drawCustomShape(canvas, borderStyle, w - margin, y, scale, stylePaint);
                    }

                    postInvalidateDelayed(350);
                    return;
                }
            }

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

        private void drawCustomShape(Canvas canvas, int style, float cx, float cy, float scale, Paint paint) {
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(scale, scale);
            Path path = new Path();

            if (style == 1) {
                // Real Heart Shape
                path.moveTo(0, 14);
                path.cubicTo(-20, -4, -20, -20, 0, -9);
                path.cubicTo(20, -20, 20, -4, 0, 14);
            } else if (style == 2) {
                // Star Shape
                path.moveTo(0, -17);
                path.lineTo(5, -5);
                path.lineTo(17, 0);
                path.lineTo(5, 5);
                path.lineTo(0, 17);
                path.lineTo(-5, 5);
                path.lineTo(-17, 0);
                path.lineTo(-5, -5);
                path.close();
            } else {
                // Fire Flame Shape
                path.moveTo(0, -18);
                path.cubicTo(13, -5, 15, 11, 0, 16);
                path.cubicTo(-15, 11, -13, -5, 0, -18);
            }

            canvas.drawPath(path, paint);
            canvas.restore();
        }
    }
}
