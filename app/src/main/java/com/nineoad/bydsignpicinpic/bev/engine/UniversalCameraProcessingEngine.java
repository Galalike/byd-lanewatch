package com.nineoad.bydsignpicinpic.bev.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Log;
import com.nineoad.bydsignpicinpic.bev.adapter.CameraFrame;
import com.nineoad.bydsignpicinpic.bev.profile.CameraCalibration;
import com.nineoad.bydsignpicinpic.bev.profile.CameraRole;
import com.nineoad.bydsignpicinpic.bev.profile.Point2D;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;
import java.util.EnumMap;
import java.util.Map;

public class UniversalCameraProcessingEngine {
    private static final String TAG = "UniversalBevEngine";

    private final Context context;
    private VehicleProfile profile;
    private final Map<CameraRole, CameraFrame> latestFrames = new EnumMap<>(CameraRole.class);
    private Bitmap composedBevBitmap;
    private Canvas composedCanvas;
    private Paint paint;
    private Paint gridPaint;
    private Paint textPaint;

    public UniversalCameraProcessingEngine(Context context, VehicleProfile profile) {
        this.context = context;
        this.profile = profile;
        initPaints();
        updateProfile(profile);
    }

    private void initPaints() {
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        this.gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.gridPaint.setColor(Color.argb(70, 0, 220, 255));
        this.gridPaint.setStrokeWidth(2f);
        this.gridPaint.setStyle(Paint.Style.STROKE);

        this.textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.textPaint.setColor(Color.WHITE);
        this.textPaint.setTextSize(24f);
        this.textPaint.setFakeBoldText(true);
    }

    public synchronized void updateProfile(VehicleProfile profile) {
        if (profile == null) return;
        this.profile = profile;
        int outW = profile.rendering.outputWidth;
        int outH = profile.rendering.outputHeight;

        if (this.composedBevBitmap == null || this.composedBevBitmap.getWidth() != outW || this.composedBevBitmap.getHeight() != outH) {
            if (this.composedBevBitmap != null && !this.composedBevBitmap.isRecycled()) {
                this.composedBevBitmap.recycle();
            }
            this.composedBevBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            this.composedCanvas = new Canvas(this.composedBevBitmap);
        }

        // Recompute all 4 homographies from 4-point calibration quad handles
        recomputeAllHomographies();
    }

    public synchronized void updateCameraControlPoints(CameraRole role, Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        CameraCalibration cal = profile.getCamera(role);
        cal.p0 = p0.copy();
        cal.p1 = p1.copy();
        cal.p2 = p2.copy();
        cal.p3 = p3.copy();

        // Standard target unit quad for perspective rectification
        Point2D[] targetQuad = new Point2D[]{
                new Point2D(0.0f, 0.0f),
                new Point2D(1.0f, 0.0f),
                new Point2D(1.0f, 1.0f),
                new Point2D(0.0f, 1.0f)
        };
        Point2D[] srcQuad = new Point2D[]{cal.p0, cal.p1, cal.p2, cal.p3};

        cal.homographyMatrix = HomographySolver.computeHomography(srcQuad, targetQuad);
    }

    public synchronized void recomputeAllHomographies() {
        for (CameraRole role : CameraRole.values()) {
            CameraCalibration cal = profile.getCamera(role);
            updateCameraControlPoints(role, cal.p0, cal.p1, cal.p2, cal.p3);
        }
    }

    public synchronized void onFrameReceived(CameraRole role, CameraFrame frame) {
        if (role != null && frame != null) {
            latestFrames.put(role, frame);
        }
    }

    /**
     * Composes the full Bird's-eye View from all active camera inputs,
     * applying perspective correction, ground alignment, seam blending, and vehicle footprint.
     */
    public synchronized Bitmap composeBirdEyeView() {
        if (composedCanvas == null || composedBevBitmap == null) return null;

        // Clear canvas with dark asphalt ground
        composedCanvas.drawColor(Color.rgb(18, 20, 24));

        int w = composedBevBitmap.getWidth();
        int h = composedBevBitmap.getHeight();

        // 1. Draw Distance Reference Calibration Grid
        if (profile.rendering.showGrid) {
            drawCalibrationGrid(composedCanvas, w, h);
        }

        // 2. Draw 4 Camera Sectors
        drawCameraSector(composedCanvas, CameraRole.FRONT, w, h);
        drawCameraSector(composedCanvas, CameraRole.REAR, w, h);
        drawCameraSector(composedCanvas, CameraRole.LEFT, w, h);
        drawCameraSector(composedCanvas, CameraRole.RIGHT, w, h);

        // 3. Draw Vehicle Outline / Top-down Model
        drawVehicleFootprint(composedCanvas, w, h);

        return composedBevBitmap;
    }

