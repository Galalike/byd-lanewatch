package com.nineoad.bydsignpicinpic.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Interactive touch preview view for Camera Calibration Studio.
 * Supports:
 * - 1-finger pan (XY position adjustment)
 * - 2-finger pinch (Zoom in / out)
 * - 2-finger twist (Fine rotation angle)
 * - Real-time HUD badges and alignment guidelines
 */
public class TouchCalibrationPreviewView extends View {

    public interface OnGestureListener {
        void onPan(float deltaX, float deltaY);
        void onZoom(float scaleFactor);
        void onRotate(float deltaDegrees);
    }

    private OnGestureListener gestureListener;
    private ScaleGestureDetector scaleDetector;

    // Paints
    private Paint gridPaint;
    private Paint pipBorderPaint;
    private Paint pipCornerPaint;
    private Paint pipDimPaint;
    private Paint pipBadgePaint;
    private Paint pipCrosshairPaint;
    private Paint hudBgPaint;
    private Paint hudTextPaint;
    private Paint touchIndicatorPaint;
    private Paint framePaint;

    // State
    private boolean showGrid = true;
    private int pipMode = 1; // 1 = Turn 16:9, 2 = Rearview 3:1, 3 = 4-Wheel 1:1
    private float targetPipAspect = 16f / 9f;
    private float touchX = -1f;
    private float touchY = -1f;
    private boolean isTouching = false;
    private boolean cameraConnected = false;

    // Multi-touch tracking
    private float lastTouchX;
    private float lastTouchY;
    private float lastRotationAngle = 0f;
    private boolean isRotating = false;

    // Live Readout Values
    private float liveCropX = 0f;
    private float liveCropY = 0f;
    private float liveZoom = 1.0f;
    private float liveRotation = 0f;
    private String activeLabel = "กล้องหน้าซ้าย (FL)";

    // Live Frame Bitmap (from hardware camera feed or simulation)
    private Bitmap cameraBitmap = null;

    public TouchCalibrationPreviewView(Context context) {
        super(context);
        init(context);
    }

