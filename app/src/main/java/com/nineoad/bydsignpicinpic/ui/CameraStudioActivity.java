package com.nineoad.bydsignpicinpic.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Surface;
import com.nineoad.bydsignpicinpic.R;
import com.nineoad.bydsignpicinpic.config.OemRecommendedPreset;
import com.nineoad.bydsignpicinpic.overlay.FisheyeGLView;
import com.nineoad.bydsignpicinpic.overlay.OverlayService;
import com.nineoad.bydsignpicinpic.overlay.Perspective3DParams;
import com.nineoad.bydsignpicinpic.signal.BevAutomationManager;
import java.util.Locale;

/**
 * Dedicated Fullscreen Touch Calibration Studio.
 * Split View:
 * - Left: Interactive Touch Preview with 1-finger pan, 2-finger zoom, and 2-finger rotate gestures.
 * - Right: Complete settings and parameters for the active mode.
 */
public class CameraStudioActivity extends AppCompatActivity implements TouchCalibrationPreviewView.OnGestureListener {

    public static final String EXTRA_MODE = "EXTRA_MODE"; // 1 = Turn Signal, 2 = Rearview, 3 = 4-Wheel Blindspot

    private int activeMode = 1;
    private SharedPreferences prefs;

    // Left View & Live Camera Feed
    private FrameLayout studioPreviewContainer;
    private FrameLayout studioAspectBox;
    private FisheyeGLView studioGlView;
    private TouchCalibrationPreviewView previewView;

    // Top Toolbar Views
    private Button btnStudioBack;
    private TextView tvStudioTitle;
    private Button btnStudioToggleGrid;
    private Button btnStudioRecommended;
    private Button btnStudioReset;
    private Button btnStudioSave;

    // Right Dock - Dynamic Selector Views
    private LinearLayout dockTurnTabs;
    private Button btnStudioSideLeft;
    private Button btnStudioSideRight;
    private boolean isTurnRight = false;

    // 3D Perspective Controls (Mode 1: Turn Signal)
    private View layoutStudio3DControls;
    private View layoutStudio2DControls;
    private Spinner spinnerStudioModelPreset;
    private Switch swStudio3DMode;

    private SeekBar sbStudioPoleTilt, sbStudioCarBodyTilt, sbStudioPitchKeystone, sbStudioHorizonStraighten;
    private SeekBar sbStudioRollDeg, sbStudioCylindrical, sbStudioTrueLensK1, sbStudioZoom3D;
    private SeekBar sbStudioGlobalScale, sbStudioPanInOut, sbStudioPanY, sbStudioPanX;
    private SeekBar sbStudioSensorWidth, sbStudioSensorHeight;

    private TextView tvStudioPoleTilt, tvStudioCarBodyTilt, tvStudioPitchKeystone, tvStudioHorizonStraighten;
    private TextView tvStudioRollDeg, tvStudioCylindrical, tvStudioTrueLensK1, tvStudioZoom3D;
    private TextView tvStudioGlobalScale, tvStudioPanInOut, tvStudioPanY, tvStudioPanX;
    private TextView tvStudioSensorWidth, tvStudioSensorHeight;

    private Button btnStudioPoleTiltMinus, btnStudioPoleTiltPlus;
    private Button btnStudioCarBodyTiltMinus, btnStudioCarBodyTiltPlus;
    private Button btnStudioPitchKeystoneMinus, btnStudioPitchKeystonePlus;
    private Button btnStudioHorizonStraightenMinus, btnStudioHorizonStraightenPlus;
    private Button btnStudioRollDegMinus, btnStudioRollDegPlus;
    private Button btnStudioCylindricalMinus, btnStudioCylindricalPlus;
    private Button btnStudioTrueLensK1Minus, btnStudioTrueLensK1Plus;
    private Button btnStudioZoom3DMinus, btnStudioZoom3DPlus;
    private Button btnStudioGlobalScaleMinus, btnStudioGlobalScalePlus;
    private Button btnStudioPanInOutMinus, btnStudioPanInOutPlus;
    private Button btnStudioPanYMinus, btnStudioPanYPlus;
    private Button btnStudioPanXMinus, btnStudioPanXPlus;
    private Button btnStudioSensorWidthMinus, btnStudioSensorWidthPlus;
    private Button btnStudioSensorHeightMinus, btnStudioSensorHeightPlus;
    private Button btnStudioImportJson;
    private Button btnStudioExportJson;

    private LinearLayout dockWheelGrid;
    private Button btnStudioWheelFL, btnStudioWheelFR;
    private int selectedWheelIdx = 0; // 0=FL, 1=FR
    private boolean isQuadOverviewMode = false;

    // Rotation & Mirror Controls
    private Button btnStudioRotate90;
    private TextView tvStudioRotateVal;
    private Button btnStudioFineMinus, btnStudioFinePlus;
    private TextView tvStudioFineVal;
    private Switch swStudioMirror, swStudioFlipV;

    // Crop SeekBars & Steppers
    private SeekBar sbStudioCropT, sbStudioCropB, sbStudioCropL, sbStudioCropR;
    private TextView tvStudioCropT, tvStudioCropB, tvStudioCropL, tvStudioCropR;
    private Button btnStudioCropTMinus, btnStudioCropTPlus;
    private Button btnStudioCropBMinus, btnStudioCropBPlus;
    private Button btnStudioCropLMinus, btnStudioCropLPlus;
    private Button btnStudioCropRMinus, btnStudioCropRPlus;

    // Fisheye SeekBars & Steppers
    private SeekBar sbStudioFeStr, sbStudioFeZoom, sbStudioFeCx, sbStudioFeCy;
    private TextView tvStudioFeStr, tvStudioFeZoom, tvStudioFeCx, tvStudioFeCy;
    private Button btnStudioFeStrMinus, btnStudioFeStrPlus;
    private Button btnStudioFeZoomMinus, btnStudioFeZoomPlus;
    private Button btnStudioFeCxMinus, btnStudioFeCxPlus;
    private Button btnStudioFeCyMinus, btnStudioFeCyPlus;

    private Button btnStudioTestPip;
    private Spinner spinnerStudioCamSlot;
    private TextView tvStudioCamSlotLabel;
    private View layoutStudioCamSlot;

    private Handler studioStatusHandler;
    private Runnable studioStatusRunnable;

    private boolean suppressSync = false;

    private boolean isEn() {
        return !MainActivity.isThaiLocale(this);
    }

    private String tr(String th, String en) {
        return isEn() ? en : th;
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        boolean isThai = MainActivity.isThaiLocale(newBase);
        java.util.Locale locale = new java.util.Locale(isThai ? "th" : "en");
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        android.content.Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_studio);

        OverlayService.isStudioActive = true;
        prefs = getSharedPreferences("pnp", 0);
        migrateLegacyPrefsIfNeeded();
        activeMode = getIntent().getIntExtra(EXTRA_MODE, 1);

        initViews();
        setupModeEnvironment();
        loadCurrentModeIntoControls();

