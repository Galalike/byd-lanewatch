package com.nineoad.bydsignpicinpic.camera;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.JNIBMMCamera;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.nineoad.bydsignpicinpic.overlay.BevGLView;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Advanced Laboratory for BYD Around-View Monitoring & 360° Bird's-Eye View (BEV).
 * Features live manual fine-tuning panel for matching com.byd.avc perspective, angles, and car dimensions.
 * Includes complete controls for Left and Right camera rotation angles, mirror position offsets, and flips.
 * Top buttons are permanently pinned and guaranteed never to be covered by the camera view.
 */
public class PreviewTestActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "PreviewTestActivity";
    private static final String BUILD_TAG = "BYD Lanewatch v2.7.0 (BEV 360 Full Calibration Assistant & FOV Tuning)";

    private int camId = 1;
    private JNIBMMCamera cam;
    private Surface textureSurface;
    private Surface bevSurface;

    private FrameLayout rootLayout;
    private TextureView texture;
    private FrameLayout bevContainer;
    private BevGLView bevView;
    private ScrollView tuningPanel;
    private TextView status;
    private SharedPreferences prefs;

    // Display modes:
    // 0 = Bird's-Eye View (BEV 360)
    // 1 = Raw 4-split full 1080p
    // 2 = Panel 1 Rear (หลัง)
    // 3 = Panel 2 Left (ซ้าย)
    // 4 = Panel 3 Right (ขวา)
    // 5 = Panel 4 Front (หน้า)
    private int displayMode = 0;
    private String diagText = "";

    // Live tuning labels (for updating text upon +/- clicks)
    private TextView lblZoom;
    private TextView lblCarW;
    private TextView lblCarH;
    private TextView lblCarOffY;
    private TextView lblDistFront;
    private TextView lblScaleFront;
    private TextView lblPitchFront;
    private TextView lblDistRear;
    private TextView lblScaleRear;
    private TextView lblPitchRear;

    // Side camera tuning labels
    private TextView lblRotSideL;
    private TextView lblRotSideR;
    private TextView lblSideOffsetY;
    private TextView lblDistSide;
    private TextView lblScaleSide;
    private TextView lblPitchSide;
    private TextView lblFishSide;
    private TextView lblSeamBlend;
    private TextView lblSeamAngleFront;

    // Camera FOV & Depth calibration labels
    private TextView lblFovFront;
    private TextView lblDepthFront;
    private TextView lblFovRear;
    private TextView lblDepthRear;
    private TextView lblFovSide;
    private TextView lblDepthSide;

    private Button btnToggleTuning;
    private Button btnGrid;
    private Button btnGridPanel;
    private Button btnCycleRotL;
    private Button btnCycleRotR;
    private Button btnFlipFront;
    private Button btnMirrorFront;
    private Button btnFlipFrontPanel;
    private Button btnMirrorFrontPanel;
    private Button btnFlipRear;
    private Button btnMirrorRear;
    private Button btnCarColor;

    private Button btnFlipSideVL;
    private Button btnFlipSideVR;
    private Button btnMirrorSideL;
    private Button btnMirrorSideR;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.prefs = getSharedPreferences("pnp", MODE_PRIVATE);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;

        this.rootLayout = new FrameLayout(this);
        this.rootLayout.setBackgroundColor(0xFF070B14);

        // 1. Raw / Single camera TextureView (background full screen for modes 1-5)
        this.texture = new TextureView(this);
        this.texture.setSurfaceTextureListener(this);
        this.rootLayout.addView(this.texture, new FrameLayout.LayoutParams(-1, -1));

        // 2. Middle Content Area (Between top bar and bottom status bar)
        LinearLayout mainContentLayout = new LinearLayout(this);
        mainContentLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainContentLayout.setGravity(Gravity.CENTER_VERTICAL);
        int topMargin = (int) (52 * density);
        int bottomMargin = (int) (40 * density);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(-1, -1);
        contentLp.setMargins((int) (12 * density), topMargin, (int) (12 * density), bottomMargin);
        this.rootLayout.addView(mainContentLayout, contentLp);

        // 2.1 BEV 360 Portrait PIP Container (Left side, scaled to fit screen height neatly)
        int availH = dm.heightPixels - topMargin - bottomMargin;
        int pipH = Math.max((int) (320 * density), availH - (int) (16 * density));
        int pipW = (int) (pipH * 9.0f / 16.0f); // BYD AVM 9:16 portrait aspect ratio

        this.bevContainer = new FrameLayout(this);
        GradientDrawable border = new GradientDrawable();
        border.setColor(0xEE030712);
        border.setCornerRadius(22 * density);
        border.setStroke((int) (2.5f * density), 0xFF38BDF8); // Glowing Cyan border
        this.bevContainer.setBackground(border);
        this.bevContainer.setPadding((int) (4 * density), (int) (4 * density), (int) (4 * density), (int) (4 * density));

        LinearLayout.LayoutParams bevLp = new LinearLayout.LayoutParams(pipW, pipH);
        bevLp.setMargins((int) (8 * density), 0, (int) (16 * density), 0);
        bevLp.gravity = Gravity.CENTER_VERTICAL;

        this.bevView = new BevGLView(this);
        this.bevView.setCornerRadiusPx(20 * density);
        this.bevView.loadFromPrefs(this.prefs); // Load persistent user calibration
        this.bevView.setSurfaceReadyListener(surface -> {
            PreviewTestActivity.this.bevSurface = surface;
            if (PreviewTestActivity.this.cam != null) {
                try {
                    PreviewTestActivity.this.cam.nativeAddPreviewSurface(surface, 0);
                } catch (Throwable ignored) {
                }
            }
        });
        this.bevContainer.addView(this.bevView, new FrameLayout.LayoutParams(-1, -1));
        mainContentLayout.addView(this.bevContainer, bevLp);

        // 2.2 Fine-Tuning Panel (Right side, scrollable drawer with +/- controls)
        this.tuningPanel = buildTuningPanel(density);
        LinearLayout.LayoutParams tuneLp = new LinearLayout.LayoutParams(0, -1, 1.0f);
        tuneLp.setMargins(0, (int) (4 * density), (int) (8 * density), (int) (4 * density));
        mainContentLayout.addView(this.tuningPanel, tuneLp);

        // 3. Pinned Top Horizontal Toolbar (Fixed at TOP, NEVER covered by BEV view)
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setBackgroundColor(0xF50F172A); // Dark navy slate solid background
        if (Build.VERSION.SDK_INT >= 21) {
            hsv.setElevation(30f);
        }
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding((int) (10 * density), (int) (4 * density), (int) (10 * density), (int) (4 * density));

        // Primary mode buttons
        topBar.addView(btn("🚗 Bird's-Eye View (BEV 360)", 0xFF2563EB, v -> setMode(0)));
        topBar.addView(btn("📺 4-ช่องเต็ม Raw", 0xFF1E293B, v -> setMode(1)));
        topBar.addView(btn("1: หลัง", 0xFF334155, v -> setMode(2)));
        topBar.addView(btn("2: ซ้าย", 0xFF334155, v -> setMode(3)));
        topBar.addView(btn("3: ขวา", 0xFF334155, v -> setMode(4)));
        topBar.addView(btn("4: หน้า", 0xFF334155, v -> setMode(5)));

        // Toggle Tuning Panel button
        this.btnToggleTuning = btn("⚙️ แผงปรับละเอียด: เปิด", 0xFF0D9488, v -> {
            boolean isVis = this.tuningPanel.getVisibility() == View.VISIBLE;
            this.tuningPanel.setVisibility(isVis ? View.GONE : View.VISIBLE);
            this.btnToggleTuning.setText(isVis ? "⚙️ แผงปรับละเอียด: ปิด" : "⚙️ แผงปรับละเอียด: เปิด");
        });
        topBar.addView(this.btnToggleTuning);

        // Toggle Calibration Grid button
        this.btnGrid = btn("📐 ตารางคาลิเบรต: " + (this.bevView.isShowGrid() ? "เปิด" : "ปิด"), 0xFF0891B2, v -> {
            this.bevView.toggleGrid();
            boolean on = this.bevView.isShowGrid();
            this.btnGrid.setText("📐 ตารางคาลิเบรต: " + (on ? "เปิด" : "ปิด"));
            if (this.btnGridPanel != null) {
                this.btnGridPanel.setText("📐 เส้นตารางช่วยเล็งคาลิเบรต: " + (on ? "เปิด" : "ปิด"));
            }
            Toast.makeText(this, on ? "📐 เปิดเส้นตารางเล็งคาลิเบรต (Grid On)" : "ปิดเส้นตาราง", Toast.LENGTH_SHORT).show();
        });
        topBar.addView(this.btnGrid);

        // Quick 90-degree side rotation buttons right on the top bar
        this.btnCycleRotL = btn("🔄 หมุนซ้าย: " + ((int) this.bevView.getRotSideDegL()) + "°", 0xFF0284C7, v -> {
            this.bevView.cycleRotLeft();
            refreshTuningLabels();
            Toast.makeText(this, "หมุนกล้องซ้าย: " + ((int) this.bevView.getRotSideDegL()) + "°", Toast.LENGTH_SHORT).show();
        });
        topBar.addView(this.btnCycleRotL);

        this.btnCycleRotR = btn("🔄 หมุนขวา: " + ((int) this.bevView.getRotSideDegR()) + "°", 0xFF0284C7, v -> {
            this.bevView.cycleRotRight();
            refreshTuningLabels();
            Toast.makeText(this, "หมุนกล้องขวา: " + ((int) this.bevView.getRotSideDegR()) + "°", Toast.LENGTH_SHORT).show();
        });
        topBar.addView(this.btnCycleRotR);

        // Front camera controls (Flip V / Mirror)
        this.btnFlipFront = btn("🔄 ตีลังกาหน้า: " + (this.bevView.isFlipFrontV() ? "เปิด" : "ปิด"), 0xFFE11D48, v -> {
            boolean cur = this.bevView.isFlipFrontV();
            this.bevView.setFlipFrontV(!cur);
            this.btnFlipFront.setText("🔄 ตีลังกาหน้า: " + (!cur ? "เปิด" : "ปิด"));
            if (this.btnFlipFrontPanel != null) {
                this.btnFlipFrontPanel.setText("↕️ ตีลังกาหน้า: " + (!cur ? "เปิด" : "ปิด"));
            }
            updateStatusText();
        });
        topBar.addView(this.btnFlipFront);

        this.btnMirrorFront = btn("↔️ กลับซ้ายขวาหน้า: " + (this.bevView.isMirrorFront() ? "เปิด" : "ปิด"), 0xFFD97706, v -> {
            boolean cur = this.bevView.isMirrorFront();
            this.bevView.setMirrorFront(!cur);
            this.btnMirrorFront.setText("↔️ กลับซ้ายขวาหน้า: " + (!cur ? "เปิด" : "ปิด"));
            if (this.btnMirrorFrontPanel != null) {
                this.btnMirrorFrontPanel.setText("↔️ กลับซ้ายขวาหน้า: " + (!cur ? "เปิด" : "ปิด"));
            }
            updateStatusText();
        });
        topBar.addView(this.btnMirrorFront);

        // Rear camera controls (Flip V / Mirror)
        this.btnFlipRear = btn("🔄 ตีลังกาหลัง: " + (this.bevView.isFlipRearV() ? "เปิด" : "ปิด"), 0xFFE11D48, v -> {
            boolean cur = this.bevView.isFlipRearV();
            this.bevView.setFlipRearV(!cur);
            this.btnFlipRear.setText("🔄 ตีลังกาหลัง: " + (!cur ? "เปิด" : "ปิด"));
            updateStatusText();
        });
        topBar.addView(this.btnFlipRear);

        this.btnMirrorRear = btn("↔️ กลับซ้ายขวาหลัง: " + (this.bevView.isMirrorRear() ? "เปิด" : "ปิด"), 0xFFD97706, v -> {
            boolean cur = this.bevView.isMirrorRear();
            this.bevView.setMirrorRear(!cur);
            this.btnMirrorRear.setText("↔️ กลับซ้ายขวา: " + (!cur ? "เปิด" : "ปิด"));
            updateStatusText();
        });
        topBar.addView(this.btnMirrorRear);

        // Car model selection (Silver-Gray with roof vs White with roof)
        String colorName = (this.bevView.getCarModelColor() == 1) ? "ขาวมุก" : "บรอนซ์เงิน";
        this.btnCarColor = btn("🎨 โมเดลรถ: " + colorName, 0xFF4F46E5, v -> {
            int cur = this.bevView.getCarModelColor();
            int next = (cur == 0) ? 1 : 0;
            this.bevView.setCarModelColor(next);
            this.btnCarColor.setText("🎨 โมเดลรถ: " + (next == 1 ? "ขาวมุก" : "บรอนซ์เงิน"));
            updateStatusText();
        });
        topBar.addView(this.btnCarColor);

        // Quick Save & Reset in Top Bar
        topBar.addView(btn("💾 บันทึกค่า", 0xFF16A34A, v -> {
            this.bevView.saveToPrefs(this.prefs);
            Toast.makeText(this, "💾 บันทึกการตั้งค่า BEV 360 สำเร็จ!", Toast.LENGTH_SHORT).show();
        }));

        topBar.addView(btn("🔄 คืนค่า BYD", 0xFF64748B, v -> {
            this.bevView.resetDefaults();
            refreshTuningLabels();
            Toast.makeText(this, "🔄 คืนค่ามาตรฐานเริ่มต้นเรียบร้อย", Toast.LENGTH_SHORT).show();
        }));

        topBar.addView(btn("⏹ Stop", 0xFFDC2626, v -> closeCam()));

        hsv.addView(topBar);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, (int) (48 * density), Gravity.TOP);
        this.rootLayout.addView(hsv, topLp);
        hsv.bringToFront();

        // 4. Status Bar at bottom
        TextView textView = new TextView(this);
        this.status = textView;
        textView.setTextColor(0xFFE2E8F0);
        textView.setTextSize(11.5f);
        textView.setBackgroundColor(0xF00B0F19);
        textView.setPadding((int) (18 * density), (int) (6 * density), (int) (18 * density), (int) (6 * density));
        if (Build.VERSION.SDK_INT >= 21) {
            textView.setElevation(30f);
        }
        FrameLayout.LayoutParams botLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        this.rootLayout.addView(this.status, botLp);
        textView.bringToFront();

        setContentView(this.rootLayout);

        this.diagText = collectDiagnostics();
        refreshTuningLabels();
        setMode(0); // Default to Bird's-Eye View
    }

    private ScrollView buildTuningPanel(float density) {
        ScrollView sv = new ScrollView(this);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xED0B1120);
        panelBg.setCornerRadius(18 * density);
        panelBg.setStroke((int) (1.5f * density), 0xFF334155);
        sv.setBackground(panelBg);
        sv.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        // Header Title
        TextView header = new TextView(this);
        header.setText("⚙️ ปรับแต่งมุมมอง & สัดส่วนกล้อง BEV 360 (Live Calibration Assistant)");
        header.setTextColor(0xFF38BDF8);
        header.setTextSize(13.5f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, (int) (6 * density));
        content.addView(header);

        // Section 0: Presets & Calibration Grid
        content.addView(createSectionHeader("🎯 ค่าพรีเซ็ตสำเร็จรูปตามรุ่นรถ (Car Presets)", density));

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(0, (int) (2 * density), 0, (int) (4 * density));

        Button btnAtto = btn("🚙 Atto 3", 0xFF0369A1, v -> {
            this.bevView.applyPreset(0);
            refreshTuningLabels();
            Toast.makeText(this, "🚙 โหลดพรีเซ็ต BYD Atto 3 / Yuan Plus สำเร็จ", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams p1Lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        p1Lp.setMargins(0, 0, (int) (3 * density), 0);
        btnAtto.setLayoutParams(p1Lp);
        presetRow.addView(btnAtto);

        Button btnDolphin = btn("🐬 Dolphin", 0xFF0284C7, v -> {
            this.bevView.applyPreset(1);
            refreshTuningLabels();
            Toast.makeText(this, "🐬 โหลดพรีเซ็ต BYD Dolphin สำเร็จ", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams p2Lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        p2Lp.setMargins((int) (2 * density), 0, (int) (2 * density), 0);
        btnDolphin.setLayoutParams(p2Lp);
        presetRow.addView(btnDolphin);

        Button btnSeal = btn("🦭 Seal", 0xFF0D9488, v -> {
            this.bevView.applyPreset(2);
            refreshTuningLabels();
            Toast.makeText(this, "🦭 โหลดพรีเซ็ต BYD Seal สำเร็จ", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams p3Lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        p3Lp.setMargins((int) (2 * density), 0, (int) (3 * density), 0);
        btnSeal.setLayoutParams(p3Lp);
        presetRow.addView(btnSeal);

        Button btnDefault = btn("🔄 มาตรฐาน", 0xFF475569, v -> {
            this.bevView.applyPreset(3);
            refreshTuningLabels();
            Toast.makeText(this, "🔄 คืนค่ามาตรฐานโรงงานสำเร็จ", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams p4Lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        p4Lp.setMargins((int) (2 * density), 0, 0, 0);
        btnDefault.setLayoutParams(p4Lp);
        presetRow.addView(btnDefault);

        content.addView(presetRow);

        this.btnGridPanel = btn("📐 เส้นตารางช่วยเล็งคาลิเบรต: " + (this.bevView.isShowGrid() ? "เปิด" : "ปิด"), 0xFF0891B2, v -> {
            this.bevView.toggleGrid();
            boolean on = this.bevView.isShowGrid();
            this.btnGridPanel.setText("📐 เส้นตารางช่วยเล็งคาลิเบรต: " + (on ? "เปิด" : "ปิด"));
            if (this.btnGrid != null) {
                this.btnGrid.setText("📐 ตารางคาลิเบรต: " + (on ? "เปิด" : "ปิด"));
            }
        });
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.setMargins(0, (int) (3 * density), 0, (int) (6 * density));
        this.btnGridPanel.setLayoutParams(gridLp);
        content.addView(this.btnGridPanel);

        // Section 1: ภาพรวมและตัวรถ
        content.addView(createSectionHeader("📏 สัดส่วนภาพรวม & ตัวรถ", density));

        this.lblZoom = new TextView(this);
        content.addView(createTuneRow("ซูมรวม (Zoom)", this.lblZoom, density,
                () -> this.bevView.getZoom(),
                v -> { this.bevView.setZoom(v); updateStatusText(); },
                0.05f, 0.40f, 2.50f, "%.2f"));

        this.lblCarW = new TextView(this);
        content.addView(createTuneRow("ความกว้างรถ (Car Width)", this.lblCarW, density,
                () -> this.bevView.getCarWidth(),
                v -> { this.bevView.setCarDimensions(v, this.bevView.getCarHeight()); updateStatusText(); },
                0.01f, 0.15f, 0.45f, "%.2f"));

        this.lblCarH = new TextView(this);
        content.addView(createTuneRow("ความยาวรถ (Car Length)", this.lblCarH, density,
                () -> this.bevView.getCarHeight(),
                v -> { this.bevView.setCarDimensions(this.bevView.getCarWidth(), v); updateStatusText(); },
                0.02f, 0.30f, 0.85f, "%.2f"));

        this.lblCarOffY = new TextView(this);
        content.addView(createTuneRow("ขยับรถหน้า-หลัง (Offset Y)", this.lblCarOffY, density,
                () -> this.bevView.getCarOffsetY(),
                v -> { this.bevView.setCarOffsetY(v); updateStatusText(); },
                0.01f, -0.15f, 0.15f, "%+.2f"));

        this.lblSeamBlend = new TextView(this);
        content.addView(createTuneRow("รอยต่อมุม 4 ด้าน (Seam Blend)", this.lblSeamBlend, density,
                () -> this.bevView.getSeamBlend(),
                v -> { this.bevView.setSeamBlend(v); updateStatusText(); },
                0.05f, 0.40f, 1.80f, "%.2f"));

        this.lblSeamAngleFront = new TextView(this);
        content.addView(createTuneRow("📐 องศาผายรอยต่อหน้า (Seam Angle)", this.lblSeamAngleFront, density,
                () -> this.bevView.getSeamAngleFront(),
                v -> { this.bevView.setSeamAngleFront(v); updateStatusText(); },
                0.05f, 0.20f, 2.50f, "%.2f"));

        // Section 2: กล้องข้างซ้าย-ขวา (สำคัญที่สุด: องศาและตำแหน่ง)
        content.addView(createSectionHeader("↔️ กล้องข้างซ้าย-ขวา (Side Cameras, ช่อง 2 & 3)", density));

        // Quick 90 deg step row for Left & Right side cameras
        LinearLayout quickRotRow = new LinearLayout(this);
        quickRotRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRotRow.setPadding(0, (int) (3 * density), 0, (int) (4 * density));

        Button btnRotL90 = btn("🔄 หมุนซ้ายทีละ 90°", 0xFF0284C7, v -> {
            this.bevView.cycleRotLeft();
            refreshTuningLabels();
        });
        LinearLayout.LayoutParams btnRotLLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        btnRotLLp.setMargins(0, 0, (int) (4 * density), 0);
        btnRotL90.setLayoutParams(btnRotLLp);
        quickRotRow.addView(btnRotL90);

        Button btnRotR90 = btn("🔄 หมุนขวาทีละ 90°", 0xFF0284C7, v -> {
            this.bevView.cycleRotRight();
            refreshTuningLabels();
        });
        LinearLayout.LayoutParams btnRotRLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        btnRotRLp.setMargins((int) (4 * density), 0, 0, 0);
        btnRotR90.setLayoutParams(btnRotRLp);
        quickRotRow.addView(btnRotR90);
        content.addView(quickRotRow);

        this.lblRotSideL = new TextView(this);
        content.addView(createTuneRow("องศาหมุนกล้องซ้าย (Rot Left)", this.lblRotSideL, density,
                () -> this.bevView.getRotSideDegL(),
                v -> { this.bevView.setRotSideDegL(v); updateStatusText(); },
                5.0f, -180.0f, 360.0f, "%.0f°"));

        this.lblRotSideR = new TextView(this);
        content.addView(createTuneRow("องศาหมุนกล้องขวา (Rot Right)", this.lblRotSideR, density,
                () -> this.bevView.getRotSideDegR(),
                v -> { this.bevView.setRotSideDegR(v); updateStatusText(); },
                5.0f, -180.0f, 360.0f, "%.0f°"));

        this.lblSideOffsetY = new TextView(this);
        content.addView(createTuneRow("ตำแหน่งกระจก หน้า-หลัง (Mirror Y)", this.lblSideOffsetY, density,
                () -> this.bevView.getSideCamOffsetY(),
                v -> { this.bevView.setSideCamOffsetY(v); updateStatusText(); },
                0.02f, -0.40f, 0.40f, "%+.2f"));

        // Side Flips & Mirrors Row
        LinearLayout sideFlipRow = new LinearLayout(this);
        sideFlipRow.setOrientation(LinearLayout.HORIZONTAL);
        sideFlipRow.setPadding(0, (int) (2 * density), 0, (int) (4 * density));

        this.btnFlipSideVL = btn("↕️ ตีลังกาซ้าย: ปิด", 0xFF334155, v -> {
            boolean cur = this.bevView.isFlipSideVL();
            this.bevView.setFlipSideVL(!cur);
            this.btnFlipSideVL.setText("↕️ ตีลังกาซ้าย: " + (!cur ? "เปิด" : "ปิด"));
            updateStatusText();
        });
        LinearLayout.LayoutParams fvlLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        fvlLp.setMargins(0, 0, (int) (2 * density), 0);
        this.btnFlipSideVL.setLayoutParams(fvlLp);
        sideFlipRow.addView(this.btnFlipSideVL);

        this.btnFlipSideVR = btn("↕️ ตีลังกาขวา: ปิด", 0xFF334155, v -> {
            boolean cur = this.bevView.isFlipSideVR();
            this.bevView.setFlipSideVR(!cur);
            this.btnFlipSideVR.setText("↕️ ตีลังกาขวา: " + (!cur ? "เปิด" : "ปิด"));
            updateStatusText();
        });
        LinearLayout.LayoutParams fvrLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        fvrLp.setMargins((int) (2 * density), 0, (int) (2 * density), 0);
        this.btnFlipSideVR.setLayoutParams(fvrLp);
        sideFlipRow.addView(this.btnFlipSideVR);

        this.btnMirrorSideL = btn("↔️ กลับซ้าย: ปิด", 0xFF334155, v -> {
            boolean cur = this.bevView.isMirrorSideL();
            this.bevView.setMirrorSideL(!cur);
            this.btnMirrorSideL.setText("↔️ กลับซ้าย: " + (!cur ? "เปิด" : "ปิด"));
            updateStatusText();
        });
        LinearLayout.LayoutParams mslLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        mslLp.setMargins((int) (2 * density), 0, (int) (2 * density), 0);
        this.btnMirrorSideL.setLayoutParams(mslLp);
        sideFlipRow.addView(this.btnMirrorSideL);

        this.btnMirrorSideR = btn("↔️ กลับขวา: ปิด", 0xFF334155, v -> {
            boolean cur = this.bevView.isMirrorSideR();
            this.bevView.setMirrorSideR(!cur);
            this.btnMirrorSideR.setText("↔️ กลับขวา: " + (!cur ? "เปิด" : "ปิด"));
            updateStatusText();
        });
        LinearLayout.LayoutParams msrLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        msrLp.setMargins((int) (2 * density), 0, 0, 0);
        this.btnMirrorSideR.setLayoutParams(msrLp);
        sideFlipRow.addView(this.btnMirrorSideR);
        content.addView(sideFlipRow);

        this.lblDistSide = new TextView(this);
        content.addView(createTuneRow("ระยะมุมกดกล้องข้าง (Dist z₀)", this.lblDistSide, density,
                () -> this.bevView.getDistSide(),
                v -> { this.bevView.setDistSide(v); updateStatusText(); },
                0.01f, -0.15f, 0.25f, "%+.2f"));

        this.lblFovSide = new TextView(this);
        content.addView(createTuneRow("ความกว้างมุมมองข้าง (Side FOV)", this.lblFovSide, density,
                () -> this.bevView.getFovSide(),
                v -> { this.bevView.setFovSide(v); updateStatusText(); },
                0.005f, 0.020f, 0.250f, "%.3f"));

        this.lblDepthSide = new TextView(this);
        content.addView(createTuneRow("ความลึกขอบฟ้าข้าง (Side Depth)", this.lblDepthSide, density,
                () -> this.bevView.getDepthSide(),
                v -> { this.bevView.setDepthSide(v); updateStatusText(); },
                0.005f, 0.010f, 0.200f, "%.3f"));

        this.lblScaleSide = new TextView(this);
        content.addView(createTuneRow("สเกลกว้างกล้องข้าง (Scale)", this.lblScaleSide, density,
                () -> this.bevView.getScaleSide(),
                v -> { this.bevView.setScaleSide(v); updateStatusText(); },
                0.02f, 0.50f, 1.80f, "%.2f"));

        this.lblPitchSide = new TextView(this);
        content.addView(createTuneRow("ขยับขอบฟ้ากล้องข้าง (Pitch Y)", this.lblPitchSide, density,
                () -> this.bevView.getPitchSide(),
                v -> { this.bevView.setPitchSide(v); updateStatusText(); },
                0.01f, -0.15f, 0.15f, "%+.2f"));

        this.lblFishSide = new TextView(this);
        content.addView(createTuneRow("🐟 แก้ตาปลาข้าง/ดัดเส้นโค้ง (Fisheye k)", this.lblFishSide, density,
                () -> this.bevView.getFishSide(),
                v -> { this.bevView.setFishSide(v); updateStatusText(); },
                0.05f, -2.00f, 2.00f, "%+.2f"));

        // Section 3: กล้องหน้า
        content.addView(createSectionHeader("🔼 กล้องหน้า (Front Camera, ช่อง 4)", density));

        this.lblFovFront = new TextView(this);
        content.addView(createTuneRow("ความกว้างมุมมองหน้า (Front FOV)", this.lblFovFront, density,
                () -> this.bevView.getFovFront(),
                v -> { this.bevView.setFovFront(v); updateStatusText(); },
                0.005f, 0.020f, 0.250f, "%.3f"));

        this.lblDepthFront = new TextView(this);
        content.addView(createTuneRow("ความลึกขอบฟ้าหน้า (Front Depth)", this.lblDepthFront, density,
                () -> this.bevView.getDepthFront(),
                v -> { this.bevView.setDepthFront(v); updateStatusText(); },
                0.005f, 0.010f, 0.200f, "%.3f"));

        this.lblDistFront = new TextView(this);
        content.addView(createTuneRow("ระยะมุมกด (Dist z₀)", this.lblDistFront, density,
                () -> this.bevView.getDistFront(),
                v -> { this.bevView.setDistFront(v); updateStatusText(); },
                0.01f, -0.15f, 0.25f, "%+.2f"));

        this.lblScaleFront = new TextView(this);
        content.addView(createTuneRow("สเกลความกว้าง (Scale X)", this.lblScaleFront, density,
                () -> this.bevView.getScaleFront(),
                v -> { this.bevView.setScaleFront(v); updateStatusText(); },
                0.02f, 0.50f, 1.80f, "%.2f"));

        this.lblPitchFront = new TextView(this);
        content.addView(createTuneRow("ขยับขอบฟ้า (Pitch Y)", this.lblPitchFront, density,
                () -> this.bevView.getPitchFront(),
                v -> { this.bevView.setPitchFront(v); updateStatusText(); },
                0.01f, -0.15f, 0.15f, "%+.2f"));

        // Front Flips & Mirrors Row
        LinearLayout frontFlipRow = new LinearLayout(this);
        frontFlipRow.setOrientation(LinearLayout.HORIZONTAL);
        frontFlipRow.setPadding(0, (int) (2 * density), 0, (int) (4 * density));

        this.btnFlipFrontPanel = btn("↕️ ตีลังกาหน้า: " + (this.bevView.isFlipFrontV() ? "เปิด" : "ปิด"), 0xFF334155, v -> {
            boolean cur = this.bevView.isFlipFrontV();
            this.bevView.setFlipFrontV(!cur);
            this.btnFlipFrontPanel.setText("↕️ ตีลังกาหน้า: " + (!cur ? "เปิด" : "ปิด"));
            if (this.btnFlipFront != null) {
                this.btnFlipFront.setText("🔄 ตีลังกาหน้า: " + (!cur ? "เปิด" : "ปิด"));
            }
            updateStatusText();
        });
        LinearLayout.LayoutParams ffpLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        ffpLp.setMargins(0, 0, (int) (2 * density), 0);
        this.btnFlipFrontPanel.setLayoutParams(ffpLp);
        frontFlipRow.addView(this.btnFlipFrontPanel);

        this.btnMirrorFrontPanel = btn("↔️ กลับซ้ายขวาหน้า: " + (this.bevView.isMirrorFront() ? "เปิด" : "ปิด"), 0xFF334155, v -> {
            boolean cur = this.bevView.isMirrorFront();
            this.bevView.setMirrorFront(!cur);
            this.btnMirrorFrontPanel.setText("↔️ กลับซ้ายขวาหน้า: " + (!cur ? "เปิด" : "ปิด"));
            if (this.btnMirrorFront != null) {
                this.btnMirrorFront.setText("↔️ กลับซ้ายขวาหน้า: " + (!cur ? "เปิด" : "ปิด"));
            }
            updateStatusText();
        });
        LinearLayout.LayoutParams mfpLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        mfpLp.setMargins((int) (2 * density), 0, 0, 0);
        this.btnMirrorFrontPanel.setLayoutParams(mfpLp);
        frontFlipRow.addView(this.btnMirrorFrontPanel);
        content.addView(frontFlipRow);

        // Section 4: กล้องหลัง
        content.addView(createSectionHeader("🔽 กล้องหลัง (Rear Camera, ช่อง 1)", density));

        this.lblFovRear = new TextView(this);
        content.addView(createTuneRow("ความกว้างมุมมองหลัง (Rear FOV)", this.lblFovRear, density,
                () -> this.bevView.getFovRear(),
                v -> { this.bevView.setFovRear(v); updateStatusText(); },
                0.005f, 0.020f, 0.250f, "%.3f"));

        this.lblDepthRear = new TextView(this);
        content.addView(createTuneRow("ความลึกขอบฟ้าหลัง (Rear Depth)", this.lblDepthRear, density,
                () -> this.bevView.getDepthRear(),
                v -> { this.bevView.setDepthRear(v); updateStatusText(); },
                0.005f, 0.010f, 0.200f, "%.3f"));

        this.lblDistRear = new TextView(this);
        content.addView(createTuneRow("ระยะมุมกด (Dist z₀)", this.lblDistRear, density,
                () -> this.bevView.getDistRear(),
                v -> { this.bevView.setDistRear(v); updateStatusText(); },
                0.01f, -0.15f, 0.25f, "%+.2f"));

        this.lblScaleRear = new TextView(this);
        content.addView(createTuneRow("สเกลความกว้าง (Scale X)", this.lblScaleRear, density,
                () -> this.bevView.getScaleRear(),
                v -> { this.bevView.setScaleRear(v); updateStatusText(); },
                0.02f, 0.50f, 1.80f, "%.2f"));

        this.lblPitchRear = new TextView(this);
        content.addView(createTuneRow("ขยับขอบฟ้า (Pitch Y)", this.lblPitchRear, density,
                () -> this.bevView.getPitchRear(),
                v -> { this.bevView.setPitchRear(v); updateStatusText(); },
                0.01f, -0.15f, 0.15f, "%+.2f"));

        // Action Save & Reset Buttons at bottom of panel
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, (int) (12 * density), 0, (int) (8 * density));

        Button btnSave = btn("💾 บันทึกค่าลงระบบ (Save)", 0xFF16A34A, v -> {
            this.bevView.saveToPrefs(this.prefs);
            Toast.makeText(this, "💾 บันทึกการตั้งค่า BEV 360 สำเร็จ!", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams btnSaveLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        btnSaveLp.setMargins(0, 0, (int) (6 * density), 0);
        btnSave.setLayoutParams(btnSaveLp);
        actionRow.addView(btnSave);

        Button btnReset = btn("🔄 คืนค่าเริ่มต้น (Reset)", 0xFF475569, v -> {
            this.bevView.resetDefaults();
            refreshTuningLabels();
            Toast.makeText(this, "🔄 คืนค่ามาตรฐานเริ่มต้นเรียบร้อย", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams btnResetLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        btnResetLp.setMargins((int) (6 * density), 0, 0, 0);
        btnReset.setLayoutParams(btnResetLp);
        actionRow.addView(btnReset);

        content.addView(actionRow);

        sv.addView(content);
        return sv;
    }

    private TextView createSectionHeader(String title, float density) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(0xFF94A3B8);
        tv.setTextSize(12f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, (int) (8 * density), 0, (int) (3 * density));
        return tv;
    }

    interface ValueGetter { float get(); }
    interface ValueSetter { void set(float v); }

    private LinearLayout createTuneRow(String title, TextView valueLabel, float density,
                                       ValueGetter getter, ValueSetter setter,
                                       float step, float min, float max, String format) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (2 * density), 0, (int) (2 * density));

        // Title
        TextView lblTitle = new TextView(this);
        lblTitle.setText(title);
        lblTitle.setTextColor(0xFFE2E8F0);
        lblTitle.setTextSize(11.5f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        lblTitle.setLayoutParams(titleLp);
        row.addView(lblTitle);

        // Minus button
        Button btnMinus = new Button(this);
        btnMinus.setText(" - ");
        btnMinus.setTextSize(13f);
        btnMinus.setTextColor(0xFFFFFFFF);
        btnMinus.setBackgroundColor(0xFF334155);
        LinearLayout.LayoutParams minusLp = new LinearLayout.LayoutParams((int) (40 * density), (int) (32 * density));
        minusLp.setMargins((int) (2 * density), 0, (int) (4 * density), 0);
        btnMinus.setLayoutParams(minusLp);
        btnMinus.setPadding(0, 0, 0, 0);
        btnMinus.setOnClickListener(v -> {
            float newVal = Math.max(min, getter.get() - step);
            setter.set(newVal);
            valueLabel.setText(String.format(Locale.US, format, newVal));
        });
        row.addView(btnMinus);

        // Value display
        valueLabel.setText(String.format(Locale.US, format, getter.get()));
        valueLabel.setTextColor(0xFF38BDF8);
        valueLabel.setTextSize(12f);
        valueLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        valueLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams((int) (56 * density), -2);
        valueLabel.setLayoutParams(valLp);
        row.addView(valueLabel);

        // Plus button
        Button btnPlus = new Button(this);
        btnPlus.setText(" + ");
        btnPlus.setTextSize(13f);
        btnPlus.setTextColor(0xFFFFFFFF);
        btnPlus.setBackgroundColor(0xFF334155);
        LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams((int) (40 * density), (int) (32 * density));
        plusLp.setMargins((int) (4 * density), 0, (int) (2 * density), 0);
        btnPlus.setLayoutParams(plusLp);
        btnPlus.setPadding(0, 0, 0, 0);
        btnPlus.setOnClickListener(v -> {
            float newVal = Math.min(max, getter.get() + step);
            setter.set(newVal);
            valueLabel.setText(String.format(Locale.US, format, newVal));
        });
        row.addView(btnPlus);

        return row;
    }

    private void refreshTuningLabels() {
        if (this.lblZoom != null) this.lblZoom.setText(String.format(Locale.US, "%.2f", this.bevView.getZoom()));
        if (this.lblCarW != null) this.lblCarW.setText(String.format(Locale.US, "%.2f", this.bevView.getCarWidth()));
        if (this.lblCarH != null) this.lblCarH.setText(String.format(Locale.US, "%.2f", this.bevView.getCarHeight()));
        if (this.lblCarOffY != null) this.lblCarOffY.setText(String.format(Locale.US, "%+.2f", this.bevView.getCarOffsetY()));
        if (this.lblDistFront != null) this.lblDistFront.setText(String.format(Locale.US, "%+.2f", this.bevView.getDistFront()));
        if (this.lblScaleFront != null) this.lblScaleFront.setText(String.format(Locale.US, "%.2f", this.bevView.getScaleFront()));
        if (this.lblPitchFront != null) this.lblPitchFront.setText(String.format(Locale.US, "%+.2f", this.bevView.getPitchFront()));
        if (this.lblDistRear != null) this.lblDistRear.setText(String.format(Locale.US, "%+.2f", this.bevView.getDistRear()));
        if (this.lblScaleRear != null) this.lblScaleRear.setText(String.format(Locale.US, "%.2f", this.bevView.getScaleRear()));
        if (this.lblPitchRear != null) this.lblPitchRear.setText(String.format(Locale.US, "%+.2f", this.bevView.getPitchRear()));

        if (this.lblRotSideL != null) this.lblRotSideL.setText(String.format(Locale.US, "%.0f°", this.bevView.getRotSideDegL()));
        if (this.lblRotSideR != null) this.lblRotSideR.setText(String.format(Locale.US, "%.0f°", this.bevView.getRotSideDegR()));
        if (this.lblSideOffsetY != null) this.lblSideOffsetY.setText(String.format(Locale.US, "%+.2f", this.bevView.getSideCamOffsetY()));
        if (this.lblDistSide != null) this.lblDistSide.setText(String.format(Locale.US, "%+.2f", this.bevView.getDistSide()));
        if (this.lblFovSide != null) this.lblFovSide.setText(String.format(Locale.US, "%.3f", this.bevView.getFovSide()));
        if (this.lblDepthSide != null) this.lblDepthSide.setText(String.format(Locale.US, "%.3f", this.bevView.getDepthSide()));
        if (this.lblScaleSide != null) this.lblScaleSide.setText(String.format(Locale.US, "%.2f", this.bevView.getScaleSide()));
        if (this.lblPitchSide != null) this.lblPitchSide.setText(String.format(Locale.US, "%+.2f", this.bevView.getPitchSide()));
        if (this.lblFishSide != null) this.lblFishSide.setText(String.format(Locale.US, "%+.2f", this.bevView.getFishSide()));
        if (this.lblSeamBlend != null) this.lblSeamBlend.setText(String.format(Locale.US, "%.2f", this.bevView.getSeamBlend()));
        if (this.lblSeamAngleFront != null) this.lblSeamAngleFront.setText(String.format(Locale.US, "%.2f", this.bevView.getSeamAngleFront()));

        if (this.lblFovFront != null) this.lblFovFront.setText(String.format(Locale.US, "%.3f", this.bevView.getFovFront()));
        if (this.lblDepthFront != null) this.lblDepthFront.setText(String.format(Locale.US, "%.3f", this.bevView.getDepthFront()));
        if (this.lblFovRear != null) this.lblFovRear.setText(String.format(Locale.US, "%.3f", this.bevView.getFovRear()));
        if (this.lblDepthRear != null) this.lblDepthRear.setText(String.format(Locale.US, "%.3f", this.bevView.getDepthRear()));

        if (this.btnGrid != null) {
            this.btnGrid.setText("📐 ตารางคาลิเบรต: " + (this.bevView.isShowGrid() ? "เปิด" : "ปิด"));
        }
        if (this.btnGridPanel != null) {
            this.btnGridPanel.setText("📐 เส้นตารางช่วยเล็งคาลิเบรต: " + (this.bevView.isShowGrid() ? "เปิด" : "ปิด"));
        }

        if (this.btnCycleRotL != null) {
            this.btnCycleRotL.setText("🔄 หมุนซ้าย: " + ((int) this.bevView.getRotSideDegL()) + "°");
        }
        if (this.btnCycleRotR != null) {
            this.btnCycleRotR.setText("🔄 หมุนขวา: " + ((int) this.bevView.getRotSideDegR()) + "°");
        }

        if (this.btnFlipSideVL != null) {
            this.btnFlipSideVL.setText("↕️ ตีลังกาซ้าย: " + (this.bevView.isFlipSideVL() ? "เปิด" : "ปิด"));
        }
        if (this.btnFlipSideVR != null) {
            this.btnFlipSideVR.setText("↕️ ตีลังกาขวา: " + (this.bevView.isFlipSideVR() ? "เปิด" : "ปิด"));
        }
        if (this.btnMirrorSideL != null) {
            this.btnMirrorSideL.setText("↔️ กลับซ้าย: " + (this.bevView.isMirrorSideL() ? "เปิด" : "ปิด"));
        }
        if (this.btnMirrorSideR != null) {
            this.btnMirrorSideR.setText("↔️ กลับขวา: " + (this.bevView.isMirrorSideR() ? "เปิด" : "ปิด"));
        }

        if (this.btnFlipFront != null) {
            this.btnFlipFront.setText("🔄 ตีลังกาหน้า: " + (this.bevView.isFlipFrontV() ? "เปิด" : "ปิด"));
        }
        if (this.btnMirrorFront != null) {
            this.btnMirrorFront.setText("↔️ กลับซ้ายขวาหน้า: " + (this.bevView.isMirrorFront() ? "เปิด" : "ปิด"));
        }
        if (this.btnFlipFrontPanel != null) {
            this.btnFlipFrontPanel.setText("↕️ ตีลังกาหน้า: " + (this.bevView.isFlipFrontV() ? "เปิด" : "ปิด"));
        }
        if (this.btnMirrorFrontPanel != null) {
            this.btnMirrorFrontPanel.setText("↔️ กลับซ้ายขวาหน้า: " + (this.bevView.isMirrorFront() ? "เปิด" : "ปิด"));
        }

        if (this.btnFlipRear != null) {
            this.btnFlipRear.setText("🔄 ตีลังกาหลัง: " + (this.bevView.isFlipRearV() ? "เปิด" : "ปิด"));
        }
        if (this.btnMirrorRear != null) {
            this.btnMirrorRear.setText("↔️ กลับซ้ายขวาหลัง: " + (this.bevView.isMirrorRear() ? "เปิด" : "ปิด"));
        }
        if (this.btnCarColor != null) {
            String colorName = (this.bevView.getCarModelColor() == 1) ? "ขาวมุก" : "บรอนซ์เงิน";
            this.btnCarColor.setText("🎨 โมเดลรถ: " + colorName);
        }
        updateStatusText();
    }

    private Button btn(String text, int bgColor, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11.5f);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundColor(bgColor);
        button.setAllCaps(false);
        button.setPadding(14, 6, 14, 6);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(4, 2, 4, 2);
        button.setLayoutParams(lp);
        button.setOnClickListener(listener);
        return button;
    }

    private void setMode(int mode) {
        this.displayMode = mode;
        if (mode == 0) {
            // BEV 360 Portrait View
            this.bevContainer.setVisibility(View.VISIBLE);
            this.tuningPanel.setVisibility(View.VISIBLE);
            if (this.btnToggleTuning != null) {
                this.btnToggleTuning.setText("⚙️ แผงปรับละเอียด: เปิด");
            }
            this.texture.setVisibility(View.INVISIBLE);
        } else {
            // TextureView Raw or Cropped
            this.bevContainer.setVisibility(View.GONE);
            this.tuningPanel.setVisibility(View.GONE);
            this.texture.setVisibility(View.VISIBLE);
            applyTextureCrop();
        }
        updateStatusText();
        if (this.cam == null) {
            startCamera();
        }
    }

    private void applyTextureCrop() {
        int width = this.texture.getWidth();
        int height = this.texture.getHeight();
        if (width == 0 || height == 0) return;

        Matrix matrix = new Matrix();
        if (this.displayMode == 1) {
            // 4-Split Raw Full: identity
            this.texture.setTransform(matrix);
        } else if (this.displayMode >= 2 && this.displayMode <= 5) {
            // Single panel: scale by 4x horizontally and translate
            int panelIdx = this.displayMode - 2; // 0=Rear, 1=Left, 2=Right, 3=Front
            matrix.postScale(4.0f, 1.0f, 0.0f, 0.0f);
            matrix.postTranslate((-panelIdx) * width, 0.0f);
            this.texture.setTransform(matrix);
        }
    }

    private void startCamera() {
        closeCam();
        new Thread(this::openAndStream, "camera-stream").start();
    }

    private void openAndStream() {
        try {
            JNIBMMCamera jNIBMMCamera = new JNIBMMCamera(this.camId);
            this.cam = jNIBMMCamera;

            if (!jNIBMMCamera.nativeOpen(this.camId)) {
                log("❌ nativeOpen(" + this.camId + ") = false (ไม่สามารถเปิดอุปกรณ์กล้องได้)");
                return;
            }

            int pw = jNIBMMCamera.nativeGetPreviewWidth();
            int ph = jNIBMMCamera.nativeGetPreviewHeight();
            if (pw <= 0 || ph <= 0) {
                pw = 1920;
                ph = 1080;
            }
            jNIBMMCamera.nativeSetPreviewSize(pw, ph);
            jNIBMMCamera.nativeSetCameraFPS(25);

            // 1. Attach TextureView Surface
            if (this.textureSurface != null) {
                jNIBMMCamera.nativeAddPreviewSurface(this.textureSurface, 0);
            }

            // 2. Attach BEV GL Surface
            if (this.bevSurface != null) {
                jNIBMMCamera.nativeAddPreviewSurface(this.bevSurface, 0);
            }

            if (!jNIBMMCamera.nativeStartPreview()) {
                log("❌ nativeStartPreview() = false");
                return;
            }

            log("✓ สตรีมมิ่งกล้อง cam 1 ทำงานสมบูรณ์ (1920x1080 @ 25fps)");
        } catch (Throwable th) {
            log("❌ กล้อง Exception: " + th.getMessage());
            Log.e(TAG, "openAndStream failed", th);
        }
    }

    private void closeCam() {
        JNIBMMCamera c = this.cam;
        this.cam = null;
        if (c != null) {
            try {
                c.nativeStopPreview();
            } catch (Throwable ignored) {
            }
            try {
                c.nativeClose();
            } catch (Throwable ignored) {
            }
        }
        log("Camera feed stopped.");
    }

    private void updateStatusText() {
        String modeName = "";
        switch (this.displayMode) {
            case 0: modeName = "🚗 Bird's-Eye View (BEV 360 กรอบแดงแนวตั้ง)"; break;
            case 1: modeName = "📺 4-ช่องเต็ม Raw (1:หลัง, 2:ซ้าย, 3:ขวา, 4:หน้า)"; break;
            case 2: modeName = "1: กล้องหลัง (Rear, Panel 1)"; break;
            case 3: modeName = "2: กล้องซ้าย (Left, Panel 2)"; break;
            case 4: modeName = "3: กล้องขวา (Right, Panel 3)"; break;
            case 5: modeName = "4: กล้องหน้า (Front, Panel 4)"; break;
        }

        String bevInfo = "";
        if (this.displayMode == 0 && this.bevView != null) {
            String colorName = (this.bevView.getCarModelColor() == 1) ? "ขาวมุก" : "บรอนซ์เงิน";
            bevInfo = String.format(Locale.US,
                    " | Zoom=%.2f | FovF=%.3f FovR=%.3f FovS=%.3f | RotL=%.0f° RotR=%.0f° (MirrorY=%+.2f) | Car=%.2fx%.2f (offY=%+.2f) | สีรถ=%s | Grid=%s",
                    this.bevView.getZoom(),
                    this.bevView.getFovFront(), this.bevView.getFovRear(), this.bevView.getFovSide(),
                    this.bevView.getRotSideDegL(), this.bevView.getRotSideDegR(), this.bevView.getSideCamOffsetY(),
                    this.bevView.getCarWidth(), this.bevView.getCarHeight(), this.bevView.getCarOffsetY(),
                    colorName,
                    this.bevView.isShowGrid() ? "ON" : "OFF");
        }

        this.status.setText(BUILD_TAG + "\n" +
                "โหมดปัจจุบัน: " + modeName + bevInfo + "\n" +
                "📌 ลำดับกล้อง: [ช่อง 1: หลัง] [ช่อง 2: ซ้าย] [ช่อง 3: ขวา] [ช่อง 4: หน้า]\n\n" +
                this.diagText);
    }

    private void log(String msg) {
        runOnUiThread(() -> {
            this.status.setText(BUILD_TAG + "\n" + msg + "\n\n" + this.diagText);
        });
    }

    private String collectDiagnostics() {
        StringBuilder sb = new StringBuilder();
        String bydCarType = getSystemProp("ro.byd.cartype", "N/A");
        String model = getSystemProp("ro.product.model", Build.MODEL);
        sb.append("📋 Vehicle Model: ").append(model).append(" | BYD CarType: ").append(bydCarType).append("\n");

        int carBodyType = -1;
        try {
            carBodyType = Settings.System.getInt(getContentResolver(), "car_body_type", -1);
        } catch (Throwable ignored) {
        }
        if (carBodyType == -1) {
            try {
                Class<?> userTable = Class.forName("android.provider.CarSettings$UserTableData");
                Method getSystemInt = userTable.getMethod("getSystemInt", android.content.ContentResolver.class, String.class, int.class);
                carBodyType = (int) getSystemInt.invoke(null, getContentResolver(), "car_body_type", -1);
            } catch (Throwable ignored) {
            }
        }
        sb.append("🚗 CarBodyType ID: ").append(carBodyType).append("\n");

        File panoSo = new File("/system/app/AutoVideo/lib/arm64/libVehiclePano.so");
        File bstSo = new File("/system/app/AutoVideo/lib/arm64/libbst_surroundview.so");
        sb.append("📦 AutoVideo Files: ")
          .append("VehiclePano: ").append(panoSo.exists() ? "✓ พบ" : "✗ ไม่พบ")
          .append(" | BST: ").append(bstSo.exists() ? "✓ พบ" : "✗ ไม่พบ");
        return sb.toString();
    }

    private String getSystemProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        st.setDefaultBufferSize(1920, 1080);
        this.textureSurface = new Surface(st);
        startCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        applyTextureCrop();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        closeCam();
        this.textureSurface = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }

    @Override
    protected void onDestroy() {
        closeCam();
        super.onDestroy();
    }
}