    public TouchCalibrationPreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TouchCalibrationPreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(Color.TRANSPARENT);
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.argb(70, 255, 255, 255));
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{8f, 8f}, 0));

        pipBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipBorderPaint.setColor(Color.rgb(62, 106, 225)); // Tesla Blue
        pipBorderPaint.setStyle(Paint.Style.STROKE);
        pipBorderPaint.setStrokeWidth(2.5f);

        pipCornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipCornerPaint.setColor(Color.rgb(56, 189, 248)); // Glowing Cyan Viewfinder brackets
        pipCornerPaint.setStyle(Paint.Style.STROKE);
        pipCornerPaint.setStrokeWidth(4.5f);
        pipCornerPaint.setStrokeCap(Paint.Cap.ROUND);

        pipDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipDimPaint.setColor(Color.argb(135, 10, 12, 16)); // Shading outside PIP window
        pipDimPaint.setStyle(Paint.Style.FILL);

        pipBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipBadgePaint.setColor(Color.rgb(56, 189, 248)); // Cyan badge text
        pipBadgePaint.setTextSize(18f);
        pipBadgePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        pipCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipCrosshairPaint.setColor(Color.argb(120, 255, 255, 255));
        pipCrosshairPaint.setStrokeWidth(1.5f);
        pipCrosshairPaint.setPathEffect(new DashPathEffect(new float[]{8f, 8f}, 0));

        hudBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudBgPaint.setColor(Color.argb(190, 18, 18, 20));
        hudBgPaint.setStyle(Paint.Style.FILL);

        hudTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudTextPaint.setColor(Color.WHITE);
        hudTextPaint.setTextSize(26f);

        touchIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        touchIndicatorPaint.setColor(Color.rgb(62, 106, 225)); // Tesla Blue
        touchIndicatorPaint.setStyle(Paint.Style.STROKE);
        touchIndicatorPaint.setStrokeWidth(3f);

        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setFilterBitmap(true);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (gestureListener != null) {
                    gestureListener.onZoom(detector.getScaleFactor());
                }
                return true;
            }
        });
    }

    public void setOnGestureListener(OnGestureListener listener) {
        this.gestureListener = listener;
    }

    public void updateReadouts(float cropX, float cropY, float zoom, float rot, String label) {
        this.liveCropX = cropX;
        this.liveCropY = cropY;
        this.liveZoom = zoom;
        this.liveRotation = rot;
        this.activeLabel = label;
        invalidate();
    }

    public void setCameraConnected(boolean connected) {
        this.cameraConnected = connected;
        invalidate();
    }

    public boolean isCameraConnected() {
        return this.cameraConnected;
    }

    public void setCameraBitmap(Bitmap bitmap) {
        this.cameraBitmap = bitmap;
        invalidate();
    }

    public void setPipMode(int mode) {
        this.pipMode = mode;
        if (mode == 1) {
            this.targetPipAspect = 16f / 9f; // Turn Signal: 16:9 Landscape
        } else if (mode == 2) {
            this.targetPipAspect = 960f / 320f; // Rearview: 3:1 Panoramic Mirror
        } else if (mode == 3) {
            this.targetPipAspect = 270f / 405f; // Front Wheel: 2:3 Vertical Strip
        }
        invalidate();
    }

    public int getPipMode() {
        return this.pipMode;
    }

    public void toggleGrid() {
        this.showGrid = !this.showGrid;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                isTouching = true;
                touchX = event.getX();
                touchY = event.getY();
                lastTouchX = touchX;
                lastTouchY = touchY;
                isRotating = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerCount >= 2) {
                    isRotating = true;
                    lastRotationAngle = calculateAngle(event);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 1 && !scaleDetector.isInProgress()) {
                    float currentX = event.getX();
                    float currentY = event.getY();
                    float dx = (currentX - lastTouchX) / (float) getWidth();
                    float dy = (currentY - lastTouchY) / (float) getHeight();

                    if (gestureListener != null && (Math.abs(dx) > 0.0001f || Math.abs(dy) > 0.0001f)) {
                        gestureListener.onPan(dx, dy);
                    }
                    lastTouchX = currentX;
                    lastTouchY = currentY;
                    touchX = currentX;
                    touchY = currentY;
                } else if (pointerCount >= 2) {
                    float currentAngle = calculateAngle(event);
                    float deltaAngle = currentAngle - lastRotationAngle;
                    if (deltaAngle > 180f) deltaAngle -= 360f;
                    if (deltaAngle < -180f) deltaAngle += 360f;

                    if (gestureListener != null && Math.abs(deltaAngle) > 0.2f) {
                        gestureListener.onRotate(deltaAngle);
                    }
                    lastRotationAngle = currentAngle;
                }
                invalidate();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() <= 2) {
                    isRotating = false;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                touchX = -1f;
                touchY = -1f;
                isRotating = false;
                invalidate();
                break;
        }

        return true;
    }

    private float calculateAngle(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.toDegrees(Math.atan2(y, x));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        // 1. Live camera feed is rendered underneath by FisheyeGLView.
        // If an explicit snapshot bitmap is set, draw it; otherwise canvas is transparent to show live GL video.
        if (cameraBitmap != null && !cameraBitmap.isRecycled()) {
            Rect src = new Rect(0, 0, cameraBitmap.getWidth(), cameraBitmap.getHeight());
            Rect dst = new Rect(0, 0, w, h);
            canvas.drawBitmap(cameraBitmap, src, dst, framePaint);
        }

        // 2. Viewport matches exact PIP aspect ratio
        float boxL = 2f;
        float boxT = 2f;
        float boxR = w - 2f;
        float boxB = h - 2f;
        float boxW = boxR - boxL;
        float boxH = boxB - boxT;

        // 3. Draw PIP Window Bounding Box with rounded corners
        RectF pipRect = new RectF(boxL, boxT, boxR, boxB);
        canvas.drawRoundRect(pipRect, 16f, 16f, pipBorderPaint);

        // 4. High-Tech Corner Viewfinder Brackets
        float bracketLen = Math.min(36f, Math.min(boxW, boxH) * 0.15f);
        // Top-Left
        canvas.drawLine(boxL, boxT, boxL + bracketLen, boxT, pipCornerPaint);
        canvas.drawLine(boxL, boxT, boxL, boxT + bracketLen, pipCornerPaint);
        // Top-Right
        canvas.drawLine(boxR, boxT, boxR - bracketLen, boxT, pipCornerPaint);
        canvas.drawLine(boxR, boxT, boxR, boxT + bracketLen, pipCornerPaint);
        // Bottom-Left
        canvas.drawLine(boxL, boxB, boxL + bracketLen, boxB, pipCornerPaint);
        canvas.drawLine(boxL, boxB, boxL, boxB - bracketLen, pipCornerPaint);
        // Bottom-Right
        canvas.drawLine(boxR, boxB, boxR - bracketLen, boxB, pipCornerPaint);
        canvas.drawLine(boxR, boxB, boxR, boxB - bracketLen, pipCornerPaint);

        // 5. Grid lines INSIDE the PIP bounding box
        if (showGrid) {
            int divX = (pipMode == 3) ? 3 : 6;
            int divY = (pipMode == 3) ? 4 : (pipMode == 2 ? 3 : 4);
            float stepX = boxW / (float) divX;
            float stepY = boxH / (float) divY;
            for (int i = 1; i < divX; i++) {
                canvas.drawLine(boxL + i * stepX, boxT, boxL + i * stepX, boxB, gridPaint);
            }
            for (int j = 1; j < divY; j++) {
                canvas.drawLine(boxL, boxT + j * stepY, boxR, boxT + j * stepY, gridPaint);
            }
        }

        // 6. Touch Indicator Ring when touching
        if (isTouching && touchX >= 0 && touchY >= 0) {
            canvas.drawCircle(touchX, touchY, 50f, touchIndicatorPaint);
            canvas.drawCircle(touchX, touchY, 6f, touchIndicatorPaint);
        }

        // 7. Top Left HUD Badge: Active Mode, PIP Scale & Live Specs
        drawHudOverlay(canvas, w, h);
    }

    private void drawHudOverlay(Canvas canvas, int w, int h) {
        String pipTag;
        if (pipMode == 1) {
            pipTag = "สเกล PIP 16:9";
        } else if (pipMode == 2) {
            pipTag = "สเกล PIP 3:1";
        } else {
            pipTag = "สเกล PIP แนวตั้ง 2:3";
        }

        // Top HUD Capsule (Position & Zoom specs)
        String readout = String.format(Locale.US,
                "%s   |   %s   |   X: %+.2f  Y: %+.2f   |   ซูม: %.2fx   |   หมุน: %+.1f°%s",
                activeLabel, pipTag, liveCropX, liveCropY, liveZoom, liveRotation,
                cameraConnected ? "" : "  [กำลังต่อกล้อง...]");

        float textWidth = hudTextPaint.measureText(readout);
        float pillW = textWidth + 40f;
        float pillH = 50f;
        float pillX = 20f;
        float pillY = 20f;

        RectF pillRect = new RectF(pillX, pillY, pillX + pillW, pillY + pillH);
        canvas.drawRoundRect(pillRect, 25f, 25f, hudBgPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f);
        borderPaint.setColor(Color.argb(100, 62, 106, 225));
        canvas.drawRoundRect(pillRect, 25f, 25f, borderPaint);

        canvas.drawText(readout, pillX + 20f, pillY + 34f, hudTextPaint);

        // Bottom Gestures Guide Badge
        String guide = "👆 1 นิ้ว: เลื่อน X/Y   |   🤏 2 นิ้ว: ซูมเข้า-ออก   |   🔄 2 นิ้ว: เอียงองศา";
        Paint guideText = new Paint(hudTextPaint);
        guideText.setTextSize(20f);
        guideText.setColor(Color.rgb(180, 185, 195));

        float gW = guideText.measureText(guide) + 36f;
        float gH = 42f;
        float gX = (w - gW) / 2f;
        float gY = h - gH - 18f;

        RectF gRect = new RectF(gX, gY, gX + gW, gY + gH);
        canvas.drawRoundRect(gRect, 21f, 21f, hudBgPaint);
        canvas.drawText(guide, gX + 18f, gY + 28f, guideText);
    }
}