        if (OverlayService.getInstance() == null) {
            Intent svc = new Intent(this, OverlayService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        activeMode = intent.getIntExtra(EXTRA_MODE, 1);
        if (previewView != null) {
            previewView.setPipMode(activeMode);
        }
        setupModeEnvironment();
        loadCurrentModeIntoControls();
        updateStudioGlParams();
    }

    @Override
    protected void onResume() {
        super.onResume();
        OverlayService.isStudioActive = true;
        if (studioGlView != null) {
            studioGlView.setActiveOnScreen(true);
            studioGlView.onResume();
        }
        attachStudioFeed();
        if (activeMode == 3 && studioStatusHandler != null && studioStatusRunnable != null) {
            studioStatusHandler.post(studioStatusRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (studioStatusHandler != null && studioStatusRunnable != null) {
            studioStatusHandler.removeCallbacks(studioStatusRunnable);
        }
        if (studioGlView != null) {
            studioGlView.setActiveOnScreen(false);
            studioGlView.onPause();
        }
        detachStudioFeed();
    }

    @Override
    protected void onDestroy() {
        if (studioStatusHandler != null && studioStatusRunnable != null) {
            studioStatusHandler.removeCallbacks(studioStatusRunnable);
            studioStatusHandler = null;
            studioStatusRunnable = null;
        }
        OverlayService.isStudioActive = false;
        OverlayService.allowStudioPipTest = false;
        detachStudioFeed();
        super.onDestroy();
    }

    private void migrateLegacyPrefsIfNeeded() {
        try {
            SharedPreferences legacy = getSharedPreferences("byd_signal_pnp_prefs", MODE_PRIVATE);
            if (legacy == null || legacy.getAll().isEmpty()) return;

            SharedPreferences.Editor ed = prefs.edit();
            if (legacy.contains("custom_fine_l")) ed.putFloat("dr_fn_l", legacy.getFloat("custom_fine_l", 0f));
            if (legacy.contains("custom_zoom_l")) ed.putFloat("dr_zoom_l", legacy.getFloat("custom_zoom_l", 1f));
            if (legacy.contains("custom_str_l")) ed.putFloat("dr_str_l", legacy.getFloat("custom_str_l", 0f));
            if (legacy.contains("custom_cx_l")) ed.putFloat("dr_cx_l", legacy.getFloat("custom_cx_l", 0.5f));
            if (legacy.contains("custom_cy_l")) ed.putFloat("dr_cy_l", legacy.getFloat("custom_cy_l", 0.5f));
            if (legacy.contains("custom_l_0")) ed.putFloat("dr_l0", legacy.getFloat("custom_l_0", 0.05f));
            if (legacy.contains("custom_l_1")) ed.putFloat("dr_l1", legacy.getFloat("custom_l_1", 0.24f));
            if (legacy.contains("custom_l_2")) ed.putFloat("dr_l2", legacy.getFloat("custom_l_2", 0.92f));
            if (legacy.contains("custom_l_3")) ed.putFloat("dr_l3", legacy.getFloat("custom_l_3", 1.0f));

            if (legacy.contains("custom_fine_rear")) ed.putFloat("rear_fn_l", legacy.getFloat("custom_fine_rear", 0f));
            if (legacy.contains("custom_zoom_rear")) ed.putFloat("rear_zoom_l", legacy.getFloat("custom_zoom_rear", 1f));
            if (legacy.contains("custom_str_rear")) ed.putFloat("rear_str_l", legacy.getFloat("custom_str_rear", 0f));
            if (legacy.contains("custom_cx_rear")) ed.putFloat("rear_cx_l", legacy.getFloat("custom_cx_rear", 0.5f));
            if (legacy.contains("custom_cy_rear")) ed.putFloat("rear_cy_l", legacy.getFloat("custom_cy_rear", 0.5f));

            ed.putInt("mode", 3);
            ed.apply();
            legacy.edit().clear().apply();
        } catch (Exception ignored) {}
    }

    private void attachStudioFeed() {
        if (studioGlView == null) return;
        studioGlView.setActiveOnScreen(true);
        Surface sL = studioGlView.getCameraSurfaceLeft();
        Surface sR = studioGlView.getCameraSurfaceRight();
        Surface sRear = studioGlView.getCameraSurfaceRear();

        OverlayService overlay = OverlayService.getInstance();
        if (overlay != null) {
            overlay.attachStudioSurfaces(sL, sR, sRear);
            if (previewView != null) {
                previewView.setCameraConnected(true);
            }
        } else {
            if (previewView != null) {
                previewView.setCameraConnected(false);
                previewView.postDelayed(this::attachStudioFeed, 200);
            }
        }
        updateStudioGlParams();
    }

    private void detachStudioFeed() {
        OverlayService overlay = OverlayService.getInstance();
        if (overlay != null) {
            overlay.detachStudioSurfaces();
        }
        if (previewView != null) {
            previewView.setCameraConnected(false);
        }
    }

    private void initViews() {
        studioPreviewContainer = findViewById(R.id.studioPreviewContainer);
        studioAspectBox = findViewById(R.id.studioAspectBox);
        if (studioPreviewContainer != null) {
            studioPreviewContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldL, oldT, oldR, oldB) -> {
                if ((right - left) != (oldR - oldL) || (bottom - top) != (oldB - oldT)) {
                    updatePreviewAspectRatio(getTargetPipAspect());
                }
            });
        }

        studioGlView = findViewById(R.id.studioGlView);
        if (studioGlView != null) {
            studioGlView.setActiveOnScreen(true);
            studioGlView.setSurfaceReadyListener(new FisheyeGLView.SurfaceReadyListener() {
                @Override
                public void onReady(Surface surface) {
                    attachStudioFeed();
                }
            });
        }

        previewView = findViewById(R.id.studioPreviewView);
        previewView.setOnGestureListener(this);

        btnStudioBack = findViewById(R.id.btnStudioBack);
        btnStudioBack.setOnClickListener(v -> finish());

        tvStudioTitle = findViewById(R.id.tvStudioTitle);
        btnStudioToggleGrid = findViewById(R.id.btnStudioToggleGrid);
        btnStudioToggleGrid.setOnClickListener(v -> previewView.toggleGrid());

        btnStudioRecommended = findViewById(R.id.btnStudioRecommended);
        btnStudioRecommended.setOnClickListener(v -> applyRecommendedPreset());

        btnStudioImportJson = findViewById(R.id.btnStudioImportJson);
        if (btnStudioImportJson != null) {
            btnStudioImportJson.setOnClickListener(v -> {
                boolean ok = Perspective3DParams.importFromLabJsonFile(prefs);
                if (ok) {
                    Toast.makeText(this, R.string.studio_toast_import_success, Toast.LENGTH_SHORT).show();
                    loadCurrentModeIntoControls();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                } else {
                    Toast.makeText(this, R.string.studio_toast_import_failed, Toast.LENGTH_LONG).show();
                }
            });
        }

        btnStudioReset = findViewById(R.id.btnStudioReset);
        btnStudioReset.setOnClickListener(v -> resetCurrentToDefaults());

        btnStudioSave = findViewById(R.id.btnStudioSave);
        btnStudioSave.setOnClickListener(v -> {
            prefs.edit().putInt("mode", activeMode).apply();
            updateStudioGlParams();
            OverlayService.reapplyLive();
            Toast.makeText(this, tr("บันทึกค่ามุมกล้องและนำไปใช้กับหน้าต่าง PIP เรียบร้อย",
                "Camera calibration saved and applied to PIP window"), Toast.LENGTH_SHORT).show();
            finish();
        });

        dockTurnTabs = findViewById(R.id.dockTurnTabs);
        btnStudioSideLeft = findViewById(R.id.btnStudioSideLeft);
        btnStudioSideRight = findViewById(R.id.btnStudioSideRight);
        btnStudioSideLeft.setOnClickListener(v -> selectTurnSide(false));
        btnStudioSideRight.setOnClickListener(v -> selectTurnSide(true));

        dockWheelGrid = findViewById(R.id.dockWheelGrid);
        btnStudioWheelFL = findViewById(R.id.btnStudioWheelFL);
        btnStudioWheelFR = findViewById(R.id.btnStudioWheelFR);
        if (btnStudioWheelFL != null) btnStudioWheelFL.setOnClickListener(v -> selectWheel(0));
        if (btnStudioWheelFR != null) btnStudioWheelFR.setOnClickListener(v -> selectWheel(1));

        btnStudioRotate90 = findViewById(R.id.btnStudioRotate90);
        tvStudioRotateVal = findViewById(R.id.tvStudioRotateVal);
        btnStudioFineMinus = findViewById(R.id.btnStudioFineMinus);
        btnStudioFinePlus = findViewById(R.id.btnStudioFinePlus);
        tvStudioFineVal = findViewById(R.id.tvStudioFineVal);
        swStudioMirror = findViewById(R.id.swStudioMirror);
        swStudioFlipV = findViewById(R.id.swStudioFlipV);

        btnStudioRotate90.setOnClickListener(v -> cycleRotate90());
        btnStudioFineMinus.setOnClickListener(v -> nudgeFine(-1f));
        btnStudioFineMinus.setOnLongClickListener(v -> { nudgeFine(-5f); return true; });
        btnStudioFinePlus.setOnClickListener(v -> nudgeFine(1f));
        btnStudioFinePlus.setOnLongClickListener(v -> { nudgeFine(5f); return true; });
        swStudioMirror.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!suppressSync) {
                saveMirror(isChecked);
                updatePreviewHud();
                OverlayService.reapplyLive();
            }
        });
        swStudioFlipV.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!suppressSync) {
                saveFlipV(isChecked);
                updatePreviewHud();
                OverlayService.reapplyLive();
            }
        });

        // Crop Views
        sbStudioCropT = findViewById(R.id.sbStudioCropT);
        sbStudioCropB = findViewById(R.id.sbStudioCropB);
        sbStudioCropL = findViewById(R.id.sbStudioCropL);
        sbStudioCropR = findViewById(R.id.sbStudioCropR);
        tvStudioCropT = findViewById(R.id.tvStudioCropT);
        tvStudioCropB = findViewById(R.id.tvStudioCropB);
        tvStudioCropL = findViewById(R.id.tvStudioCropL);
        tvStudioCropR = findViewById(R.id.tvStudioCropR);
        btnStudioCropTMinus = findViewById(R.id.btnStudioCropTMinus);
        btnStudioCropTPlus = findViewById(R.id.btnStudioCropTPlus);
        btnStudioCropBMinus = findViewById(R.id.btnStudioCropBMinus);
        btnStudioCropBPlus = findViewById(R.id.btnStudioCropBPlus);
        btnStudioCropLMinus = findViewById(R.id.btnStudioCropLMinus);
        btnStudioCropLPlus = findViewById(R.id.btnStudioCropLPlus);
        btnStudioCropRMinus = findViewById(R.id.btnStudioCropRMinus);
        btnStudioCropRPlus = findViewById(R.id.btnStudioCropRPlus);

        setupCropListeners();

        // Fisheye Views
        sbStudioFeStr = findViewById(R.id.sbStudioFeStr);
        sbStudioFeZoom = findViewById(R.id.sbStudioFeZoom);
        sbStudioFeCx = findViewById(R.id.sbStudioFeCx);
        sbStudioFeCy = findViewById(R.id.sbStudioFeCy);
        tvStudioFeStr = findViewById(R.id.tvStudioFeStr);
        tvStudioFeZoom = findViewById(R.id.tvStudioFeZoom);
        tvStudioFeCx = findViewById(R.id.tvStudioFeCx);
        tvStudioFeCy = findViewById(R.id.tvStudioFeCy);
        btnStudioFeStrMinus = findViewById(R.id.btnStudioFeStrMinus);
        btnStudioFeStrPlus = findViewById(R.id.btnStudioFeStrPlus);
        btnStudioFeZoomMinus = findViewById(R.id.btnStudioFeZoomMinus);
        btnStudioFeZoomPlus = findViewById(R.id.btnStudioFeZoomPlus);
        btnStudioFeCxMinus = findViewById(R.id.btnStudioFeCxMinus);
        btnStudioFeCxPlus = findViewById(R.id.btnStudioFeCxPlus);
        btnStudioFeCyMinus = findViewById(R.id.btnStudioFeCyMinus);
        btnStudioFeCyPlus = findViewById(R.id.btnStudioFeCyPlus);

        setupFisheyeListeners();

        btnStudioTestPip = findViewById(R.id.btnStudioTestPip);
        btnStudioTestPip.setOnClickListener(v -> triggerLivePipTest());

        layoutStudioCamSlot = findViewById(R.id.layoutStudioCamSlot);
        tvStudioCamSlotLabel = findViewById(R.id.tvStudioCamSlotLabel);
        spinnerStudioCamSlot = findViewById(R.id.spinnerStudioCamSlot);
        setupCamSlotSelector();

        setupStudioExtraFeatures();
        setup3DListeners();
    }

    private void setupCamSlotSelector() {
        if (spinnerStudioCamSlot == null) return;
        final String[] slotOptions = new String[]{
            "Index 0", "Index 1", "Index 2", "Index 3", "Index 4", "Index 5"
        };
        spinnerStudioCamSlot.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, slotOptions));
        spinnerStudioCamSlot.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSync) return;
                int targetType = (activeMode == 2) ? 4 : (isTurnRight ? 2 : 1);
                String prefKey = (activeMode == 2) ? "rear_cam_index" : (isTurnRight ? "right_cam_index" : "left_cam_index");
                int defVal = (activeMode == 2) ? 1 : (isTurnRight ? 3 : 2);
                int oldVal = prefs.getInt(prefKey, defVal);
                if (oldVal != position) {
                    prefs.edit().putInt(prefKey, position).apply();
                    if (targetType == 1) OverlayService.leftIndex = position;
                    else if (targetType == 2) OverlayService.rightIndex = position;
                    else if (targetType == 4) OverlayService.rearIndex = position;

                    OverlayService overlay = OverlayService.getInstance();
                    if (overlay != null) {
                        overlay.updateStudioSlot(targetType, oldVal, position);
                    }
                    updateStudioGlParams();
                }
            }
        });
    }

    private void setup3DListeners() {
        layoutStudio3DControls = findViewById(R.id.layoutStudio3DControls);
        layoutStudio2DControls = findViewById(R.id.layoutStudio2DControls);
        spinnerStudioModelPreset = findViewById(R.id.spinnerStudioModelPreset);
        swStudio3DMode = findViewById(R.id.swStudio3DMode);

        if (spinnerStudioModelPreset != null) {
            final String[] modelOptions = new String[]{
                getString(R.string.studio_model_atto3),
                getString(R.string.studio_model_dolphin),
                getString(R.string.studio_model_seal)
            };
            spinnerStudioModelPreset.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modelOptions));
            spinnerStudioModelPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(AdapterView<?> parent) {}
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (suppressSync) return;
                    if (position == 0) { // ATTO 3
                        OemRecommendedPreset.applyAtto3PerspectivePreset(prefs);
                        Toast.makeText(CameraStudioActivity.this, R.string.studio_toast_atto3_loaded, Toast.LENGTH_SHORT).show();
                        loadCurrentModeIntoControls();
                        updateStudioGlParams();
                        OverlayService.reapplyLive();
                    } else {
                        Toast.makeText(CameraStudioActivity.this, tr("พรีเซ็ตรุ่นนี้กำลังอยู่ในการทดสอบ (Coming Soon)", "Preset for this model is coming soon"), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (swStudio3DMode != null) {
            swStudio3DMode.setOnCheckedChangeListener((btn, isChecked) -> {
                if (!suppressSync) {
                    prefs.edit().putBoolean("perspective_3d_enabled", isChecked).apply();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            });
        }

        // 1. Pole Plumb
        sbStudioPoleTilt = findViewById(R.id.sbStudioPoleTilt);
        tvStudioPoleTilt = findViewById(R.id.tvStudioPoleTilt);
        btnStudioPoleTiltMinus = findViewById(R.id.btnStudioPoleTiltMinus);
        btnStudioPoleTiltPlus = findViewById(R.id.btnStudioPoleTiltPlus);
        if (sbStudioPoleTilt != null) {
            sbStudioPoleTilt.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) - 1.00f;
                        save3DParam("poleTilt", v);
                        if (tvStudioPoleTilt != null) tvStudioPoleTilt.setText(String.format(Locale.US, "%+.2f", v));
                    }
                }
            });
        }
        if (btnStudioPoleTiltMinus != null) btnStudioPoleTiltMinus.setOnClickListener(v -> nudge3DParam("poleTilt", -0.02f, -1.00f, 3.50f));
        if (btnStudioPoleTiltPlus != null) btnStudioPoleTiltPlus.setOnClickListener(v -> nudge3DParam("poleTilt", 0.02f, -1.00f, 3.50f));

        // 2. Car Body Plumb
        sbStudioCarBodyTilt = findViewById(R.id.sbStudioCarBodyTilt);
        tvStudioCarBodyTilt = findViewById(R.id.tvStudioCarBodyTilt);
        btnStudioCarBodyTiltMinus = findViewById(R.id.btnStudioCarBodyTiltMinus);
        btnStudioCarBodyTiltPlus = findViewById(R.id.btnStudioCarBodyTiltPlus);
        if (sbStudioCarBodyTilt != null) {
            sbStudioCarBodyTilt.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) - 1.00f;
                        save3DParam("carBodyTilt", v);
                        if (tvStudioCarBodyTilt != null) tvStudioCarBodyTilt.setText(String.format(Locale.US, "%+.2f", v));
                    }
                }
            });
        }
        if (btnStudioCarBodyTiltMinus != null) btnStudioCarBodyTiltMinus.setOnClickListener(v -> nudge3DParam("carBodyTilt", -0.02f, -1.00f, 2.50f));
        if (btnStudioCarBodyTiltPlus != null) btnStudioCarBodyTiltPlus.setOnClickListener(v -> nudge3DParam("carBodyTilt", 0.02f, -1.00f, 2.50f));

        // 3. Pitch Keystone
        sbStudioPitchKeystone = findViewById(R.id.sbStudioPitchKeystone);
        tvStudioPitchKeystone = findViewById(R.id.tvStudioPitchKeystone);
        btnStudioPitchKeystoneMinus = findViewById(R.id.btnStudioPitchKeystoneMinus);
        btnStudioPitchKeystonePlus = findViewById(R.id.btnStudioPitchKeystonePlus);
        if (sbStudioPitchKeystone != null) {
            sbStudioPitchKeystone.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) - 0.50f;
                        save3DParam("pitchKeystone", v);
                        if (tvStudioPitchKeystone != null) tvStudioPitchKeystone.setText(String.format(Locale.US, "%+.2f", v));
                    }
                }
            });
        }
        if (btnStudioPitchKeystoneMinus != null) btnStudioPitchKeystoneMinus.setOnClickListener(v -> nudge3DParam("pitchKeystone", -0.01f, -0.50f, 0.80f));
        if (btnStudioPitchKeystonePlus != null) btnStudioPitchKeystonePlus.setOnClickListener(v -> nudge3DParam("pitchKeystone", 0.01f, -0.50f, 0.80f));

        // 4. Horizon Straighten
        sbStudioHorizonStraighten = findViewById(R.id.sbStudioHorizonStraighten);
        tvStudioHorizonStraighten = findViewById(R.id.tvStudioHorizonStraighten);
        btnStudioHorizonStraightenMinus = findViewById(R.id.btnStudioHorizonStraightenMinus);
        btnStudioHorizonStraightenPlus = findViewById(R.id.btnStudioHorizonStraightenPlus);
        if (sbStudioHorizonStraighten != null) {
            sbStudioHorizonStraighten.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) - 1.00f;
                        save3DParam("horizonStraighten", v);
                        if (tvStudioHorizonStraighten != null) tvStudioHorizonStraighten.setText(String.format(Locale.US, "%+.2f", v));
                    }
                }
            });
        }
        if (btnStudioHorizonStraightenMinus != null) btnStudioHorizonStraightenMinus.setOnClickListener(v -> nudge3DParam("horizonStraighten", -0.01f, -1.00f, 0.50f));
        if (btnStudioHorizonStraightenPlus != null) btnStudioHorizonStraightenPlus.setOnClickListener(v -> nudge3DParam("horizonStraighten", 0.01f, -1.00f, 0.50f));

        // 5. Roll Deg
        sbStudioRollDeg = findViewById(R.id.sbStudioRollDeg);
        tvStudioRollDeg = findViewById(R.id.tvStudioRollDeg);
        btnStudioRollDegMinus = findViewById(R.id.btnStudioRollDegMinus);
        btnStudioRollDegPlus = findViewById(R.id.btnStudioRollDegPlus);
        if (sbStudioRollDeg != null) {
            sbStudioRollDeg.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 10.0f) - 30.0f;
                        save3DParam("rollDeg", v);
                        if (tvStudioRollDeg != null) tvStudioRollDeg.setText(String.format(Locale.US, "%+.1f°", v));
                    }
                }
            });
        }
        if (btnStudioRollDegMinus != null) {
            btnStudioRollDegMinus.setOnClickListener(v -> nudge3DParam("rollDeg", -0.1f, -30.0f, 30.0f));
            btnStudioRollDegMinus.setOnLongClickListener(v -> { nudge3DParam("rollDeg", -1.0f, -30.0f, 30.0f); return true; });
        }
        if (btnStudioRollDegPlus != null) {
            btnStudioRollDegPlus.setOnClickListener(v -> nudge3DParam("rollDeg", 0.1f, -30.0f, 30.0f));
            btnStudioRollDegPlus.setOnLongClickListener(v -> { nudge3DParam("rollDeg", 1.0f, -30.0f, 30.0f); return true; });
        }

        // 6. Cylindrical Panini
        sbStudioCylindrical = findViewById(R.id.sbStudioCylindrical);
        tvStudioCylindrical = findViewById(R.id.tvStudioCylindrical);
        btnStudioCylindricalMinus = findViewById(R.id.btnStudioCylindricalMinus);
        btnStudioCylindricalPlus = findViewById(R.id.btnStudioCylindricalPlus);
        if (sbStudioCylindrical != null) {
            sbStudioCylindrical.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) - 0.50f;
                        save3DParam("cylindrical", v);
                        if (tvStudioCylindrical != null) tvStudioCylindrical.setText(String.format(Locale.US, "%.2f", v));
                    }
                }
            });
        }
        if (btnStudioCylindricalMinus != null) btnStudioCylindricalMinus.setOnClickListener(v -> nudge3DParam("cylindrical", -0.02f, -0.50f, 1.00f));
        if (btnStudioCylindricalPlus != null) btnStudioCylindricalPlus.setOnClickListener(v -> nudge3DParam("cylindrical", 0.02f, -0.50f, 1.00f));

        // 7. True Lens Fisheye K1
        sbStudioTrueLensK1 = findViewById(R.id.sbStudioTrueLensK1);
        tvStudioTrueLensK1 = findViewById(R.id.tvStudioTrueLensK1);
        btnStudioTrueLensK1Minus = findViewById(R.id.btnStudioTrueLensK1Minus);
        btnStudioTrueLensK1Plus = findViewById(R.id.btnStudioTrueLensK1Plus);
        if (sbStudioTrueLensK1 != null) {
            sbStudioTrueLensK1.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) - 0.80f;
                        save3DParam("trueLensK1", v);
                        if (tvStudioTrueLensK1 != null) tvStudioTrueLensK1.setText(String.format(Locale.US, "%.2f", v));
                    }
                }
            });
        }
        if (btnStudioTrueLensK1Minus != null) btnStudioTrueLensK1Minus.setOnClickListener(v -> nudge3DParam("trueLensK1", -0.01f, -0.80f, 0.30f));
        if (btnStudioTrueLensK1Plus != null) btnStudioTrueLensK1Plus.setOnClickListener(v -> nudge3DParam("trueLensK1", 0.01f, -0.80f, 0.30f));

        // 8. Zoom 3D
        sbStudioZoom3D = findViewById(R.id.sbStudioZoom3D);
        tvStudioZoom3D = findViewById(R.id.tvStudioZoom3D);
        btnStudioZoom3DMinus = findViewById(R.id.btnStudioZoom3DMinus);
        btnStudioZoom3DPlus = findViewById(R.id.btnStudioZoom3DPlus);
        if (sbStudioZoom3D != null) {
            sbStudioZoom3D.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) + 0.50f;
                        save3DParam("zoom3D", v);
                        if (tvStudioZoom3D != null) tvStudioZoom3D.setText(String.format(Locale.US, "%.2fx", v));
                    }
                }
            });
        }
        if (btnStudioZoom3DMinus != null) btnStudioZoom3DMinus.setOnClickListener(v -> nudge3DParam("zoom3D", -0.05f, 0.50f, 3.50f));
        if (btnStudioZoom3DPlus != null) btnStudioZoom3DPlus.setOnClickListener(v -> nudge3DParam("zoom3D", 0.05f, 0.50f, 3.50f));

        // 9. Global Scale
        sbStudioGlobalScale = findViewById(R.id.sbStudioGlobalScale);
        tvStudioGlobalScale = findViewById(R.id.tvStudioGlobalScale);
        btnStudioGlobalScaleMinus = findViewById(R.id.btnStudioGlobalScaleMinus);
        btnStudioGlobalScalePlus = findViewById(R.id.btnStudioGlobalScalePlus);
        if (sbStudioGlobalScale != null) {
            sbStudioGlobalScale.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) + 0.50f;
                        save3DParam("globalScale", v);
                        if (tvStudioGlobalScale != null) tvStudioGlobalScale.setText(String.format(Locale.US, "%.2fx", v));
                    }
                }
            });
        }
        if (btnStudioGlobalScaleMinus != null) btnStudioGlobalScaleMinus.setOnClickListener(v -> nudge3DParam("globalScale", -0.02f, 0.50f, 3.00f));
        if (btnStudioGlobalScalePlus != null) btnStudioGlobalScalePlus.setOnClickListener(v -> nudge3DParam("globalScale", 0.02f, 0.50f, 3.00f));

        // 10. Pan In / Out
        sbStudioPanInOut = findViewById(R.id.sbStudioPanInOut);
        tvStudioPanInOut = findViewById(R.id.tvStudioPanInOut);
        btnStudioPanInOutMinus = findViewById(R.id.btnStudioPanInOutMinus);
        btnStudioPanInOutPlus = findViewById(R.id.btnStudioPanInOutPlus);
        if (sbStudioPanInOut != null) {
            sbStudioPanInOut.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 200.0f) - 1.00f;
                        save3DParam("panInOut", v);
                        if (tvStudioPanInOut != null) tvStudioPanInOut.setText(String.format(Locale.US, "%+.3f", v));
                    }
                }
            });
        }
        if (btnStudioPanInOutMinus != null) btnStudioPanInOutMinus.setOnClickListener(v -> nudge3DParam("panInOut", -0.01f, -1.00f, 1.00f));
        if (btnStudioPanInOutPlus != null) btnStudioPanInOutPlus.setOnClickListener(v -> nudge3DParam("panInOut", 0.01f, -1.00f, 1.00f));

        // 11. Pan Y
        sbStudioPanY = findViewById(R.id.sbStudioPanY);
        tvStudioPanY = findViewById(R.id.tvStudioPanY);
        btnStudioPanYMinus = findViewById(R.id.btnStudioPanYMinus);
        btnStudioPanYPlus = findViewById(R.id.btnStudioPanYPlus);
        if (sbStudioPanY != null) {
            sbStudioPanY.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) - 1.50f;
                        save3DParam("panY", v);
                        if (tvStudioPanY != null) tvStudioPanY.setText(String.format(Locale.US, "%+.2f", v));
                    }
                }
            });
        }
        if (btnStudioPanYMinus != null) btnStudioPanYMinus.setOnClickListener(v -> nudge3DParam("panY", -0.02f, -1.50f, 1.50f));
        if (btnStudioPanYPlus != null) btnStudioPanYPlus.setOnClickListener(v -> nudge3DParam("panY", 0.02f, -1.50f, 1.50f));

        // 12. Pan X
        sbStudioPanX = findViewById(R.id.sbStudioPanX);
        tvStudioPanX = findViewById(R.id.tvStudioPanX);
        btnStudioPanXMinus = findViewById(R.id.btnStudioPanXMinus);
        btnStudioPanXPlus = findViewById(R.id.btnStudioPanXPlus);
        if (sbStudioPanX != null) {
            sbStudioPanX.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 200.0f) - 0.50f;
                        save3DParam("panX", v);
                        if (tvStudioPanX != null) tvStudioPanX.setText(String.format(Locale.US, "%+.3f", v));
                    }
                }
            });
        }
        if (btnStudioPanXMinus != null) btnStudioPanXMinus.setOnClickListener(v -> nudge3DParam("panX", -0.005f, -0.50f, 0.50f));
        if (btnStudioPanXPlus != null) btnStudioPanXPlus.setOnClickListener(v -> nudge3DParam("panX", 0.005f, -0.50f, 0.50f));

        // 13. Sensor Width
        sbStudioSensorWidth = findViewById(R.id.sbStudioSensorWidth);
        tvStudioSensorWidth = findViewById(R.id.tvStudioSensorWidth);
        btnStudioSensorWidthMinus = findViewById(R.id.btnStudioSensorWidthMinus);
        btnStudioSensorWidthPlus = findViewById(R.id.btnStudioSensorWidthPlus);
        if (sbStudioSensorWidth != null) {
            sbStudioSensorWidth.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) + 0.40f;
                        save3DSensorSize("width", v);
                        if (tvStudioSensorWidth != null) tvStudioSensorWidth.setText(String.format(Locale.US, "%.2f", v));
                    }
                }
            });
        }
        if (btnStudioSensorWidthMinus != null) btnStudioSensorWidthMinus.setOnClickListener(v -> nudge3DSensorSize("width", -0.02f));
        if (btnStudioSensorWidthPlus != null) btnStudioSensorWidthPlus.setOnClickListener(v -> nudge3DSensorSize("width", 0.02f));

        // 14. Sensor Height
        sbStudioSensorHeight = findViewById(R.id.sbStudioSensorHeight);
        tvStudioSensorHeight = findViewById(R.id.tvStudioSensorHeight);
        btnStudioSensorHeightMinus = findViewById(R.id.btnStudioSensorHeightMinus);
        btnStudioSensorHeightPlus = findViewById(R.id.btnStudioSensorHeightPlus);
        if (sbStudioSensorHeight != null) {
            sbStudioSensorHeight.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && !suppressSync) {
                        float v = (p / 100.0f) + 0.40f;
                        save3DSensorSize("height", v);
                        if (tvStudioSensorHeight != null) tvStudioSensorHeight.setText(String.format(Locale.US, "%.2f", v));
                    }
                }
            });
        }
        if (btnStudioSensorHeightMinus != null) btnStudioSensorHeightMinus.setOnClickListener(v -> nudge3DSensorSize("height", -0.02f));
        if (btnStudioSensorHeightPlus != null) btnStudioSensorHeightPlus.setOnClickListener(v -> nudge3DSensorSize("height", 0.02f));

        btnStudioExportJson = findViewById(R.id.btnStudioExportJson);
        if (btnStudioExportJson != null) {
            btnStudioExportJson.setOnClickListener(v -> {
                boolean ok = Perspective3DParams.exportToLabJsonFile(prefs);
                if (ok) {
                    Toast.makeText(this, R.string.studio_toast_export_success, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void save3DSensorSize(String dimension, float val) {
        String prefix = isTurnRight ? "R" : "L";
        Perspective3DParams def = isTurnRight ? Perspective3DParams.createAtto3RightDefault() : Perspective3DParams.createAtto3LeftDefault();
        Perspective3DParams p = new Perspective3DParams();
        p.load(prefs, prefix, def);
        if ("width".equals(dimension)) {
            p.setSensorWidth(val);
        } else {
            p.setSensorHeight(val);
        }
        SharedPreferences.Editor ed = prefs.edit();
        p.save(ed, prefix);
        ed.apply();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void nudge3DSensorSize(String dimension, float delta) {
        String prefix = isTurnRight ? "R" : "L";
        Perspective3DParams def = isTurnRight ? Perspective3DParams.createAtto3RightDefault() : Perspective3DParams.createAtto3LeftDefault();
        Perspective3DParams p = new Perspective3DParams();
        p.load(prefs, prefix, def);
        float cur = "width".equals(dimension) ? p.getSensorWidth() : p.getSensorHeight();
        float next = Math.max(0.40f, Math.min(1.00f, Math.round((cur + delta) * 100f) / 100f));
        save3DSensorSize(dimension, next);
        loadCurrentModeIntoControls();
    }

    private void save3DParam(String name, float val) {
        String prefix = isTurnRight ? "R" : "L";
        prefs.edit().putFloat(prefix + "_" + name, val).apply();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void nudge3DParam(String name, float delta, float min, float max) {
        String prefix = isTurnRight ? "R" : "L";
        Perspective3DParams def = isTurnRight ? Perspective3DParams.createAtto3RightDefault() : Perspective3DParams.createAtto3LeftDefault();
        float defVal = 0f;
        if (name.equals("poleTilt")) defVal = def.poleTilt;
        else if (name.equals("carBodyTilt")) defVal = def.carBodyTilt;
        else if (name.equals("pitchKeystone")) defVal = def.pitchKeystone;
        else if (name.equals("horizonStraighten")) defVal = def.horizonStraighten;
        else if (name.equals("rollDeg")) defVal = def.rollDeg;
        else if (name.equals("cylindrical")) defVal = def.cylindrical;
        else if (name.equals("trueLensK1")) defVal = def.trueLensK1;
        else if (name.equals("zoom3D")) defVal = def.zoom3D;
        else if (name.equals("globalScale")) defVal = def.globalScale;
        else if (name.equals("panInOut")) defVal = def.panInOut;
        else if (name.equals("panY")) defVal = def.panY;
        else if (name.equals("panX")) defVal = def.panX;

        float cur = prefs.getFloat(prefix + "_" + name, defVal);
        float next = Math.max(min, Math.min(max, Math.round((cur + delta) * 1000.0f) / 1000.0f));
        save3DParam(name, next);
        loadCurrentModeIntoControls();
    }

    private void setupStudioExtraFeatures() {
        Button btnStudioTestAdas = findViewById(R.id.btnStudioTestAdas);
        if (btnStudioTestAdas != null) {
            btnStudioTestAdas.setVisibility(activeMode == 1 ? View.VISIBLE : View.GONE);
            btnStudioTestAdas.setOnClickListener(v -> {
                OverlayService overlay = OverlayService.getInstance();
                if (overlay == null || !overlay.isShowing()) {
                    OverlayService.allowStudioPipTest = true;
                    Intent showIntent = new Intent(CameraStudioActivity.this, OverlayService.class);
                    showIntent.setAction("SHOW_LEFT");
                    startService(showIntent);
                }
                Intent testIntent = new Intent(CameraStudioActivity.this, OverlayService.class);
                testIntent.setAction("TEST_ADAS");
                startService(testIntent);
                Toast.makeText(CameraStudioActivity.this, tr("🚨 จำลองเตือนจุดอับสายตา ADAS 5 วินาที",
                    "🚨 Simulated ADAS Blind Spot Alert (5s)"), Toast.LENGTH_SHORT).show();
            });
        }

        View layoutStudioAdasToggle = findViewById(R.id.layoutStudioAdasToggle);
        Switch swStudioAdasBsdAlert = findViewById(R.id.swStudioAdasBsdAlert);
        if (layoutStudioAdasToggle != null) {
            layoutStudioAdasToggle.setVisibility(activeMode == 1 ? View.VISIBLE : View.GONE);
        }
        if (swStudioAdasBsdAlert != null) {
            swStudioAdasBsdAlert.setChecked(prefs.getBoolean("adas_bsd_warning_enabled", true));
            swStudioAdasBsdAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("adas_bsd_warning_enabled", isChecked).apply();
                Intent intent = new Intent(CameraStudioActivity.this, OverlayService.class);
                intent.setAction("RELOAD_PREFS");
                startService(intent);
            });
        }

        View layoutStudioAutomation = findViewById(R.id.layoutStudioAutomation);
        if (layoutStudioAutomation != null) {
            layoutStudioAutomation.setVisibility(activeMode == 3 ? View.VISIBLE : View.GONE);
        }

        if (activeMode != 3) return;

        // Live Telemetry Status Banner in Studio Mode 3
        TextView tvStudioLiveStatus = findViewById(R.id.tvStudioLiveStatus);
        if (tvStudioLiveStatus != null) {
            if (studioStatusHandler == null) {
                studioStatusHandler = new Handler(Looper.getMainLooper());
            }
            if (studioStatusRunnable != null) {
                studioStatusHandler.removeCallbacks(studioStatusRunnable);
            }
            studioStatusRunnable = new Runnable() {
                @Override
                public void run() {
                    OverlayService overlay = OverlayService.getInstance();
                    if (overlay != null && overlay.getBevAutomation() != null) {
                        BevAutomationManager mgr = overlay.getBevAutomation();
                        int gear = mgr.getGear();
                        String gearStr = gear == 1 ? "P" : (gear == 2 ? "R" : (gear == 3 ? "N" : (gear == 4 ? "D" : "-")));
                        double angle = mgr.getSteeringAngle();
                        double speed = mgr.getSpeed();
                        int activeTrig = mgr.getActiveTrigger();
                        String trigStr = activeTrig == BevAutomationManager.TRIGGER_EXIT_PARK ? tr(" [ทำงาน: ออกจาก P]", " [Trigger: Exit P]") :
                                        (activeTrig == BevAutomationManager.TRIGGER_STEERING ? tr(" [ทำงาน: เลี้ยวพวงมาลัย]", " [Trigger: Steering]") :
                                        (activeTrig == BevAutomationManager.TRIGGER_MANUAL ? tr(" [เปิดเอง]", " [Manual]") : ""));
                        tvStudioLiveStatus.setText(String.format(java.util.Locale.getDefault(),
                            tr("📡 สถานะสด: เกียร์ %s · พวงมาลัย %.0f° · ความเร็ว %.0f กม./ชม.%s",
                               "📡 Live Status: Gear %s · Steer %.0f° · Speed %.0f km/h%s"),
                            gearStr, angle, speed, trigStr));
                    } else {
                        tvStudioLiveStatus.setText(tr("📡 สถานะสด: กำลังรอการเชื่อมต่อระบบรถ...",
                            "📡 Live Status: Waiting for vehicle connection..."));
                    }
                    if (studioStatusHandler != null && studioStatusRunnable != null) {
                        studioStatusHandler.postDelayed(this, 350L);
                    }
                }
            };
            studioStatusHandler.post(studioStatusRunnable);
        }

        // Auto 1: Exit Park
        Switch swStudioExitPark = findViewById(R.id.swStudioExitPark);
        TextView tvStudioExitDuration = findViewById(R.id.tvStudioExitDuration);
        Button btnStudioExitDurationMinus = findViewById(R.id.btnStudioExitDurationMinus);
        Button btnStudioExitDurationPlus = findViewById(R.id.btnStudioExitDurationPlus);

        if (swStudioExitPark != null) {
            swStudioExitPark.setChecked(prefs.getBoolean("bev_auto_exit_park", true));
            swStudioExitPark.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean("bev_auto_exit_park", isChecked).apply();
                OverlayService.reapplyLive();
            });
        }

        Runnable updateExitDurText = () -> {
            if (tvStudioExitDuration != null) {
                int s = Math.round(getSafeFloat("bev_exit_park_duration_sec", 8.0f));
                tvStudioExitDuration.setText(tr("ระยะเวลาแสดงผล: " + s + " วินาที", "Duration: " + s + " s"));
            }
        };
        updateExitDurText.run();

        if (btnStudioExitDurationMinus != null) {
            btnStudioExitDurationMinus.setOnClickListener(v -> {
                int cur = Math.round(getSafeFloat("bev_exit_park_duration_sec", 8.0f));
                if (cur > 3) {
                    prefs.edit().putFloat("bev_exit_park_duration_sec", (float) (cur - 1)).apply();
                    updateExitDurText.run();
                    OverlayService.reapplyLive();
                }
            });
        }
        if (btnStudioExitDurationPlus != null) {
            btnStudioExitDurationPlus.setOnClickListener(v -> {
                int cur = Math.round(getSafeFloat("bev_exit_park_duration_sec", 8.0f));
                if (cur < 30) {
                    prefs.edit().putFloat("bev_exit_park_duration_sec", (float) (cur + 1)).apply();
                    updateExitDurText.run();
                    OverlayService.reapplyLive();
                }
            });
        }

        // Auto 2: Steering Wheel Angle
        Switch swStudioSteering = findViewById(R.id.swStudioSteering);
        TextView tvStudioSteeringAngle = findViewById(R.id.tvStudioSteeringAngle);
        Button btnStudioAngleMinus = findViewById(R.id.btnStudioAngleMinus);
        Button btnStudioAnglePlus = findViewById(R.id.btnStudioAnglePlus);
        SeekBar sbStudioSteeringAngle = findViewById(R.id.sbStudioSteeringAngle);

        if (swStudioSteering != null) {
            swStudioSteering.setChecked(prefs.getBoolean("bev_auto_steering", true));
            swStudioSteering.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean("bev_auto_steering", isChecked).apply();
                OverlayService.reapplyLive();
            });
        }

        Runnable updateSteerText = () -> {
            int deg = getSafeInt("bev_steering_angle", 90);
            if (tvStudioSteeringAngle != null) {
                tvStudioSteeringAngle.setText(tr("องศาเลี้ยวที่เริ่มทำงาน: " + deg + "°", "Trigger Angle: " + deg + "°"));
            }
            if (sbStudioSteeringAngle != null && sbStudioSteeringAngle.getProgress() != deg) {
                sbStudioSteeringAngle.setProgress(deg);
            }
        };
        updateSteerText.run();

        if (btnStudioAngleMinus != null) {
            btnStudioAngleMinus.setOnClickListener(v -> {
                int cur = getSafeInt("bev_steering_angle", 90);
                if (cur > 30) {
                    prefs.edit().putInt("bev_steering_angle", cur - 10).apply();
                    updateSteerText.run();
                    OverlayService.reapplyLive();
                }
            });
        }
        if (btnStudioAnglePlus != null) {
            btnStudioAnglePlus.setOnClickListener(v -> {
                int cur = getSafeInt("bev_steering_angle", 90);
                if (cur < 360) {
                    prefs.edit().putInt("bev_steering_angle", cur + 10).apply();
                    updateSteerText.run();
                    OverlayService.reapplyLive();
                }
            });
        }
        if (sbStudioSteeringAngle != null) {
            sbStudioSteeringAngle.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int snap = Math.max(30, Math.round(progress / 10.0f) * 10);
                        prefs.edit().putInt("bev_steering_angle", snap).apply();
                        if (tvStudioSteeringAngle != null) {
                            tvStudioSteeringAngle.setText(tr("องศาเลี้ยวที่เริ่มทำงาน: " + snap + "°", "Trigger Angle: " + snap + "°"));
                        }
                        OverlayService.reapplyLive();
                    }
                }
            });
        }

        // Auto 3: Speed Limit
        Switch swStudioSpeedLimit = findViewById(R.id.swStudioSpeedLimit);
        TextView tvStudioSpeedLimit = findViewById(R.id.tvStudioSpeedLimit);
        Button btnStudioSpeedMinus = findViewById(R.id.btnStudioSpeedMinus);
        Button btnStudioSpeedPlus = findViewById(R.id.btnStudioSpeedPlus);

        if (swStudioSpeedLimit != null) {
            swStudioSpeedLimit.setChecked(prefs.getBoolean("bev_auto_speed_limit", true));
            swStudioSpeedLimit.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean("bev_auto_speed_limit", isChecked).apply();
                OverlayService.reapplyLive();
            });
        }

        Runnable updateSpeedText = () -> {
            int kmh = Math.round(getSafeFloat("bev_speed_limit_kmh", 20.0f));
            if (tvStudioSpeedLimit != null) {
                tvStudioSpeedLimit.setText(tr("ความเร็วจำกัด: " + kmh + " กม./ชม.", "Cutoff Speed: " + kmh + " km/h"));
            }
        };
        updateSpeedText.run();

        if (btnStudioSpeedMinus != null) {
            btnStudioSpeedMinus.setOnClickListener(v -> {
                int cur = Math.round(getSafeFloat("bev_speed_limit_kmh", 20.0f));
                if (cur > 5) {
                    prefs.edit().putFloat("bev_speed_limit_kmh", (float) (cur - 5)).apply();
                    updateSpeedText.run();
                    OverlayService.reapplyLive();
                }
            });
        }
        if (btnStudioSpeedPlus != null) {
            btnStudioSpeedPlus.setOnClickListener(v -> {
                int cur = Math.round(getSafeFloat("bev_speed_limit_kmh", 20.0f));
                if (cur < 60) {
                    prefs.edit().putFloat("bev_speed_limit_kmh", (float) (cur + 5)).apply();
                    updateSpeedText.run();
                    OverlayService.reapplyLive();
                }
            });
        }
    }

    private int getSafeInt(String key, int defVal) {
        if (prefs == null) return defVal;
        try {
            return prefs.getInt(key, defVal);
        } catch (ClassCastException e) {
            try {
                return Math.round(prefs.getFloat(key, (float) defVal));
            } catch (Exception ex) {
                return defVal;
            }
        }
    }

    private float getSafeFloat(String key, float defVal) {
        if (prefs == null) return defVal;
        try {
            return prefs.getFloat(key, defVal);
        } catch (ClassCastException e) {
            try {
                return (float) prefs.getInt(key, (int) defVal);
            } catch (Exception ex) {
                return defVal;
            }
        }
    }

    private float getTargetPipAspect() {
        if (activeMode == 1) {
            return 5.0f / 3.0f; // 1.6667 (Approved ATTO 3 PIP Aspect)
        }
        if (activeMode == 2) {
            int aspectChoice = prefs != null ? prefs.getInt("rear_aspect", 0) : 0;
            return aspectChoice == 1 ? 4.0f : 3.0f;
        }
        return 270.0f / 405.0f; // 2:3 vertical aspect ratio for front wheel curb view
    }

    private void updatePreviewAspectRatio(float targetAspect) {
        if (studioPreviewContainer == null || studioAspectBox == null) return;
        studioPreviewContainer.post(() -> {
            int containerW = studioPreviewContainer.getWidth() - studioPreviewContainer.getPaddingLeft() - studioPreviewContainer.getPaddingRight();
            int containerH = studioPreviewContainer.getHeight() - studioPreviewContainer.getPaddingTop() - studioPreviewContainer.getPaddingBottom();
            if (containerW <= 0 || containerH <= 0) return;

            int boxW, boxH;
            if ((float) containerW / (float) containerH > targetAspect) {
                boxH = containerH;
                boxW = Math.round(boxH * targetAspect);
            } else {
                boxW = containerW;
                boxH = Math.round(boxW / targetAspect);
            }

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) studioAspectBox.getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(boxW, boxH, android.view.Gravity.CENTER);
            } else {
                lp.width = boxW;
                lp.height = boxH;
                lp.gravity = android.view.Gravity.CENTER;
            }
            studioAspectBox.setLayoutParams(lp);
            if (studioGlView != null) {
                studioGlView.requestLayout();
            }
        });
    }

    private void setupModeEnvironment() {
        if (previewView != null) {
            previewView.setPipMode(activeMode);
        }
        updatePreviewAspectRatio(getTargetPipAspect());
        if (activeMode == 1) {
            tvStudioTitle.setText(tr("สตูดิโอตั้งค่า: กล้องช่วยเลี้ยว 3D (Turn Signal Camera)", "Calibration Studio: Turn Signal 3D"));
            dockTurnTabs.setVisibility(View.VISIBLE);
            dockWheelGrid.setVisibility(View.GONE);
            if (layoutStudio3DControls != null) layoutStudio3DControls.setVisibility(View.VISIBLE);
            if (layoutStudio2DControls != null) layoutStudio2DControls.setVisibility(View.GONE);
            selectTurnSide(false);
        } else if (activeMode == 2) {
            tvStudioTitle.setText(tr("สตูดิโอตั้งค่า: กระจกมองหลังดิจิทัล (Digital Rearview)", "Calibration Studio: Digital Rearview Mirror"));
            dockTurnTabs.setVisibility(View.GONE);
            dockWheelGrid.setVisibility(View.GONE);
            if (layoutStudio3DControls != null) layoutStudio3DControls.setVisibility(View.GONE);
            if (layoutStudio2DControls != null) layoutStudio2DControls.setVisibility(View.VISIBLE);
        } else {
            tvStudioTitle.setText(tr("สตูดิโอตั้งค่า: มุมล้อหน้าซ้ายและขวา (Front Wheels)", "Calibration Studio: Front Wheels (FL & FR)"));
            dockTurnTabs.setVisibility(View.GONE);
            dockWheelGrid.setVisibility(View.VISIBLE);
            if (layoutStudio3DControls != null) layoutStudio3DControls.setVisibility(View.GONE);
            if (layoutStudio2DControls != null) layoutStudio2DControls.setVisibility(View.VISIBLE);
            selectWheel(0);
        }
    }

    private void selectTurnSide(boolean isRight) {
        this.isTurnRight = isRight;
        btnStudioSideLeft.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isRight ? 0x00000000 : 0xFF3E6AE1));
        btnStudioSideLeft.setTextColor(isRight ? 0xFF8E8E93 : 0xFFFFFFFF);
        btnStudioSideRight.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isRight ? 0xFF3E6AE1 : 0x00000000));
        btnStudioSideRight.setTextColor(isRight ? 0xFFFFFFFF : 0xFF8E8E93);

        loadCurrentModeIntoControls();
    }

    private void selectWheel(int idx) {
        this.isQuadOverviewMode = false;
        this.selectedWheelIdx = Math.max(0, Math.min(1, idx));
        if (btnStudioWheelFL != null) {
            boolean isFL = (selectedWheelIdx == 0);
            btnStudioWheelFL.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isFL ? 0xFF3E6AE1 : 0x00000000));
            btnStudioWheelFL.setTextColor(isFL ? 0xFFFFFFFF : 0xFF8E8E93);
        }
        if (btnStudioWheelFR != null) {
            boolean isFR = (selectedWheelIdx == 1);
            btnStudioWheelFR.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isFR ? 0xFF3E6AE1 : 0x00000000));
            btnStudioWheelFR.setTextColor(isFR ? 0xFFFFFFFF : 0xFF8E8E93);
        }
        loadCurrentModeIntoControls();
    }

    private void selectWheelAll() {
        selectWheel(0);
    }

    private String getActiveTargetKey() {
        if (activeMode == 1) {
            return isTurnRight ? "r" : "l";
        } else if (activeMode == 2) {
            return "rear";
        } else {
            return selectedWheelIdx == 0 ? "fl" : "fr";
        }
    }

    private String getTargetLabel() {
        if (activeMode == 1) {
            return isTurnRight ? tr("กล้องขวา (Right)", "Right Camera (Right)") : tr("กล้องซ้าย (Left)", "Left Camera (Left)");
        } else if (activeMode == 2) {
            return tr("กระจกมองหลัง (Rearview)", "Rearview Mirror (Rear)");
        } else {
            return selectedWheelIdx == 0 ? tr("ล้อหน้าซ้าย (FL)", "Front-Left (FL)") : tr("ล้อหน้าขวา (FR)", "Front-Right (FR)");
        }
    }

    // Canonical Preference Key Helpers for "pnp" SharedPreferences
    private String getKeyRot() {
        if (activeMode == 1) return "dr_rot_" + (isTurnRight ? "r" : "l");
        if (activeMode == 2) return "rear_rot_l";
        return "quad_rot_" + getActiveTargetKey();
    }

    private String getKeyFine() {
        if (activeMode == 1) return "dr_fn_" + (isTurnRight ? "r" : "l");
        if (activeMode == 2) return "rear_fn_l";
        return "quad_fn_" + getActiveTargetKey();
    }

    private String getKeyMir() {
        if (activeMode == 1) return "dr_mir_" + (isTurnRight ? "r" : "l");
        if (activeMode == 2) return "rear_mir_l";
        return "quad_mir_" + getActiveTargetKey();
    }

    private String getKeyFv() {
        if (activeMode == 1) return "dr_fv_" + (isTurnRight ? "r" : "l");
        if (activeMode == 2) return "rear_fv_l";
        return "quad_fv_" + getActiveTargetKey();
    }

    private String getKeyCrop(int idx) {
        if (activeMode == 1) return "dr_" + (isTurnRight ? "r" : "l") + idx;
        if (activeMode == 2) return "rear_l" + idx;
        return "quad_crop_" + getActiveTargetKey() + idx;
    }

    private String getKeyFeStr() {
        if (activeMode == 1) return "dr_str_" + (isTurnRight ? "r" : "l");
        if (activeMode == 2) return "rear_str_l";
        return "quad_str_" + getActiveTargetKey();
    }

    private String getKeyFeZoom() {
        if (activeMode == 1) return "dr_zoom_" + (isTurnRight ? "r" : "l");
        if (activeMode == 2) return "rear_zoom_l";
        return "quad_zoom_" + getActiveTargetKey();
    }

    private String getKeyFeCx() {
        if (activeMode == 1) return "dr_cx_" + (isTurnRight ? "r" : "l");
        if (activeMode == 2) return "rear_cx_l";
        return "quad_cx_" + getActiveTargetKey();
    }

    private String getKeyFeCy() {
        if (activeMode == 1) return "dr_cy_" + (isTurnRight ? "r" : "l");
        if (activeMode == 2) return "rear_cy_l";
        return "quad_cy_" + getActiveTargetKey();
    }

    private int getStoredRotation() {
        if (activeMode == 3) {
            float defRot = (selectedWheelIdx == 0 || selectedWheelIdx == 3) ? 270f : 90f;
            return (int) prefs.getFloat(getKeyRot(), defRot);
        }
        return prefs.getInt(getKeyRot(), (activeMode == 1 && isTurnRight) ? 270 : 0);
    }

    private void saveRotation(int deg) {
        SharedPreferences.Editor ed = prefs.edit();
        if (activeMode == 3) {
            ed.putFloat(getKeyRot(), (float) deg);
        } else {
            ed.putInt(getKeyRot(), deg);
        }
        ed.apply();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void loadCurrentModeIntoControls() {
        suppressSync = true;

        // 1. Rotation & Mirror
        int rot = getStoredRotation();
        float defFine = (activeMode == 1) ? (isTurnRight ? 27.0f : 48.0f) : 0f;
        boolean defMirror = (activeMode == 1) ? true : (activeMode == 2);
        float fine = prefs.getFloat(getKeyFine(), defFine);
        boolean mirror = prefs.getBoolean(getKeyMir(), defMirror);
        boolean flipV = prefs.getBoolean(getKeyFv(), false);

        tvStudioRotateVal.setText(rot + "°");
        tvStudioFineVal.setText(String.format(Locale.US, "%+.1f°", fine));
        swStudioMirror.setChecked(mirror);
        swStudioFlipV.setChecked(flipV);

        // 2. Crop
        float defL = (activeMode == 1) ? (isTurnRight ? 0.21f : 0.22f) : 0.0f;
        float defT = (activeMode == 1) ? (isTurnRight ? 0.62f : 0.57f) : ((activeMode == 2) ? 0.20f : ((selectedWheelIdx < 2) ? 0.00f : 0.45f));
        float defR = (activeMode == 1) ? (isTurnRight ? 0.75f : 0.81f) : 1.0f;
        float defB = (activeMode == 1) ? (isTurnRight ? 0.89f : 0.97f) : ((activeMode == 2) ? 0.80f : ((selectedWheelIdx < 2) ? 0.55f : 1.00f));

        float cropL = prefs.getFloat(getKeyCrop(0), defL);
        float cropT = prefs.getFloat(getKeyCrop(1), defT);
        float cropR = prefs.getFloat(getKeyCrop(2), defR);
        float cropB = prefs.getFloat(getKeyCrop(3), defB);

        sbStudioCropT.setProgress((int) (cropT * 100));
        sbStudioCropB.setProgress((int) (cropB * 100));
        sbStudioCropL.setProgress((int) (cropL * 100));
        sbStudioCropR.setProgress((int) (cropR * 100));
        tvStudioCropT.setText(String.format(Locale.US, "%.2f", cropT));
        tvStudioCropB.setText(String.format(Locale.US, "%.2f", cropB));
        tvStudioCropL.setText(String.format(Locale.US, "%.2f", cropL));
        tvStudioCropR.setText(String.format(Locale.US, "%.2f", cropR));

        // 3. Fisheye
        float defStr = (activeMode == 1) ? (isTurnRight ? -0.38f : -0.24f) : ((activeMode == 3) ? -0.15f : 0.0f);
        float defZoom = (activeMode == 1) ? (isTurnRight ? 0.91f : 1.14f) : ((activeMode == 3) ? 1.25f : 1.0f);
        float defCx = (activeMode == 1) ? (isTurnRight ? 0.66f : 0.40f) : 0.50f;
        float defCy = (activeMode == 1) ? (isTurnRight ? 0.68f : 0.5601612f) : ((activeMode == 3) ? ((selectedWheelIdx < 2) ? 0.35f : 0.65f) : 0.50f);

        float str = prefs.getFloat(getKeyFeStr(), defStr);
        float zoom = prefs.getFloat(getKeyFeZoom(), defZoom);
        float cx = prefs.getFloat(getKeyFeCx(), defCx);
        float cy = prefs.getFloat(getKeyFeCy(), defCy);

        sbStudioFeStr.setProgress((int) ((str + 0.50f) * 100));
        sbStudioFeZoom.setProgress((int) ((zoom - 0.50f) * 100));
        sbStudioFeCx.setProgress((int) ((cx - 0.10f) * 100));
        sbStudioFeCy.setProgress((int) ((cy - 0.10f) * 100));
        tvStudioFeStr.setText(String.format(Locale.US, "%.2f", str));
        tvStudioFeZoom.setText(String.format(Locale.US, "%.2fx", zoom));
        tvStudioFeCx.setText(String.format(Locale.US, "%.2f", cx));
        tvStudioFeCy.setText(String.format(Locale.US, "%.2f", cy));

        // 4. Update Camera Slot Spinner for current selected camera
        if (spinnerStudioCamSlot != null && layoutStudioCamSlot != null) {
            if (activeMode == 1 || activeMode == 2) {
                layoutStudioCamSlot.setVisibility(View.VISIBLE);
                String prefKey = (activeMode == 2) ? "rear_cam_index" : (isTurnRight ? "right_cam_index" : "left_cam_index");
                int defVal = (activeMode == 2) ? 1 : (isTurnRight ? 3 : 2);
                int savedSlot = prefs.getInt(prefKey, defVal);
                if (tvStudioCamSlotLabel != null) {
                    String camName = (activeMode == 2) ? tr("กล้องหลัง (Rear)", "Rear Camera")
                                   : (isTurnRight ? tr("กล้องขวา (Right)", "Right Camera")
                                                  : tr("กล้องซ้าย (Left)", "Left Camera"));
                    tvStudioCamSlotLabel.setText(tr("ลำดับพอร์ต " + camName + ":", camName + " Slot:"));
                }
                spinnerStudioCamSlot.setSelection(Math.max(0, Math.min(5, savedSlot)));
            } else {
                layoutStudioCamSlot.setVisibility(View.GONE);
            }
        }

        // 5. 3D Perspective Controls Sync (Mode 1: Turn Signal)
        if (activeMode == 1) {
            String pfx = isTurnRight ? "R" : "L";
            Perspective3DParams defP = isTurnRight ? Perspective3DParams.createAtto3RightDefault() : Perspective3DParams.createAtto3LeftDefault();
            Perspective3DParams p = new Perspective3DParams();
            p.load(prefs, pfx, defP);

            if (sbStudioPoleTilt != null) sbStudioPoleTilt.setProgress(Math.round((p.poleTilt + 1.00f) * 100));
            if (tvStudioPoleTilt != null) tvStudioPoleTilt.setText(String.format(Locale.US, "%+.2f", p.poleTilt));

            if (sbStudioCarBodyTilt != null) sbStudioCarBodyTilt.setProgress(Math.round((p.carBodyTilt + 1.00f) * 100));
            if (tvStudioCarBodyTilt != null) tvStudioCarBodyTilt.setText(String.format(Locale.US, "%+.2f", p.carBodyTilt));

            if (sbStudioPitchKeystone != null) sbStudioPitchKeystone.setProgress(Math.round((p.pitchKeystone + 0.50f) * 100));
            if (tvStudioPitchKeystone != null) tvStudioPitchKeystone.setText(String.format(Locale.US, "%+.2f", p.pitchKeystone));

            if (sbStudioHorizonStraighten != null) sbStudioHorizonStraighten.setProgress(Math.round((p.horizonStraighten + 1.00f) * 100));
            if (tvStudioHorizonStraighten != null) tvStudioHorizonStraighten.setText(String.format(Locale.US, "%+.2f", p.horizonStraighten));

            if (sbStudioRollDeg != null) sbStudioRollDeg.setProgress(Math.round((p.rollDeg + 30.0f) * 10));
            if (tvStudioRollDeg != null) tvStudioRollDeg.setText(String.format(Locale.US, "%+.1f°", p.rollDeg));

            if (sbStudioCylindrical != null) sbStudioCylindrical.setProgress(Math.round((p.cylindrical + 0.50f) * 100));
            if (tvStudioCylindrical != null) tvStudioCylindrical.setText(String.format(Locale.US, "%.2f", p.cylindrical));

            if (sbStudioTrueLensK1 != null) sbStudioTrueLensK1.setProgress(Math.round((p.trueLensK1 + 0.80f) * 100));
            if (tvStudioTrueLensK1 != null) tvStudioTrueLensK1.setText(String.format(Locale.US, "%.2f", p.trueLensK1));

            if (sbStudioZoom3D != null) sbStudioZoom3D.setProgress(Math.round((p.zoom3D - 0.50f) * 100));
            if (tvStudioZoom3D != null) tvStudioZoom3D.setText(String.format(Locale.US, "%.2fx", p.zoom3D));

            if (sbStudioGlobalScale != null) sbStudioGlobalScale.setProgress(Math.round((p.globalScale - 0.50f) * 100));
            if (tvStudioGlobalScale != null) tvStudioGlobalScale.setText(String.format(Locale.US, "%.2fx", p.globalScale));

            if (sbStudioPanInOut != null) sbStudioPanInOut.setProgress(Math.round((p.panInOut + 1.00f) * 200));
            if (tvStudioPanInOut != null) tvStudioPanInOut.setText(String.format(Locale.US, "%+.3f", p.panInOut));

            if (sbStudioPanY != null) sbStudioPanY.setProgress(Math.round((p.panY + 1.50f) * 100));
            if (tvStudioPanY != null) tvStudioPanY.setText(String.format(Locale.US, "%+.2f", p.panY));

            if (sbStudioPanX != null) sbStudioPanX.setProgress(Math.round((p.panX + 0.50f) * 200));
            if (tvStudioPanX != null) tvStudioPanX.setText(String.format(Locale.US, "%+.3f", p.panX));

            if (sbStudioSensorWidth != null) sbStudioSensorWidth.setProgress(Math.round((p.getSensorWidth() - 0.40f) * 100));
            if (tvStudioSensorWidth != null) tvStudioSensorWidth.setText(String.format(Locale.US, "%.2f", p.getSensorWidth()));

            if (sbStudioSensorHeight != null) sbStudioSensorHeight.setProgress(Math.round((p.getSensorHeight() - 0.40f) * 100));
            if (tvStudioSensorHeight != null) tvStudioSensorHeight.setText(String.format(Locale.US, "%.2f", p.getSensorHeight()));

            if (swStudio3DMode != null) swStudio3DMode.setChecked(prefs.getBoolean("perspective_3d_enabled", true));
        }

        suppressSync = false;
        updatePreviewHud();
        updateStudioGlParams();
    }

    private void updatePreviewHud() {
        if (activeMode == 1) {
            String pfx = isTurnRight ? "R" : "L";
            Perspective3DParams defP = isTurnRight ? Perspective3DParams.createAtto3RightDefault() : Perspective3DParams.createAtto3LeftDefault();
            Perspective3DParams p = new Perspective3DParams();
            p.load(prefs, pfx, defP);
            previewView.updateReadouts(p.panInOut, p.panY, p.zoom3D, p.rollDeg, getTargetLabel());
            return;
        }
        float cx = sbStudioFeCx.getProgress() / 100.0f + 0.10f;
        float cy = sbStudioFeCy.getProgress() / 100.0f + 0.10f;
        float zoom = sbStudioFeZoom.getProgress() / 100.0f + 0.50f;
        float fine = prefs.getFloat(getKeyFine(), 0f);

        previewView.updateReadouts(cx, cy, zoom, fine, getTargetLabel());
    }

    private void updateStudioGlParams() {
        if (studioGlView == null) return;

        if (activeMode == 1) {
            boolean isR = isTurnRight;
            String side = isR ? "r" : "l";
            int rot = prefs.getInt("dr_rot_" + side, isR ? 270 : 0);
            float fine = prefs.getFloat("dr_fn_" + side, isR ? 27.0f : 48.0f);
            boolean mir = prefs.getBoolean("dr_mir_" + side, true);
            boolean fv = prefs.getBoolean("dr_fv_" + side, false);
            float cropL = prefs.getFloat("dr_" + side + "0", isR ? 0.21f : 0.22f);
            float cropT = prefs.getFloat("dr_" + side + "1", isR ? 0.62f : 0.57f);
            float cropR = prefs.getFloat("dr_" + side + "2", isR ? 0.75f : 0.81f);
            float cropB = prefs.getFloat("dr_" + side + "3", isR ? 0.89f : 0.97f);
            float str = prefs.getFloat("dr_str_" + side, isR ? -0.38f : -0.24f);
            float zoom = prefs.getFloat("dr_zoom_" + side, isR ? 0.91f : 1.14f);
            float cx = prefs.getFloat("dr_cx_" + side, isR ? 0.66f : 0.40f);
            float cy = prefs.getFloat("dr_cy_" + side, isR ? 0.68f : 0.5601612f);
            float[] crop = new float[]{cropL, cropT, cropR, cropB};

            Perspective3DParams p3dL = new Perspective3DParams();
            p3dL.load(prefs, "L", Perspective3DParams.createAtto3LeftDefault());
            Perspective3DParams p3dR = new Perspective3DParams();
            p3dR.load(prefs, "R", Perspective3DParams.createAtto3RightDefault());
            boolean p3dGlobal = prefs.getBoolean("perspective_3d_enabled", true);
            p3dL.enabled = p3dGlobal;
            p3dR.enabled = p3dGlobal;

            studioGlView.setParams3DLeft(p3dL);
            studioGlView.setParams3DRight(p3dR);

            studioGlView.setDualMode(false, isR ? 2 : 1);
            if (isR) {
                studioGlView.setParamsRight(rot, fine, mir, fv, crop, str, zoom, cx, cy);
            } else {
                studioGlView.setParamsLeft(rot, fine, mir, fv, crop, str, zoom, cx, cy);
            }
        } else if (activeMode == 2) {
            int rot = prefs.getInt("rear_rot_l", 0);
            float fine = prefs.getFloat("rear_fn_l", 0f);
            boolean mir = prefs.getBoolean("rear_mir_l", true);
            boolean fv = prefs.getBoolean("rear_fv_l", false);
            float cropL = prefs.getFloat("rear_l0", 0.00f);
            float cropT = prefs.getFloat("rear_l1", 0.20f);
            float cropR = prefs.getFloat("rear_l2", 1.00f);
            float cropB = prefs.getFloat("rear_l3", 0.80f);
            float str = prefs.getFloat("rear_str_l", 0.0f);
            float zoom = prefs.getFloat("rear_zoom_l", 1.0f);
            float cx = prefs.getFloat("rear_cx_l", 0.50f);
            float cy = prefs.getFloat("rear_cy_l", 0.50f);
            float[] crop = new float[]{cropL, cropT, cropR, cropB};

            studioGlView.setDualMode(false, 4);
            studioGlView.setParamsRear(rot, fine, mir, fv, crop, str, zoom, cx, cy);
        } else {
            String[] wheelKeys = {"fl", "fr"};
            for (int i = 0; i < 2; i++) {
                String wKey = wheelKeys[i];
                float defRot = (i == 0) ? 270f : 90f;
                float rot = prefs.getFloat("quad_rot_" + wKey, defRot);
                float fine = prefs.getFloat("quad_fn_" + wKey, 0f);
                boolean mir = prefs.getBoolean("quad_mir_" + wKey, false);
                boolean fv = prefs.getBoolean("quad_fv_" + wKey, false);
                float c0 = prefs.getFloat("quad_crop_" + wKey + "0", 0.0f);
                float c1 = prefs.getFloat("quad_crop_" + wKey + "1", 0.0f);
                float c2 = prefs.getFloat("quad_crop_" + wKey + "2", 1.0f);
                float c3 = prefs.getFloat("quad_crop_" + wKey + "3", 0.55f);
                float str = prefs.getFloat("quad_str_" + wKey, -0.15f);
                float zoom = prefs.getFloat("quad_zoom_" + wKey, 1.25f);
                float cx = prefs.getFloat("quad_cx_" + wKey, 0.50f);
                float cy = prefs.getFloat("quad_cy_" + wKey, 0.35f);
                float[] crop = new float[]{c0, c1, c2, c3};

                studioGlView.setParamsQuadWheel(i, rot, fine, mir, fv, crop, str, zoom, cx, cy);
            }
            studioGlView.setDualMode(false, selectedWheelIdx == 0 ? 11 : 12);
        }
        studioGlView.requestRender();
    }

    // --- Direct Touch Gestures Callbacks ---
    @Override
    public void onPan(float deltaX, float deltaY) {
        if (activeMode == 1) {
            String prefix = isTurnRight ? "R" : "L";
            Perspective3DParams def = isTurnRight ? Perspective3DParams.createAtto3RightDefault() : Perspective3DParams.createAtto3LeftDefault();
            float curInOut = prefs.getFloat(prefix + "_panInOut", def.panInOut);
            float curY = prefs.getFloat(prefix + "_panY", def.panY);
            float nextInOut = Math.max(-1.00f, Math.min(1.00f, Math.round((curInOut - deltaX * 0.5f) * 1000f) / 1000f));
            float nextY = Math.max(-1.50f, Math.min(1.50f, Math.round((curY - deltaY * 0.5f) * 1000f) / 1000f));
            prefs.edit().putFloat(prefix + "_panInOut", nextInOut).putFloat(prefix + "_panY", nextY).apply();
            loadCurrentModeIntoControls();
            updateStudioGlParams();
            OverlayService.reapplyLive();
            return;
        }
        String keyCx = getKeyFeCx();
        String keyCy = getKeyFeCy();
        float defCy = (activeMode == 3) ? ((selectedWheelIdx < 2) ? 0.35f : 0.65f) : 0.50f;
        float curCx = prefs.getFloat(keyCx, 0.50f);
        float curCy = prefs.getFloat(keyCy, defCy);
        float nextCx = Math.max(0.05f, Math.min(0.95f, curCx - deltaX * 0.6f));
        float nextCy = Math.max(0.05f, Math.min(0.95f, curCy - deltaY * 0.6f));

        prefs.edit().putFloat(keyCx, nextCx).putFloat(keyCy, nextCy).apply();
        loadCurrentModeIntoControls();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    @Override
    public void onZoom(float scaleFactor) {
        if (activeMode == 1) {
            String prefix = isTurnRight ? "R" : "L";
            Perspective3DParams def = isTurnRight ? Perspective3DParams.createAtto3RightDefault() : Perspective3DParams.createAtto3LeftDefault();
            float curZoom = prefs.getFloat(prefix + "_zoom3D", def.zoom3D);
            float nextZoom = Math.max(0.50f, Math.min(3.50f, Math.round(curZoom * scaleFactor * 100f) / 100f));
            prefs.edit().putFloat(prefix + "_zoom3D", nextZoom).apply();
            loadCurrentModeIntoControls();
            updateStudioGlParams();
            OverlayService.reapplyLive();
            return;
        }
        String keyZoom = getKeyFeZoom();
        float defZoom = (activeMode == 3) ? 1.25f : 1.0f;
        float curZoom = prefs.getFloat(keyZoom, defZoom);
        float nextZoom = Math.max(0.50f, Math.min(2.50f, curZoom * scaleFactor));

        prefs.edit().putFloat(keyZoom, nextZoom).apply();
        loadCurrentModeIntoControls();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    @Override
    public void onRotate(float deltaDegrees) {
        if (activeMode == 1) {
            String prefix = isTurnRight ? "R" : "L";
            Perspective3DParams def = isTurnRight ? Perspective3DParams.createAtto3RightDefault() : Perspective3DParams.createAtto3LeftDefault();
            float curRoll = prefs.getFloat(prefix + "_rollDeg", def.rollDeg);
            float nextRoll = Math.max(-30.0f, Math.min(30.0f, Math.round((curRoll + deltaDegrees * 0.5f) * 10f) / 10f));
            prefs.edit().putFloat(prefix + "_rollDeg", nextRoll).apply();
            loadCurrentModeIntoControls();
            updateStudioGlParams();
            OverlayService.reapplyLive();
            return;
        }
        String keyFine = getKeyFine();
        float cur = prefs.getFloat(keyFine, 0f);
        float next = Math.max(-180f, Math.min(180f, cur + deltaDegrees * 0.5f));

        prefs.edit().putFloat(keyFine, next).apply();
        loadCurrentModeIntoControls();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    // --- Recommended Presets ---
    private void applyRecommendedPreset() {
        if (activeMode == 1) {
            OemRecommendedPreset.applyAtto3PerspectivePreset(prefs);
            loadCurrentModeIntoControls();
            updateStudioGlParams();
            OverlayService.reapplyLive();
            Toast.makeText(this, R.string.studio_toast_atto3_loaded, Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        if (activeMode == 1) {
            if (isTurnRight) {
                // Right Turn
                ed.putInt("dr_rot_r", 270)
                  .putFloat("dr_fn_r", 27.0f)
                  .putBoolean("dr_mir_r", true)
                  .putBoolean("dr_fv_r", false)
                  .putFloat("dr_r0", 0.21f)
                  .putFloat("dr_r1", 0.62f)
                  .putFloat("dr_r2", 0.75f)
                  .putFloat("dr_r3", 0.89f)
                  .putFloat("dr_str_r", -0.38f)
                  .putFloat("dr_zoom_r", 0.91f)
                  .putFloat("dr_cx_r", 0.66f)
                  .putFloat("dr_cy_r", 0.68f);
            } else {
                // Left Turn
                ed.putInt("dr_rot_l", 0)
                  .putFloat("dr_fn_l", 48.0f)
                  .putBoolean("dr_mir_l", true)
                  .putBoolean("dr_fv_l", false)
                  .putFloat("dr_l0", 0.22f)
                  .putFloat("dr_l1", 0.57f)
                  .putFloat("dr_l2", 0.81f)
                  .putFloat("dr_l3", 0.97f)
                  .putFloat("dr_str_l", -0.24f)
                  .putFloat("dr_zoom_l", 1.14f)
                  .putFloat("dr_cx_l", 0.40f)
                  .putFloat("dr_cy_l", 0.5601612f);
            }
            ed.putFloat("scale_dr", 0.9500799f)
              .putInt("x_dr", 1236)
              .putInt("y_dr", 0)
              .putInt("pip_corner_radius", 22);
        } else if (activeMode == 2) {
            ed.putInt("rear_rot_l", 0)
              .putFloat("rear_fn_l", 0.0f)
              .putBoolean("rear_mir_l", true)
              .putBoolean("rear_fv_l", false)
              .putFloat("rear_l0", 0.00f)
              .putFloat("rear_l1", 0.50f)
              .putFloat("rear_l2", 1.00f)
              .putFloat("rear_l3", 1.00f)
              .putFloat("rear_str_l", -0.02f)
              .putFloat("rear_zoom_l", 2.30f)
              .putFloat("rear_cx_l", 0.50845903f)
              .putFloat("rear_cy_l", 0.6826888f)
              .putFloat("scale_rear", 1.0796669f)
              .putInt("x_rear", 768)
              .putInt("y_rear", 22);
        } else {
            String wKey = getActiveTargetKey();
            if (selectedWheelIdx == 0) { // FL
                ed.putFloat("quad_rot_fl", 270.0f)
                  .putFloat("quad_fn_fl", 10.0f)
                  .putBoolean("quad_mir_fl", false)
                  .putBoolean("quad_fv_fl", false)
                  .putFloat("quad_crop_fl0", 0.0f)
                  .putFloat("quad_crop_fl1", 0.31f)
                  .putFloat("quad_crop_fl2", 1.0f)
                  .putFloat("quad_crop_fl3", 1.0f)
                  .putFloat("quad_str_fl", -0.37f)
                  .putFloat("quad_zoom_fl", 1.20f)
                  .putFloat("quad_cx_fl", 0.51f)
                  .putFloat("quad_cy_fl", 0.50f);
            } else if (selectedWheelIdx == 1) { // FR
                ed.putFloat("quad_rot_fr", 90.0f)
                  .putFloat("quad_fn_fr", -24.0f)
                  .putBoolean("quad_mir_fr", false)
                  .putBoolean("quad_fv_fr", false)
                  .putFloat("quad_crop_fr0", 0.0f)
                  .putFloat("quad_crop_fr1", 0.32f)
                  .putFloat("quad_crop_fr2", 1.0f)
                  .putFloat("quad_crop_fr3", 1.0f)
                  .putFloat("quad_str_fr", -0.35f)
                  .putFloat("quad_zoom_fr", 1.25f)
                  .putFloat("quad_cx_fr", 0.46f)
                  .putFloat("quad_cy_fr", 0.52f);
            } else if (selectedWheelIdx == 2) { // RL
                ed.putFloat("quad_rot_rl", 0.0f)
                  .putFloat("quad_fn_rl", 0.0f)
                  .putBoolean("quad_mir_rl", false)
                  .putBoolean("quad_fv_rl", false)
                  .putFloat("quad_crop_rl0", 0.0f)
                  .putFloat("quad_crop_rl1", 0.0f)
                  .putFloat("quad_crop_rl2", 1.0f)
                  .putFloat("quad_crop_rl3", 1.0f)
                  .putFloat("quad_str_rl", 0.0f)
                  .putFloat("quad_zoom_rl", 1.0f)
                  .putFloat("quad_cx_rl", 0.50f)
                  .putFloat("quad_cy_rl", 0.50f);
            } else { // RR
                ed.putFloat("quad_rot_rr", 0.0f)
                  .putFloat("quad_fn_rr", 0.0f)
                  .putBoolean("quad_mir_rr", false)
                  .putBoolean("quad_fv_rr", false)
                  .putFloat("quad_crop_rr0", 0.0f)
                  .putFloat("quad_crop_rr1", 0.0f)
                  .putFloat("quad_crop_rr2", 1.0f)
                  .putFloat("quad_crop_rr3", 1.0f)
                  .putFloat("quad_str_rr", 0.0f)
                  .putFloat("quad_zoom_rr", 1.0f)
                  .putFloat("quad_cx_rr", 0.50f)
                  .putFloat("quad_cy_rr", 0.50f);
            }
        }
        ed.apply();
        loadCurrentModeIntoControls();
        updateStudioGlParams();
        OverlayService.reapplyLive();
        Toast.makeText(this, tr("โหลดค่าแนะนำสำหรับ " + getTargetLabel() + " เรียบร้อย",
            "Loaded recommended presets for " + getTargetLabel()), Toast.LENGTH_SHORT).show();
    }

    private void resetCurrentToDefaults() {
        if (activeMode == 1) {
            OemRecommendedPreset.applyAtto3PerspectivePreset(prefs);
            loadCurrentModeIntoControls();
            updateStudioGlParams();
            OverlayService.reapplyLive();
            Toast.makeText(this, tr("รีเซ็ตค่า 3D และมุมกล้องกลับเป็นค่าแนะนำสำหรับ BYD ATTO 3 เรียบร้อย",
                "Reset 3D and camera calibration to BYD ATTO 3 defaults"), Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        if (activeMode == 1) {
            String side = isTurnRight ? "r" : "l";
            ed.putInt("dr_rot_" + side, isTurnRight ? 270 : 0)
              .putFloat("dr_fn_" + side, 0.0f)
              .putBoolean("dr_mir_" + side, false)
              .putBoolean("dr_fv_" + side, false)
              .putFloat("dr_" + side + "0", 0.0f)
              .putFloat("dr_" + side + "1", 0.0f)
              .putFloat("dr_" + side + "2", 1.0f)
              .putFloat("dr_" + side + "3", 1.0f)
              .putFloat("dr_str_" + side, 0.0f)
              .putFloat("dr_zoom_" + side, 1.0f)
              .putFloat("dr_cx_" + side, 0.5f)
              .putFloat("dr_cy_" + side, 0.5f);
        } else if (activeMode == 2) {
            ed.putInt("rear_rot_l", 0)
              .putFloat("rear_fn_l", 0.0f)
              .putBoolean("rear_mir_l", true)
              .putBoolean("rear_fv_l", false)
              .putFloat("rear_l0", 0.0f)
              .putFloat("rear_l1", 0.0f)
              .putFloat("rear_l2", 1.0f)
              .putFloat("rear_l3", 1.0f)
              .putFloat("rear_str_l", 0.0f)
              .putFloat("rear_zoom_l", 1.0f)
              .putFloat("rear_cx_l", 0.50f)
              .putFloat("rear_cy_l", 0.50f);
        } else {
            String wKey = getActiveTargetKey();
            ed.putFloat("quad_rot_" + wKey, 0f)
              .putFloat("quad_fn_" + wKey, 0.0f)
              .putBoolean("quad_mir_" + wKey, false)
              .putBoolean("quad_fv_" + wKey, false)
              .putFloat("quad_crop_" + wKey + "0", 0.0f)
              .putFloat("quad_crop_" + wKey + "1", 0.0f)
              .putFloat("quad_crop_" + wKey + "2", 1.0f)
              .putFloat("quad_crop_" + wKey + "3", 1.0f)
              .putFloat("quad_str_" + wKey, 0.0f)
              .putFloat("quad_zoom_" + wKey, 1.0f)
              .putFloat("quad_cx_" + wKey, 0.5f)
              .putFloat("quad_cy_" + wKey, 0.5f);
        }
        ed.apply();
        loadCurrentModeIntoControls();
        updateStudioGlParams();
        OverlayService.reapplyLive();
        Toast.makeText(this, tr("รีเซ็ตค่ากลับสู่ค่ากลางแล้ว", "Reset to default values"), Toast.LENGTH_SHORT).show();
    }

    private void cycleRotate90() {
        int cur = getStoredRotation();
        int next = (cur + 90) % 360;
        saveRotation(next);
        loadCurrentModeIntoControls();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void nudgeFine(float delta) {
        float cur = prefs.getFloat(getKeyFine(), 0f);
        float next = Math.max(-180f, Math.min(180f, Math.round((cur + delta) * 10f) / 10f));
        prefs.edit().putFloat(getKeyFine(), next).apply();
        loadCurrentModeIntoControls();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void saveMirror(boolean isChecked) {
        prefs.edit().putBoolean(getKeyMir(), isChecked).apply();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void saveFlipV(boolean isChecked) {
        prefs.edit().putBoolean(getKeyFv(), isChecked).apply();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void setupCropListeners() {
        sbStudioCropT.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && !suppressSync) {
                    saveCropPart(1, p / 100.0f);
                    tvStudioCropT.setText(String.format(Locale.US, "%.2f", p / 100.0f));
                    updatePreviewHud();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            }
        });
        sbStudioCropB.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && !suppressSync) {
                    saveCropPart(3, p / 100.0f);
                    tvStudioCropB.setText(String.format(Locale.US, "%.2f", p / 100.0f));
                    updatePreviewHud();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            }
        });
        sbStudioCropL.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && !suppressSync) {
                    saveCropPart(0, p / 100.0f);
                    tvStudioCropL.setText(String.format(Locale.US, "%.2f", p / 100.0f));
                    updatePreviewHud();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            }
        });
        sbStudioCropR.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && !suppressSync) {
                    saveCropPart(2, p / 100.0f);
                    tvStudioCropR.setText(String.format(Locale.US, "%.2f", p / 100.0f));
                    updatePreviewHud();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            }
        });

        btnStudioCropTMinus.setOnClickListener(v -> nudgeCrop(1, -0.01f));
        btnStudioCropTPlus.setOnClickListener(v -> nudgeCrop(1, 0.01f));
        btnStudioCropBMinus.setOnClickListener(v -> nudgeCrop(3, -0.01f));
        btnStudioCropBPlus.setOnClickListener(v -> nudgeCrop(3, 0.01f));
        btnStudioCropLMinus.setOnClickListener(v -> nudgeCrop(0, -0.01f));
        btnStudioCropLPlus.setOnClickListener(v -> nudgeCrop(0, 0.01f));
        btnStudioCropRMinus.setOnClickListener(v -> nudgeCrop(2, -0.01f));
        btnStudioCropRPlus.setOnClickListener(v -> nudgeCrop(2, 0.01f));
    }

    private void saveCropPart(int partIdx, float val) {
        prefs.edit().putFloat(getKeyCrop(partIdx), val).apply();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void nudgeCrop(int partIdx, float delta) {
        SeekBar[] sbs = {sbStudioCropL, sbStudioCropT, sbStudioCropR, sbStudioCropB};
        SeekBar target = sbs[partIdx];
        if (target != null) {
            int cur = target.getProgress();
            int next = Math.max(0, Math.min(100, cur + (int) (delta * 100)));
            target.setProgress(next);
            saveCropPart(partIdx, next / 100.0f);
            loadCurrentModeIntoControls();
            updateStudioGlParams();
            OverlayService.reapplyLive();
        }
    }

    private void setupFisheyeListeners() {
        sbStudioFeStr.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && !suppressSync) {
                    float v = (p / 100.0f) - 0.50f;
                    saveFe("str", v);
                    tvStudioFeStr.setText(String.format(Locale.US, "%.2f", v));
                    updatePreviewHud();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            }
        });
        sbStudioFeZoom.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && !suppressSync) {
                    float v = (p / 100.0f) + 0.50f;
                    saveFe("zoom", v);
                    tvStudioFeZoom.setText(String.format(Locale.US, "%.2fx", v));
                    updatePreviewHud();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            }
        });
        sbStudioFeCx.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && !suppressSync) {
                    float v = (p / 100.0f) + 0.10f;
                    saveFe("cx", v);
                    tvStudioFeCx.setText(String.format(Locale.US, "%.2f", v));
                    updatePreviewHud();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            }
        });
        sbStudioFeCy.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && !suppressSync) {
                    float v = (p / 100.0f) + 0.10f;
                    saveFe("cy", v);
                    tvStudioFeCy.setText(String.format(Locale.US, "%.2f", v));
                    updatePreviewHud();
                    updateStudioGlParams();
                    OverlayService.reapplyLive();
                }
            }
        });

        btnStudioFeStrMinus.setOnClickListener(v -> nudgeFe("str", -0.02f));
        btnStudioFeStrPlus.setOnClickListener(v -> nudgeFe("str", 0.02f));
        btnStudioFeZoomMinus.setOnClickListener(v -> nudgeFe("zoom", -0.05f));
        btnStudioFeZoomPlus.setOnClickListener(v -> nudgeFe("zoom", 0.05f));
        btnStudioFeCxMinus.setOnClickListener(v -> nudgeFe("cx", -0.02f));
        btnStudioFeCxPlus.setOnClickListener(v -> nudgeFe("cx", 0.02f));
        btnStudioFeCyMinus.setOnClickListener(v -> nudgeFe("cy", -0.02f));
        btnStudioFeCyPlus.setOnClickListener(v -> nudgeFe("cy", 0.02f));
    }

    private void saveFe(String tag, float val) {
        String key;
        if (tag.equals("str")) key = getKeyFeStr();
        else if (tag.equals("zoom")) key = getKeyFeZoom();
        else if (tag.equals("cx")) key = getKeyFeCx();
        else key = getKeyFeCy();

        prefs.edit().putFloat(key, val).apply();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void nudgeFe(String tag, float delta) {
        float cur;
        if (tag.equals("str")) {
            cur = prefs.getFloat(getKeyFeStr(), (activeMode == 3) ? -0.15f : 0.0f);
        } else if (tag.equals("zoom")) {
            cur = prefs.getFloat(getKeyFeZoom(), (activeMode == 3) ? 1.25f : 1.0f);
        } else if (tag.equals("cx")) {
            cur = prefs.getFloat(getKeyFeCx(), 0.50f);
        } else {
            float defCy = (activeMode == 3) ? ((selectedWheelIdx < 2) ? 0.35f : 0.65f) : 0.50f;
            cur = prefs.getFloat(getKeyFeCy(), defCy);
        }
        float next = Math.round((cur + delta) * 100.0f) / 100.0f;
        saveFe(tag, next);
        loadCurrentModeIntoControls();
        updateStudioGlParams();
        OverlayService.reapplyLive();
    }

    private void triggerLivePipTest() {
        OverlayService overlay = OverlayService.getInstance();
        if (overlay != null && overlay.isShowing()) {
            OverlayService.allowStudioPipTest = false;
            overlay.hide();
            return;
        }
        OverlayService.allowStudioPipTest = true;
        if (activeMode == 3 && overlay != null) {
            overlay.setSteeringWheelSide(selectedWheelIdx == 1 ? 2 : 1);
        }
        Intent intent = new Intent(this, OverlayService.class);
        if (activeMode == 1) {
            intent.setAction(isTurnRight ? "SHOW_RIGHT" : "SHOW_LEFT");
        } else if (activeMode == 2) {
            intent.setAction("SHOW_REAR");
        } else {
            intent.setAction(selectedWheelIdx == 1 ? "SHOW_WHEEL_RIGHT" : "SHOW_WHEEL_LEFT");
        }
        startService(intent);
    }

    private static abstract class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
