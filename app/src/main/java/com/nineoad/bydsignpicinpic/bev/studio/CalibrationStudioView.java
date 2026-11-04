package com.nineoad.bydsignpicinpic.bev.studio;

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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import androidx.annotation.Nullable;
import com.nineoad.bydsignpicinpic.bev.adapter.CameraFrame;
import com.nineoad.bydsignpicinpic.bev.engine.UniversalCameraProcessingEngine;
import com.nineoad.bydsignpicinpic.bev.profile.CameraCalibration;
import com.nineoad.bydsignpicinpic.bev.profile.CameraRole;
import com.nineoad.bydsignpicinpic.bev.profile.Point2D;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * High-precision automotive Touch Calibration Studio View.
 * Supports:
 * - 4 interactive draggable corner control handles (p0, p1, p2, p3)
 * - Anti-occlusion finger offset line
 * - 1-finger pan and fine translation
 * - 2-finger pinch zoom and rotation
 * - Distance grid and alignment guide overlays
 * - Before / After comparison
 * - Full BEV 360 preview mode with interactive quadrant tap selection
 * - Full Undo / Redo history stack
 */
public class CalibrationStudioView extends View {

    public interface OnCalibrationListener {
        void onCalibrationChanged(CameraRole role, CameraCalibration calibration);
        void onHandleSelected(int handleIndex, Point2D point);
        void onSectorSelected(CameraRole role);
        void onHistoryStateChanged(boolean canUndo, boolean canRedo);
    }

    private static final int MAX_HISTORY = 40;
    private static final float TOUCH_RADIUS_DP = 44f;
    private static final float HANDLE_RADIUS_DP = 14f;
    private static final float FINGER_OFFSET_DP = 40f; // Elevates handle above thumb

    private final float density;
    private final float touchRadiusPx;
    private final float handleRadiusPx;
    private final float fingerOffsetPx;

    private UniversalCameraProcessingEngine engine;
    private VehicleProfile profile;
    private CameraRole activeRole = CameraRole.FRONT; // null = 360 BEV Preview
    private CameraFrame currentFrame;

    private boolean showGrid = true;
    private boolean showGuides = true;
    private boolean beforeAfterMode = false; // true = Raw unwarped frame

    private int selectedHandleIndex = 0; // 0=TL, 1=TR, 2=BR, 3=BL
    private int draggingHandleIndex = -1;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private boolean isDraggingHandle = false;
    private boolean isPanning = false;

    // Multi-touch gestures
    private ScaleGestureDetector scaleDetector;
    private float lastRotationAngle = 0f;
    private boolean isTwoFingerGesture = false;

    // Undo / Redo History
    private final Deque<CameraCalibration> undoStack = new ArrayDeque<>();
    private final Deque<CameraCalibration> redoStack = new ArrayDeque<>();

    // Paints
    private Paint bgPaint;
    private Paint framePaint;
    private Paint quadStrokePaint;
    private Paint quadFillPaint;
    private Paint diagonalPaint;
    private Paint handleOuterPaint;
    private Paint handleInnerPaint;
    private Paint handleActivePaint;
    private Paint handleTextPaint;
    private Paint guidePaint;
    private Paint gridPaint;
    private Paint textBadgePaint;
    private Paint leaderLinePaint;

    // Geometries
    private final RectF viewportRect = new RectF();
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final Matrix transformMatrix = new Matrix();
    private final float[] polySrc = new float[8];
    private final float[] polyDst = new float[8];

    private OnCalibrationListener listener;

    public CalibrationStudioView(Context context) {
        this(context, null);
    }

    public CalibrationStudioView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CalibrationStudioView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.density = getResources().getDisplayMetrics().density;
        this.touchRadiusPx = TOUCH_RADIUS_DP * density;
        this.handleRadiusPx = HANDLE_RADIUS_DP * density;
        this.fingerOffsetPx = FINGER_OFFSET_DP * density;