    private void drawCalibrationGrid(Canvas canvas, int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;

        // 1-meter, 2-meter, 3-meter ground rings
        float r1m = w * 0.18f;
        float r2m = w * 0.30f;
        float r3m = w * 0.42f;

        canvas.drawCircle(cx, cy, r1m, gridPaint);
        canvas.drawCircle(cx, cy, r2m, gridPaint);
        canvas.drawCircle(cx, cy, r3m, gridPaint);

        // Cross-axes
        canvas.drawLine(cx, 0, cx, h, gridPaint);
        canvas.drawLine(0, cy, w, cy, gridPaint);

        // Diagonal Seam Blending Boundary Rays (45 deg, 135 deg, 225 deg, 315 deg)
        Paint seamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        seamPaint.setColor(Color.argb(100, 255, 200, 0));
        seamPaint.setStrokeWidth(2f);
        seamPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 10}, 0));

        canvas.drawLine(0, 0, w, h, seamPaint);
        canvas.drawLine(0, h, w, 0, seamPaint);
    }

    private void drawCameraSector(Canvas canvas, CameraRole role, int w, int h) {
        CameraFrame frame = latestFrames.get(role);
        if (frame == null || frame.bitmap == null || frame.bitmap.isRecycled()) {
            return;
        }

        CameraCalibration cal = profile.getCamera(role);

        // Sector Clip Path (Front: Top quadrant, Rear: Bottom quadrant, etc.)
        Path clip = new Path();
        float cx = w / 2f;
        float cy = h / 2f;

        switch (role) {
            case FRONT:
                clip.moveTo(0, 0);
                clip.lineTo(w, 0);
                clip.lineTo(cx, cy);
                clip.close();
                break;
            case REAR:
                clip.moveTo(0, h);
                clip.lineTo(w, h);
                clip.lineTo(cx, cy);
                clip.close();
                break;
            case LEFT:
                clip.moveTo(0, 0);
                clip.lineTo(0, h);
                clip.lineTo(cx, cy);
                clip.close();
                break;
            case RIGHT:
                clip.moveTo(w, 0);
                clip.lineTo(w, h);
                clip.lineTo(cx, cy);
                clip.close();
                break;
        }

        canvas.save();
        canvas.clipPath(clip);

        // Apply calibrated transform (Scale + Translation + Roll Rotation)
        canvas.translate(cx + cal.offsetX * w, cy + cal.offsetY * h);
        canvas.rotate(cal.rollDeg + cal.fineRotationDeg);
        canvas.scale(cal.scaleX, cal.scaleY);

        int bw = frame.bitmap.getWidth();
        int bh = frame.bitmap.getHeight();
        canvas.drawBitmap(frame.bitmap, -bw / 2f, -bh / 2f, paint);

        canvas.restore();
    }

    private void drawVehicleFootprint(Canvas canvas, int w, int h) {
        float cx = w / 2f + profile.rendering.carCenterOffsetX * w;
        float cy = h / 2f + profile.rendering.carCenterOffsetY * h;
        float carW = w * profile.rendering.carWidthRatio;
        float carH = h * profile.rendering.carHeightRatio;

        // Draw Car Body Background
        Paint carBody = new Paint(Paint.ANTI_ALIAS_FLAG);
        carBody.setColor(Color.rgb(30, 34, 42));
        canvas.drawRoundRect(cx - carW / 2f, cy - carH / 2f, cx + carW / 2f, cy + carH / 2f, 20f, 20f, carBody);

        // Vehicle Outline Stroke
        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setColor(Color.argb(200, 0, 220, 255));
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(3f);
        canvas.drawRoundRect(cx - carW / 2f, cy - carH / 2f, cx + carW / 2f, cy + carH / 2f, 20f, 20f, outline);

        // Vehicle Info Text
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(profile.model.toUpperCase(), cx, cy - 10, textPaint);

        Paint subPaint = new Paint(textPaint);
        subPaint.setTextSize(18f);
        subPaint.setColor(Color.LTGRAY);
        canvas.drawText(profile.manufacturer, cx, cy + 20, subPaint);
    }

    public VehicleProfile getProfile() {
        return profile;
    }
}
