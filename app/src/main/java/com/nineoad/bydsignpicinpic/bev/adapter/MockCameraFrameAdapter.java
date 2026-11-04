package com.nineoad.bydsignpicinpic.bev.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Surface;
import com.nineoad.bydsignpicinpic.bev.profile.CameraInputType;
import com.nineoad.bydsignpicinpic.bev.profile.CameraRole;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;
import java.util.EnumMap;
import java.util.Map;

public class MockCameraFrameAdapter implements ICameraInputAdapter {
    private Context context;
    private VehicleProfile profile;
    private OnFrameAvailableListener listener;
    private volatile boolean running = false;
    private Thread generatorThread;
    private final Map<CameraRole, Bitmap> mockBitmaps = new EnumMap<>(CameraRole.class);

    @Override
    public void initialize(Context context, VehicleProfile profile) {
        this.context = context;
        this.profile = profile;
        initMockBitmaps();
    }

    private void initMockBitmaps() {
        int w = 640;
        int h = 480;
        for (CameraRole role : CameraRole.values()) {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            renderCheckerboard(bmp, role, 0);
            mockBitmaps.put(role, bmp);
        }
    }

    private void renderCheckerboard(Bitmap bmp, CameraRole role, int frameCount) {
        Canvas canvas = new Canvas(bmp);
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int bgColor;
        switch (role) {
            case FRONT: bgColor = Color.rgb(20, 40, 70); break;
            case REAR:  bgColor = Color.rgb(70, 50, 20); break;
            case LEFT:  bgColor = Color.rgb(20, 60, 30); break;
            case RIGHT: bgColor = Color.rgb(60, 20, 40); break;
            default:    bgColor = Color.DKGRAY; break;
        }
        canvas.drawColor(bgColor);

        // Ground Plane Calibration Checkerboard
        Paint checkerPaint1 = new Paint();
        checkerPaint1.setColor(Color.argb(120, 220, 220, 220));
        Paint checkerPaint2 = new Paint();
        checkerPaint2.setColor(Color.argb(80, 50, 50, 50));

        int tileSize = 40;
        for (int y = 0; y < h; y += tileSize) {
            for (int x = 0; x < w; x += tileSize) {
                boolean alt = ((x / tileSize) + (y / tileSize)) % 2 == 0;
                canvas.drawRect(x, y, x + tileSize, y + tileSize, alt ? checkerPaint1 : checkerPaint2);
            }
        }

        // Calibration Crosshair & Distance Ring
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);

        canvas.drawCircle(w / 2f, h / 2f, 120f, linePaint);
        canvas.drawLine(w / 2f - 40, h / 2f, w / 2f + 40, h / 2f, linePaint);
        canvas.drawLine(w / 2f, h / 2f - 40, w / 2f, h / 2f + 40, linePaint);

        // Label
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("MOCK FEED: " + role.label.toUpperCase(), w / 2f, 60f, textPaint);

        // Pulse dot to verify live animation
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.GREEN);
        float pulseX = (float) ((Math.sin(frameCount * 0.1) * 0.5 + 0.5) * (w - 80) + 40);
        canvas.drawCircle(pulseX, h - 40, 10, dotPaint);
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        generatorThread = new Thread(() -> {
            int frame = 0;
            while (running) {
                frame++;
                for (CameraRole role : CameraRole.values()) {
                    Bitmap bmp = mockBitmaps.get(role);
                    if (bmp != null) {
                        renderCheckerboard(bmp, role, frame);
                        if (listener != null) {
                            CameraFrame cf = new CameraFrame(role, bmp.getWidth(), bmp.getHeight(),
                                    System.nanoTime(), bmp, null, 0);
                            listener.onFrameAvailable(role, cf);
                        }
                    }
                }
                try {
                    Thread.sleep(66); // ~15 FPS simulated
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "mock-camera-generator");
        generatorThread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (generatorThread != null) {
            generatorThread.interrupt();
            generatorThread = null;
        }
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available on any hardware (Preview Only mode)
    }

    @Override
    public CameraInputType getInputType() {
        return CameraInputType.MOCK_TEST_STREAM;
    }

    @Override
    public Surface getInputSurface(CameraRole role) {
        return null;
    }

    @Override
    public void setOnFrameAvailableListener(OnFrameAvailableListener listener) {
        this.listener = listener;
    }
}