        initPaints();
        initGestureDetectors();
    }

    private void initPaints() {
        bgPaint = new Paint();
        bgPaint.setColor(Color.rgb(18, 20, 24)); // Dark cockpit asphalt

        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        quadStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        quadStrokePaint.setColor(Color.rgb(0, 229, 255)); // Neon Cyan
        quadStrokePaint.setStrokeWidth(3.5f * density);
        quadStrokePaint.setStyle(Paint.Style.STROKE);

        quadFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        quadFillPaint.setColor(Color.argb(35, 0, 229, 255));
        quadFillPaint.setStyle(Paint.Style.FILL);

        diagonalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        diagonalPaint.setColor(Color.argb(120, 0, 229, 255));
        diagonalPaint.setStrokeWidth(1.5f * density);
        diagonalPaint.setStyle(Paint.Style.STROKE);
        diagonalPaint.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0));

        handleOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handleOuterPaint.setColor(Color.argb(90, 0, 229, 255));
        handleOuterPaint.setStyle(Paint.Style.FILL);

        handleInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handleInnerPaint.setColor(Color.rgb(2, 132, 199)); // Brand Cyan
        handleInnerPaint.setStyle(Paint.Style.FILL);

        handleActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handleActivePaint.setColor(Color.rgb(255, 179, 0)); // Amber Glow for active
        handleActivePaint.setStyle(Paint.Style.FILL);

        handleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handleTextPaint.setColor(Color.WHITE);
        handleTextPaint.setTextSize(12f * density);
        handleTextPaint.setFakeBoldText(true);
        handleTextPaint.setTextAlign(Paint.Align.CENTER);

        guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidePaint.setColor(Color.argb(120, 255, 255, 255));
        guidePaint.setStrokeWidth(1.5f * density);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setPathEffect(new DashPathEffect(new float[]{6 * density, 6 * density}, 0));

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.argb(50, 0, 220, 255));
        gridPaint.setStrokeWidth(1.2f * density);
        gridPaint.setStyle(Paint.Style.STROKE);

        textBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBadgePaint.setColor(Color.argb(200, 15, 23, 42));
        textBadgePaint.setStyle(Paint.Style.FILL);

        leaderLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        leaderLinePaint.setColor(Color.rgb(255, 179, 0));
        leaderLinePaint.setStrokeWidth(2f * density);
        leaderLinePaint.setStyle(Paint.Style.STROKE);
    }

    private void initGestureDetectors() {
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (activeRole != null && profile != null) {
                    CameraCalibration cal = profile.getCamera(activeRole);
                    float factor = detector.getScaleFactor();
                    cal.scaleX = Math.max(0.2f, Math.min(3.0f, cal.scaleX * factor));
                    cal.scaleY = Math.max(0.2f, Math.min(3.0f, cal.scaleY * factor));
                    notifyCalibrationUpdated();
                    invalidate();
                    return true;
                }
                return false;
            }
        });
    }

    public void setEngine(UniversalCameraProcessingEngine engine) {
        this.engine = engine;
        if (engine != null) {
            this.profile = engine.getProfile();
        }
        invalidate();
    }

    public void setProfile(VehicleProfile profile) {
        this.profile = profile;
        if (engine != null) {
            engine.updateProfile(profile);
        }
        undoStack.clear();
        redoStack.clear();
        notifyHistoryChanged();
        invalidate();
    }

    public void setOnCalibrationListener(OnCalibrationListener listener) {
        this.listener = listener;
    }

    public void setActiveRole(@Nullable CameraRole role) {
        this.activeRole = role;
        this.selectedHandleIndex = 0;
        this.draggingHandleIndex = -1;
        this.isDraggingHandle = false;
        undoStack.clear();
        redoStack.clear();
        notifyHistoryChanged();
        if (listener != null && role != null && profile != null) {
            Point2D pt = getHandlePoint(profile.getCamera(role), selectedHandleIndex);
            listener.onHandleSelected(selectedHandleIndex, pt);
        }
        invalidate();
    }

    @Nullable
    public CameraRole getActiveRole() {
        return activeRole;
    }

    public void setCameraFrame(CameraRole role, CameraFrame frame) {
        if (engine != null) {
            engine.onFrameReceived(role, frame);
        }
        if (activeRole == role) {
            this.currentFrame = frame;
        }
        postInvalidate();
    }

    public void setShowGrid(boolean show) {
        this.showGrid = show;
        if (profile != null) {
            profile.rendering.showGrid = show;
        }
        invalidate();
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setBeforeAfterMode(boolean before) {
        this.beforeAfterMode = before;
        invalidate();
    }

    public boolean isBeforeAfterMode() {
        return beforeAfterMode;
    }

    public int getSelectedHandleIndex() {
        return selectedHandleIndex;
    }

    public void setSelectedHandleIndex(int index) {
        setSelectedHandleIndex(index, true);
    }

    public void setSelectedHandleIndex(int index, boolean notify) {
        if (index >= 0 && index < 4) {
            boolean changed = (this.selectedHandleIndex != index);
            this.selectedHandleIndex = index;
            if (notify && changed && listener != null && activeRole != null && profile != null) {
                Point2D pt = getHandlePoint(profile.getCamera(activeRole), index);
                listener.onHandleSelected(index, pt);
            }
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateViewport(w, h);
    }

    private void calculateViewport(int w, int h) {
        float padding = 20f * density;
        float availW = w - padding * 2;
        float availH = h - padding * 2;

        // Maintain 4:3 or 16:9 view aspect ratio
        float targetAspect = 16f / 9f;
        float viewW = availW;
        float viewH = viewW / targetAspect;

        if (viewH > availH) {
            viewH = availH;
            viewW = viewH * targetAspect;
        }

        float left = (w - viewW) / 2f;
        float top = (h - viewH) / 2f;
        viewportRect.set(left, top, left + viewW, top + viewH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        if (profile == null) {
            drawEmptyState(canvas, "No Vehicle Profile Loaded");
            return;
        }

        if (activeRole == null) {
            // BEV 360 Preview Mode
            drawBevComposite(canvas);
        } else {
            // Individual Camera Calibration Mode
            drawSingleCameraCalibration(canvas);
        }
    }

    private void drawEmptyState(Canvas canvas, String msg) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(20f * density);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(msg, getWidth() / 2f, getHeight() / 2f, p);
    }

    private void drawBevComposite(Canvas canvas) {
        if (engine == null) return;
        Bitmap bev = engine.composeBirdEyeView();
        if (bev != null && !bev.isRecycled()) {
            srcRect.set(0, 0, bev.getWidth(), bev.getHeight());
            canvas.drawBitmap(bev, srcRect, viewportRect, framePaint);
        }

        // Draw Interactive Quadrant Tap Guides
        float cx = viewportRect.centerX();
        float cy = viewportRect.centerY();

        Paint quadrantText = new Paint(Paint.ANTI_ALIAS_FLAG);
        quadrantText.setColor(Color.argb(180, 255, 255, 255));
        quadrantText.setTextSize(14f * density);
        quadrantText.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("TAP TO CALIBRATE: [FRONT]", cx, viewportRect.top + 32f * density, quadrantText);
        canvas.drawText("[REAR]", cx, viewportRect.bottom - 16f * density, quadrantText);
        canvas.drawText("[LEFT]", viewportRect.left + 50f * density, cy, quadrantText);
        canvas.drawText("[RIGHT]", viewportRect.right - 50f * density, cy, quadrantText);
    }

    private void drawSingleCameraCalibration(Canvas canvas) {
        CameraCalibration cal = profile.getCamera(activeRole);
        if (cal == null) return;

        // 1. Draw Camera Frame (Raw or Warped)
        if (currentFrame != null && currentFrame.bitmap != null && !currentFrame.bitmap.isRecycled()) {
            Bitmap bmp = currentFrame.bitmap;
            int bw = bmp.getWidth();
            int bh = bmp.getHeight();

            if (beforeAfterMode) {
                // Before: Centered Raw Uncalibrated Frame
                srcRect.set(0, 0, bw, bh);
                canvas.drawBitmap(bmp, srcRect, viewportRect, framePaint);
                drawBadge(canvas, viewportRect.left + 16 * density, viewportRect.top + 28 * density, "BEFORE: RAW FRAME", Color.rgb(255, 82, 82));
            } else {
                // After: Calibrated Perspective Warp
                canvas.save();
                canvas.clipRect(viewportRect);

                // Hardware-accelerated perspective warp via poly-to-poly matrix
                setupPolyToPolyMatrix(cal, bw, bh);
                canvas.drawBitmap(bmp, transformMatrix, framePaint);
                canvas.restore();

                drawBadge(canvas, viewportRect.left + 16 * density, viewportRect.top + 28 * density, "AFTER: GROUND PROJECTED", Color.rgb(0, 229, 255));
            }
        } else {
            // Draw placeholder frame grid
            Paint phPaint = new Paint();
            phPaint.setColor(Color.rgb(25, 30, 38));
            canvas.drawRect(viewportRect, phPaint);
            drawBadge(canvas, viewportRect.centerX(), viewportRect.centerY(), "WAITING FOR CAMERA STREAM...", Color.LTGRAY);
        }

        // Viewport Border
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.argb(80, 255, 255, 255));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f * density);
        canvas.drawRect(viewportRect, border);

        // 2. Draw Distance & Alignment Grid
        if (showGrid) {
            drawPerspectiveGrid(canvas);
        }

        // 3. Draw 4-Point Quad Polygon
        drawCalibrationQuad(canvas, cal);

        // 4. Draw 4 Draggable Corner Handles
        drawHandles(canvas, cal);
    }

    private void setupPolyToPolyMatrix(CameraCalibration cal, int bw, int bh) {
        polySrc[0] = 0f;  polySrc[1] = 0f;
        polySrc[2] = bw;  polySrc[3] = 0f;
        polySrc[4] = bw;  polySrc[5] = bh;
        polySrc[6] = 0f;  polySrc[7] = bh;

        polyDst[0] = viewportRect.left + cal.p0.x * viewportRect.width();
        polyDst[1] = viewportRect.top  + cal.p0.y * viewportRect.height();
        polyDst[2] = viewportRect.left + cal.p1.x * viewportRect.width();
        polyDst[3] = viewportRect.top  + cal.p1.y * viewportRect.height();
        polyDst[4] = viewportRect.left + cal.p2.x * viewportRect.width();
        polyDst[5] = viewportRect.top  + cal.p2.y * viewportRect.height();
        polyDst[6] = viewportRect.left + cal.p3.x * viewportRect.width();
        polyDst[7] = viewportRect.top  + cal.p3.y * viewportRect.height();

        transformMatrix.setPolyToPoly(polySrc, 0, polyDst, 0, 4);

        // Apply fine rotation and scale around quad center
        float qcx = (polyDst[0] + polyDst[2] + polyDst[4] + polyDst[6]) / 4f;
        float qcy = (polyDst[1] + polyDst[3] + polyDst[5] + polyDst[7]) / 4f;

        transformMatrix.postTranslate(cal.offsetX * viewportRect.width(), cal.offsetY * viewportRect.height());
        transformMatrix.postRotate(cal.fineRotationDeg, qcx, qcy);
        transformMatrix.postScale(cal.scaleX, cal.scaleY, qcx, qcy);
    }

    private void drawPerspectiveGrid(Canvas canvas) {
        float cx = viewportRect.centerX();
        float cy = viewportRect.centerY();
        float w = viewportRect.width();
        float h = viewportRect.height();

        // 1m, 2m, 3m Reference Distance Arcs / Ground Rings
        canvas.drawCircle(cx, cy, w * 0.18f, gridPaint);
        canvas.drawCircle(cx, cy, w * 0.32f, gridPaint);
        canvas.drawCircle(cx, cy, w * 0.45f, gridPaint);

        // Horizon & Center Crosshairs
        canvas.drawLine(viewportRect.left, cy, viewportRect.right, cy, guidePaint);
        canvas.drawLine(cx, viewportRect.top, cx, viewportRect.bottom, guidePaint);

        // Ground Projection Perspective Rays
        for (int i = 1; i <= 3; i++) {
            float off = (w / 8f) * i;
            canvas.drawLine(cx, cy, viewportRect.left + off, viewportRect.bottom, gridPaint);
            canvas.drawLine(cx, cy, viewportRect.right - off, viewportRect.bottom, gridPaint);
        }
    }

    private void drawCalibrationQuad(Canvas canvas, CameraCalibration cal) {
        float sx0 = viewportRect.left + cal.p0.x * viewportRect.width();
        float sy0 = viewportRect.top  + cal.p0.y * viewportRect.height();
        float sx1 = viewportRect.left + cal.p1.x * viewportRect.width();
        float sy1 = viewportRect.top  + cal.p1.y * viewportRect.height();
        float sx2 = viewportRect.left + cal.p2.x * viewportRect.width();
        float sy2 = viewportRect.top  + cal.p2.y * viewportRect.height();
        float sx3 = viewportRect.left + cal.p3.x * viewportRect.width();
        float sy3 = viewportRect.top  + cal.p3.y * viewportRect.height();

        Path path = new Path();
        path.moveTo(sx0, sy0);
        path.lineTo(sx1, sy1);
        path.lineTo(sx2, sy2);
        path.lineTo(sx3, sy3);
        path.close();

        // Translucent polygon fill
        canvas.drawPath(path, quadFillPaint);

        // Bold neon stroke
        canvas.drawPath(path, quadStrokePaint);

        // Dashed diagonal alignment guides
        canvas.drawLine(sx0, sy0, sx2, sy2, diagonalPaint);
        canvas.drawLine(sx1, sy1, sx3, sy3, diagonalPaint);
    }

    private void drawHandles(Canvas canvas, CameraCalibration cal) {
        Point2D[] pts = new Point2D[]{cal.p0, cal.p1, cal.p2, cal.p3};
        String[] labels = new String[]{"TL (P0)", "TR (P1)", "BR (P2)", "BL (P3)"};

        for (int i = 0; i < 4; i++) {
            float hx = viewportRect.left + pts[i].x * viewportRect.width();
            float hy = viewportRect.top  + pts[i].y * viewportRect.height();
            boolean isSelected = (i == selectedHandleIndex);
            boolean isDragging = (i == draggingHandleIndex && isDraggingHandle);

            // If dragging, draw anti-occlusion leader line to user's finger touch position
            if (isDragging) {
                canvas.drawLine(hx, hy, lastTouchX, lastTouchY, leaderLinePaint);
                canvas.drawCircle(lastTouchX, lastTouchY, 8f * density, leaderLinePaint);
            }

            // Outer touch aura
            canvas.drawCircle(hx, hy, touchRadiusPx * 0.7f, handleOuterPaint);

            // Handle circle
            Paint inner = (isSelected || isDragging) ? handleActivePaint : handleInnerPaint;
            canvas.drawCircle(hx, hy, handleRadiusPx, inner);

            // White center crosshair / dot
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(Color.WHITE);
            canvas.drawCircle(hx, hy, 3.5f * density, dot);

            // Label badge pill
            String text = String.format(Locale.US, "%s [%.2f, %.2f]", labels[i], pts[i].x, pts[i].y);
            float badgeY = (i == 0 || i == 1) ? hy - 18f * density : hy + 26f * density;
            drawBadge(canvas, hx, badgeY, text, isSelected ? Color.rgb(255, 179, 0) : Color.rgb(0, 229, 255));
        }
    }

    private void drawBadge(Canvas canvas, float x, float y, String text, int textColor) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(210, 15, 23, 42));

        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(textColor);
        tp.setTextSize(11f * density);
        tp.setFakeBoldText(true);
        tp.setTextAlign(Paint.Align.CENTER);

        float textW = tp.measureText(text);
        float padX = 8f * density;
        float padY = 4f * density;
        RectF pill = new RectF(x - textW / 2f - padX, y - 11f * density - padY, x + textW / 2f + padX, y + padY);

        canvas.drawRoundRect(pill, 6f * density, 6f * density, bg);
        canvas.drawText(text, x, y - 2f * density, tp);
    }

    // Touch Event Handling
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        if (pointerCount >= 2) {
            isTwoFingerGesture = true;
            handleTwoFingerRotation(event);
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                lastTouchX = touchDownX;
                lastTouchY = touchDownY;
                isTwoFingerGesture = false;

                if (activeRole == null) {
                    // In BEV 360 mode: detect quadrant tap
                    handleSectorTap(touchDownX, touchDownY);
                    return true;
                }

                // In Single Camera mode: Hit test 4 handles
                int hitIndex = findNearestHandle(touchDownX, touchDownY);
                if (hitIndex >= 0) {
                    // Push state for undo before starting drag
                    pushUndoState();
                    selectedHandleIndex = hitIndex;
                    draggingHandleIndex = hitIndex;
                    isDraggingHandle = true;
                    isPanning = false;

                    if (listener != null && profile != null) {
                        Point2D pt = getHandlePoint(profile.getCamera(activeRole), selectedHandleIndex);
                        listener.onHandleSelected(selectedHandleIndex, pt);
                    }
                    invalidate();
                } else if (viewportRect.contains(touchDownX, touchDownY)) {
                    isPanning = true;
                    isDraggingHandle = false;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float curX = event.getX();
                float curY = event.getY();
                lastTouchX = curX;
                lastTouchY = curY;

                if (isTwoFingerGesture) {
                    return true;
                }

                if (isDraggingHandle && draggingHandleIndex >= 0 && activeRole != null && profile != null) {
                    // Apply anti-occlusion finger offset: handle sits above user finger
                    float targetX = curX;
                    float targetY = curY - fingerOffsetPx;

                    // Convert to normalized viewport coordinates
                    float normX = (targetX - viewportRect.left) / viewportRect.width();
                    float normY = (targetY - viewportRect.top)  / viewportRect.height();

                    // Clamp to safe bounds [-0.5, 1.5]
                    normX = Math.max(-0.5f, Math.min(1.5f, normX));
                    normY = Math.max(-0.5f, Math.min(1.5f, normY));

                    CameraCalibration cal = profile.getCamera(activeRole);
                    setHandlePoint(cal, draggingHandleIndex, normX, normY);

                    // Recompute homography matrix
                    if (engine != null) {
                        engine.updateCameraControlPoints(activeRole, cal.p0, cal.p1, cal.p2, cal.p3);
                    }
                    notifyCalibrationUpdated();
                    invalidate();
                } else if (isPanning && activeRole != null && profile != null) {
                    float dx = (curX - touchDownX) / viewportRect.width();
                    float dy = (curY - touchDownY) / viewportRect.height();
                    CameraCalibration cal = profile.getCamera(activeRole);
                    cal.offsetX += dx * 0.05f;
                    cal.offsetY += dy * 0.05f;
                    touchDownX = curX;
                    touchDownY = curY;
                    notifyCalibrationUpdated();
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDraggingHandle = false;
                draggingHandleIndex = -1;
                isPanning = false;
                isTwoFingerGesture = false;
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void handleTwoFingerRotation(MotionEvent event) {
        if (event.getPointerCount() < 2 || activeRole == null || profile == null) return;

        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);
        float currentAngle = (float) Math.toDegrees(Math.atan2(dy, dx));

        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            lastRotationAngle = currentAngle;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float delta = currentAngle - lastRotationAngle;
            if (Math.abs(delta) < 40f) { // Ignore wild wrap around jumps
                CameraCalibration cal = profile.getCamera(activeRole);
                cal.fineRotationDeg += delta * 0.6f;
                notifyCalibrationUpdated();
                invalidate();
            }
            lastRotationAngle = currentAngle;
        }
    }

    private void handleSectorTap(float x, float y) {
        if (!viewportRect.contains(x, y)) return;

        float cx = viewportRect.centerX();
        float cy = viewportRect.centerY();
        float dx = x - cx;
        float dy = y - cy;

        CameraRole tappedRole;
        if (Math.abs(dx) > Math.abs(dy)) {
            tappedRole = (dx < 0) ? CameraRole.LEFT : CameraRole.RIGHT;
        } else {
            tappedRole = (dy < 0) ? CameraRole.FRONT : CameraRole.REAR;
        }

        if (listener != null) {
            listener.onSectorSelected(tappedRole);
        }
    }

    private int findNearestHandle(float touchX, float touchY) {
        if (activeRole == null || profile == null) return -1;
        CameraCalibration cal = profile.getCamera(activeRole);
        Point2D[] pts = new Point2D[]{cal.p0, cal.p1, cal.p2, cal.p3};

        int bestIndex = -1;
        float bestDistSq = touchRadiusPx * touchRadiusPx;

        for (int i = 0; i < 4; i++) {
            float hx = viewportRect.left + pts[i].x * viewportRect.width();
            float hy = viewportRect.top  + pts[i].y * viewportRect.height();
            float d2 = (touchX - hx) * (touchX - hx) + (touchY - hy) * (touchY - hy);
            if (d2 <= bestDistSq) {
                bestDistSq = d2;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    // Precision Numeric Stepper API
    public void adjustSelectedHandle(float deltaX, float deltaY) {
        if (activeRole == null || profile == null) return;
        pushUndoState();
        CameraCalibration cal = profile.getCamera(activeRole);
        Point2D pt = getHandlePoint(cal, selectedHandleIndex);
        pt.x = Math.max(-0.5f, Math.min(1.5f, pt.x + deltaX));
        pt.y = Math.max(-0.5f, Math.min(1.5f, pt.y + deltaY));

        if (engine != null) {
            engine.updateCameraControlPoints(activeRole, cal.p0, cal.p1, cal.p2, cal.p3);
        }
        notifyCalibrationUpdated();
        if (listener != null) {
            listener.onHandleSelected(selectedHandleIndex, pt);
        }
        invalidate();
    }

    public void adjustRotation(float deltaDeg) {
        if (activeRole == null || profile == null) return;
        pushUndoState();
        CameraCalibration cal = profile.getCamera(activeRole);
        cal.fineRotationDeg += deltaDeg;
        notifyCalibrationUpdated();
        invalidate();
    }

    public void adjustScale(float deltaScale) {
        if (activeRole == null || profile == null) return;
        pushUndoState();
        CameraCalibration cal = profile.getCamera(activeRole);
        cal.scaleX = Math.max(0.1f, Math.min(3.0f, cal.scaleX + deltaScale));
        cal.scaleY = Math.max(0.1f, Math.min(3.0f, cal.scaleY + deltaScale));
        notifyCalibrationUpdated();
        invalidate();
    }

    public void adjustOffset(float deltaX, float deltaY) {
        if (activeRole == null || profile == null) return;
        pushUndoState();
        CameraCalibration cal = profile.getCamera(activeRole);
        cal.offsetX += deltaX;
        cal.offsetY += deltaY;
        notifyCalibrationUpdated();
        invalidate();
    }

    public void adjustDistortion(float deltaK1) {
        if (activeRole == null || profile == null) return;
        pushUndoState();
        CameraCalibration cal = profile.getCamera(activeRole);
        cal.distortionK1 += deltaK1;
        notifyCalibrationUpdated();
        invalidate();
    }

    public void resetCurrentCamera() {
        if (activeRole == null || profile == null) return;
        pushUndoState();
        CameraCalibration cal = profile.getCamera(activeRole);
        cal.p0 = new Point2D(0.0f, 0.0f);
        cal.p1 = new Point2D(1.0f, 0.0f);
        cal.p2 = new Point2D(1.0f, 1.0f);
        cal.p3 = new Point2D(0.0f, 1.0f);
        cal.fineRotationDeg = 0.0f;
        cal.scaleX = 1.0f;
        cal.scaleY = 1.0f;
        cal.offsetX = 0.0f;
        cal.offsetY = 0.0f;
        cal.distortionK1 = -0.15f;

        if (engine != null) {
            engine.updateCameraControlPoints(activeRole, cal.p0, cal.p1, cal.p2, cal.p3);
        }
        notifyCalibrationUpdated();
        invalidate();
    }

    // Undo / Redo System
    public void pushUndoState() {
        if (activeRole == null || profile == null) return;
        CameraCalibration copy = profile.getCamera(activeRole).copy();
        undoStack.push(copy);
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        redoStack.clear();
        notifyHistoryChanged();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (!canUndo() || activeRole == null || profile == null) return;
        CameraCalibration current = profile.getCamera(activeRole).copy();
        redoStack.push(current);

        CameraCalibration prev = undoStack.pop();
        applyCalibration(activeRole, prev);
        notifyHistoryChanged();
        invalidate();
    }

    public void redo() {
        if (!canRedo() || activeRole == null || profile == null) return;
        CameraCalibration current = profile.getCamera(activeRole).copy();
        undoStack.push(current);

        CameraCalibration next = redoStack.pop();
        applyCalibration(activeRole, next);
        notifyHistoryChanged();
        invalidate();
    }

    private void applyCalibration(CameraRole role, CameraCalibration state) {
        CameraCalibration target = profile.getCamera(role);
        target.p0 = state.p0.copy();
        target.p1 = state.p1.copy();
        target.p2 = state.p2.copy();
        target.p3 = state.p3.copy();
        target.fineRotationDeg = state.fineRotationDeg;
        target.scaleX = state.scaleX;
        target.scaleY = state.scaleY;
        target.offsetX = state.offsetX;
        target.offsetY = state.offsetY;
        target.distortionK1 = state.distortionK1;

        if (engine != null) {
            engine.updateCameraControlPoints(role, target.p0, target.p1, target.p2, target.p3);
        }
        notifyCalibrationUpdated();
    }

    private void notifyCalibrationUpdated() {
        if (listener != null && activeRole != null && profile != null) {
            listener.onCalibrationChanged(activeRole, profile.getCamera(activeRole));
        }
    }

    private void notifyHistoryChanged() {
        if (listener != null) {
            listener.onHistoryStateChanged(canUndo(), canRedo());
        }
    }

    private Point2D getHandlePoint(CameraCalibration cal, int index) {
        switch (index) {
            case 0: return cal.p0;
            case 1: return cal.p1;
            case 2: return cal.p2;
            case 3: return cal.p3;
            default: return cal.p0;
        }
    }

    private void setHandlePoint(CameraCalibration cal, int index, float x, float y) {
        switch (index) {
            case 0: cal.p0.x = x; cal.p0.y = y; break;
            case 1: cal.p1.x = x; cal.p1.y = y; break;
            case 2: cal.p2.x = x; cal.p2.y = y; break;
            case 3: cal.p3.x = x; cal.p3.y = y; break;
        }
    }
}
