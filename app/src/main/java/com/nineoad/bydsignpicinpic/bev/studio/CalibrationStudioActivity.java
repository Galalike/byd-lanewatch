package com.nineoad.bydsignpicinpic.bev.studio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.nineoad.bydsignpicinpic.R;
import com.nineoad.bydsignpicinpic.bev.adapter.CameraAdapterFactory;
import com.nineoad.bydsignpicinpic.bev.adapter.CameraFrame;
import com.nineoad.bydsignpicinpic.bev.adapter.ICameraInputAdapter;
import com.nineoad.bydsignpicinpic.bev.adapter.MockCameraFrameAdapter;
import com.nineoad.bydsignpicinpic.bev.community.ProfileSharingService;
import com.nineoad.bydsignpicinpic.bev.compat.CompatibilityChecker;
import com.nineoad.bydsignpicinpic.bev.compat.CompatibilityLevel;
import com.nineoad.bydsignpicinpic.bev.engine.UniversalCameraProcessingEngine;
import com.nineoad.bydsignpicinpic.bev.profile.CameraCalibration;
import com.nineoad.bydsignpicinpic.bev.profile.CameraRole;
import com.nineoad.bydsignpicinpic.bev.profile.Point2D;
import com.nineoad.bydsignpicinpic.bev.profile.ProfileStatus;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfileManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalibrationStudioActivity extends AppCompatActivity implements CalibrationStudioView.OnCalibrationListener {

    private VehicleProfileManager profileManager;
    private VehicleProfile activeProfile;
    private UniversalCameraProcessingEngine engine;
    private ICameraInputAdapter cameraAdapter;
    private boolean forceMockAdapter = false;

    // Views
    private CalibrationStudioView studioView;
    private Spinner spinnerProfile;
    private TextView tvProfileStatus;
    private TextView tvCompatStatus;
    private Button btnStudioBack;
    private Button btnTabFront;
    private Button btnTabRear;
    private Button btnTabLeft;
    private Button btnTabRight;
    private Button btnTabBev360;
    private Button btnToggleInputSource;

    private ScrollView dockScrollView;
    private Button btnSelectP0, btnSelectP1, btnSelectP2, btnSelectP3;
    private TextView tvActiveHandleCoord;
    private TextView tvValHandleX, tvValHandleY;
    private Button btnHandleXMinus, btnHandleXPlus;
    private Button btnHandleYMinus, btnHandleYPlus;
    private Button btnStepFine, btnStepNorm, btnStepCoarse;
    private float currentStepSize = 0.002f;

    private TextView tvValRotation;
    private Button btnRotMinus, btnRotPlus;
    private TextView tvValScale;
    private Button btnScaleMinus, btnScalePlus;
    private TextView tvValDistortion;
    private Button btnDistMinus, btnDistPlus;
    private TextView tvVehicleDimensions;

    private Button btnUndo, btnRedo, btnResetCamera;
    private Button btnToggleGrid, btnBeforeAfter;
    private Button btnExportJson, btnImportJson, btnDuplicateProfile, btnSaveProfile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen awake and full landscape for vehicle studio
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        setContentView(R.layout.activity_calibration_studio);

        profileManager = VehicleProfileManager.getInstance(this);
        activeProfile = profileManager.getActiveProfile();
        engine = new UniversalCameraProcessingEngine(this, activeProfile);

        initViews();
        setupHardwareCompatibility();
        setupProfileSpinner();
        setupCameraTabs();
        setupPrecisionControls();
        setupBottomToolbar();
        startCameraStream();

        updateActiveRoleUI(CameraRole.FRONT);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void initViews() {
        studioView = findViewById(R.id.studioView);
        studioView.setEngine(engine);
        studioView.setProfile(activeProfile);
        studioView.setOnCalibrationListener(this);

        spinnerProfile = findViewById(R.id.spinnerProfile);
        tvProfileStatus = findViewById(R.id.tvProfileStatus);
        tvCompatStatus = findViewById(R.id.tvCompatStatus);
        btnStudioBack = findViewById(R.id.btnStudioBack);
        btnStudioBack.setOnClickListener(v -> finish());

        btnTabFront = findViewById(R.id.btnTabFront);
        btnTabRear = findViewById(R.id.btnTabRear);
        btnTabLeft = findViewById(R.id.btnTabLeft);
        btnTabRight = findViewById(R.id.btnTabRight);
        btnTabBev360 = findViewById(R.id.btnTabBev360);
        btnToggleInputSource = findViewById(R.id.btnToggleInputSource);

        dockScrollView = findViewById(R.id.dockScrollView);
        btnSelectP0 = findViewById(R.id.btnSelectP0);
        btnSelectP1 = findViewById(R.id.btnSelectP1);
        btnSelectP2 = findViewById(R.id.btnSelectP2);
        btnSelectP3 = findViewById(R.id.btnSelectP3);
        tvActiveHandleCoord = findViewById(R.id.tvActiveHandleCoord);
        tvValHandleX = findViewById(R.id.tvValHandleX);
        tvValHandleY = findViewById(R.id.tvValHandleY);
        btnHandleXMinus = findViewById(R.id.btnHandleXMinus);
        btnHandleXPlus = findViewById(R.id.btnHandleXPlus);
        btnHandleYMinus = findViewById(R.id.btnHandleYMinus);
        btnHandleYPlus = findViewById(R.id.btnHandleYPlus);

        btnStepFine = findViewById(R.id.btnStepFine);
        btnStepNorm = findViewById(R.id.btnStepNorm);
        btnStepCoarse = findViewById(R.id.btnStepCoarse);

        tvValRotation = findViewById(R.id.tvValRotation);
        btnRotMinus = findViewById(R.id.btnRotMinus);
        btnRotPlus = findViewById(R.id.btnRotPlus);
        tvValScale = findViewById(R.id.tvValScale);
        btnScaleMinus = findViewById(R.id.btnScaleMinus);
        btnScalePlus = findViewById(R.id.btnScalePlus);
        tvValDistortion = findViewById(R.id.tvValDistortion);
        btnDistMinus = findViewById(R.id.btnDistMinus);
        btnDistPlus = findViewById(R.id.btnDistPlus);
        tvVehicleDimensions = findViewById(R.id.tvVehicleDimensions);

        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        btnResetCamera = findViewById(R.id.btnResetCamera);
        btnToggleGrid = findViewById(R.id.btnToggleGrid);
        btnBeforeAfter = findViewById(R.id.btnBeforeAfter);
        btnExportJson = findViewById(R.id.btnExportJson);
        btnImportJson = findViewById(R.id.btnImportJson);
        btnDuplicateProfile = findViewById(R.id.btnDuplicateProfile);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
    }

    private void setupHardwareCompatibility() {
        CompatibilityChecker.CompatibilityReport report = CompatibilityChecker.checkCompatibility(this, activeProfile);
        tvCompatStatus.setText(report.level.label.toUpperCase());
        tvCompatStatus.setBackgroundColor(report.level.color);
    }

    private void setupProfileSpinner() {
        List<VehicleProfile> profiles = profileManager.getAllProfiles();
        List<String> labels = new ArrayList<>();
        int selectedIndex = 0;

        for (int i = 0; i < profiles.size(); i++) {
            VehicleProfile p = profiles.get(i);
            labels.add(p.manufacturer + " " + p.model + " (" + p.id + ")");
            if (activeProfile != null && activeProfile.id.equals(p.id)) {
                selectedIndex = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinnerProfile.setAdapter(adapter);
        spinnerProfile.setSelection(selectedIndex);

        spinnerProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                VehicleProfile selected = profiles.get(position);
                if (activeProfile == null || !activeProfile.id.equals(selected.id)) {
                    activeProfile = selected;
                    profileManager.setActiveProfile(selected.id);
                    engine.updateProfile(activeProfile);
                    studioView.setProfile(activeProfile);
                    updateProfileStatusBadge();
                    updateVehicleSpecsReadout();
                    restartCameraStream();
                    updateNumericReadouts();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateProfileStatusBadge();
        updateVehicleSpecsReadout();
    }

    private void updateProfileStatusBadge() {
        if (activeProfile == null) return;
        ProfileStatus status = activeProfile.status;
        tvProfileStatus.setText(status.label.toUpperCase());
        switch (status) {
            case VERIFIED:
                tvProfileStatus.setTextColor(Color.rgb(0, 230, 118));
                tvProfileStatus.setBackgroundColor(Color.argb(50, 0, 230, 118));
                break;
            case COMMUNITY_TESTED:
                tvProfileStatus.setTextColor(Color.rgb(255, 179, 0));
                tvProfileStatus.setBackgroundColor(Color.argb(50, 255, 179, 0));
                break;
            case EXPERIMENTAL:
            default:
                tvProfileStatus.setTextColor(Color.rgb(0, 229, 255));
                tvProfileStatus.setBackgroundColor(Color.argb(50, 0, 229, 255));
                break;
        }
    }

    private void updateVehicleSpecsReadout() {
        if (activeProfile == null) return;
        String spec = String.format(Locale.US, "L: %dmm | W: %dmm | Wheelbase: %dmm",
                activeProfile.dimensions.lengthMm,
                activeProfile.dimensions.widthMm,
                activeProfile.dimensions.wheelbaseMm);
        tvVehicleDimensions.setText(spec);
    }

    private void setupCameraTabs() {
        btnTabFront.setOnClickListener(v -> updateActiveRoleUI(CameraRole.FRONT));
        btnTabRear.setOnClickListener(v -> updateActiveRoleUI(CameraRole.REAR));
        btnTabLeft.setOnClickListener(v -> updateActiveRoleUI(CameraRole.LEFT));
        btnTabRight.setOnClickListener(v -> updateActiveRoleUI(CameraRole.RIGHT));
        btnTabBev360.setOnClickListener(v -> updateActiveRoleUI(null));

        btnToggleInputSource.setOnClickListener(v -> {
            forceMockAdapter = !forceMockAdapter;
            btnToggleInputSource.setText(forceMockAdapter ? "SRC: MOCK STREAM" : "SRC: AUTO / BYD HW");
            btnToggleInputSource.setTextColor(forceMockAdapter ? Color.rgb(255, 179, 0) : Color.rgb(56, 189, 248));
            restartCameraStream();
            Toast.makeText(this, "Camera Source: " + (forceMockAdapter ? "Mock Generator" : "Hardware Adapter"), Toast.LENGTH_SHORT).show();
        });
    }

    private void updateActiveRoleUI(@Nullable CameraRole role) {
        studioView.setActiveRole(role);

        // Highlight selected tab
        int activeBg = Color.rgb(44, 59, 78);
        int inactiveBg = Color.TRANSPARENT;
        int activeText = Color.rgb(0, 229, 255);
        int inactiveText = Color.rgb(160, 179, 198);

        btnTabFront.setBackgroundColor(role == CameraRole.FRONT ? activeBg : inactiveBg);
        btnTabFront.setTextColor(role == CameraRole.FRONT ? activeText : inactiveText);

        btnTabRear.setBackgroundColor(role == CameraRole.REAR ? activeBg : inactiveBg);
        btnTabRear.setTextColor(role == CameraRole.REAR ? activeText : inactiveText);

        btnTabLeft.setBackgroundColor(role == CameraRole.LEFT ? activeBg : inactiveBg);
        btnTabLeft.setTextColor(role == CameraRole.LEFT ? activeText : inactiveText);

        btnTabRight.setBackgroundColor(role == CameraRole.RIGHT ? activeBg : inactiveBg);
        btnTabRight.setTextColor(role == CameraRole.RIGHT ? activeText : inactiveText);

        btnTabBev360.setBackgroundColor(role == null ? activeBg : inactiveBg);
        btnTabBev360.setTextColor(role == null ? Color.rgb(255, 179, 0) : inactiveText);

        // Hide numeric dock when in full 360 preview mode
        dockScrollView.setVisibility(role == null ? View.GONE : View.VISIBLE);

        updateHandleSelectionUI(0);
        updateNumericReadouts();
    }

    private void setupPrecisionControls() {
        // Handle selector buttons
        btnSelectP0.setOnClickListener(v -> updateHandleSelectionUI(0));
        btnSelectP1.setOnClickListener(v -> updateHandleSelectionUI(1));
        btnSelectP2.setOnClickListener(v -> updateHandleSelectionUI(2));
        btnSelectP3.setOnClickListener(v -> updateHandleSelectionUI(3));

        // Step size selection
        btnStepFine.setOnClickListener(v -> setStepSize(0.002f, btnStepFine));
        btnStepNorm.setOnClickListener(v -> setStepSize(0.010f, btnStepNorm));
        btnStepCoarse.setOnClickListener(v -> setStepSize(0.050f, btnStepCoarse));

        // X/Y Steppers
        btnHandleXMinus.setOnClickListener(v -> studioView.adjustSelectedHandle(-currentStepSize, 0f));
        btnHandleXPlus.setOnClickListener(v -> studioView.adjustSelectedHandle(currentStepSize, 0f));
        btnHandleYMinus.setOnClickListener(v -> studioView.adjustSelectedHandle(0f, -currentStepSize));
        btnHandleYPlus.setOnClickListener(v -> studioView.adjustSelectedHandle(0f, currentStepSize));

        // Fine Rotation (+/- 0.5 deg)
        btnRotMinus.setOnClickListener(v -> studioView.adjustRotation(-0.5f));
        btnRotPlus.setOnClickListener(v -> studioView.adjustRotation(0.5f));

        // Scale (+/- 0.01)
        btnScaleMinus.setOnClickListener(v -> studioView.adjustScale(-0.01f));
        btnScalePlus.setOnClickListener(v -> studioView.adjustScale(0.01f));

        // Fisheye Distortion k1 (+/- 0.01)
        btnDistMinus.setOnClickListener(v -> studioView.adjustDistortion(-0.01f));
        btnDistPlus.setOnClickListener(v -> studioView.adjustDistortion(0.01f));
    }

    private void setStepSize(float step, Button activeBtn) {
        this.currentStepSize = step;
        int activeBg = Color.rgb(44, 59, 78);
        int inactiveBg = Color.rgb(31, 40, 55);

        btnStepFine.setBackgroundColor(btnStepFine == activeBtn ? activeBg : inactiveBg);
        btnStepNorm.setBackgroundColor(btnStepNorm == activeBtn ? activeBg : inactiveBg);
        btnStepCoarse.setBackgroundColor(btnStepCoarse == activeBtn ? activeBg : inactiveBg);

        btnStepFine.setTextColor(btnStepFine == activeBtn ? Color.rgb(0, 229, 255) : Color.rgb(160, 179, 198));
        btnStepNorm.setTextColor(btnStepNorm == activeBtn ? Color.rgb(0, 229, 255) : Color.rgb(160, 179, 198));
        btnStepCoarse.setTextColor(btnStepCoarse == activeBtn ? Color.rgb(0, 229, 255) : Color.rgb(160, 179, 198));
    }

    private void updateHandleSelectionUI(int index) {
        if (studioView.getSelectedHandleIndex() != index) {
            studioView.setSelectedHandleIndex(index, false);
        }
        int activeBg = Color.rgb(42, 56, 77);
        int inactiveBg = Color.rgb(31, 40, 55);

        btnSelectP0.setBackgroundColor(index == 0 ? activeBg : inactiveBg);
        btnSelectP1.setBackgroundColor(index == 1 ? activeBg : inactiveBg);
        btnSelectP2.setBackgroundColor(index == 2 ? activeBg : inactiveBg);
        btnSelectP3.setBackgroundColor(index == 3 ? activeBg : inactiveBg);

        btnSelectP0.setTextColor(index == 0 ? Color.rgb(0, 229, 255) : Color.rgb(160, 179, 198));
        btnSelectP1.setTextColor(index == 1 ? Color.rgb(0, 229, 255) : Color.rgb(160, 179, 198));
        btnSelectP2.setTextColor(index == 2 ? Color.rgb(0, 229, 255) : Color.rgb(160, 179, 198));
        btnSelectP3.setTextColor(index == 3 ? Color.rgb(0, 229, 255) : Color.rgb(160, 179, 198));

        updateNumericReadouts();
    }

    private void updateNumericReadouts() {
        CameraRole role = studioView.getActiveRole();
        if (role == null || activeProfile == null) return;

        CameraCalibration cal = activeProfile.getCamera(role);
        int handleIdx = studioView.getSelectedHandleIndex();
        Point2D pt;
        switch (handleIdx) {
            case 0: pt = cal.p0; break;
            case 1: pt = cal.p1; break;
            case 2: pt = cal.p2; break;
            case 3: pt = cal.p3; break;
            default: pt = cal.p0; break;
        }

        tvActiveHandleCoord.setText(String.format(Locale.US, "P%d Handle: X=%.3f, Y=%.3f", handleIdx, pt.x, pt.y));
        tvValHandleX.setText(String.format(Locale.US, "%.3f", pt.x));
        tvValHandleY.setText(String.format(Locale.US, "%.3f", pt.y));

        tvValRotation.setText(String.format(Locale.US, "%.1f°", cal.fineRotationDeg));
        tvValScale.setText(String.format(Locale.US, "%.3f", cal.scaleX));
        tvValDistortion.setText(String.format(Locale.US, "%.3f", cal.distortionK1));
    }

    private void setupBottomToolbar() {
        btnUndo.setEnabled(false);
        btnRedo.setEnabled(false);
        btnUndo.setOnClickListener(v -> studioView.undo());
        btnRedo.setOnClickListener(v -> studioView.redo());

        btnResetCamera.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Reset Camera Calibration")
                    .setMessage("Reset current camera perspective handles to factory default?")
                    .setPositiveButton("Reset", (dialog, which) -> {
                        studioView.resetCurrentCamera();
                        updateNumericReadouts();
                        Toast.makeText(this, "Camera Calibration Reset", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnToggleGrid.setOnClickListener(v -> {
            boolean current = studioView.isShowGrid();
            studioView.setShowGrid(!current);
            btnToggleGrid.setText(!current ? "GRID: ON" : "GRID: OFF");
            btnToggleGrid.setTextColor(!current ? Color.rgb(0, 229, 255) : Color.rgb(160, 179, 198));
        });

        btnBeforeAfter.setOnClickListener(v -> {
            boolean current = studioView.isBeforeAfterMode();
            studioView.setBeforeAfterMode(!current);
            btnBeforeAfter.setText(!current ? "VIEWING: RAW" : "VIEWING: WARP");
            btnBeforeAfter.setTextColor(!current ? Color.rgb(255, 82, 82) : Color.rgb(255, 179, 0));
        });

        btnExportJson.setOnClickListener(v -> showExportDialog());
        btnImportJson.setOnClickListener(v -> showImportDialog());
        btnDuplicateProfile.setOnClickListener(v -> showDuplicateDialog());

        btnSaveProfile.setOnClickListener(v -> {
            if (activeProfile != null) {
                boolean ok = profileManager.saveProfile(activeProfile);
                if (ok) {
                    Toast.makeText(this, "Saved Profile: " + activeProfile.model, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Error Saving Profile", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showExportDialog() {
        if (activeProfile == null) return;
        String jsonStr;
        try {
            jsonStr = ProfileSharingService.exportProfileToJson(activeProfile);
        } catch (Throwable t) {
            try {
                jsonStr = activeProfile.toJsonString(true);
            } catch (Throwable ignored) {
                jsonStr = "{}";
            }
        }
        final String finalJson = jsonStr;

        new AlertDialog.Builder(this)
                .setTitle("Export Profile JSON: " + activeProfile.model)
                .setMessage(finalJson)
                .setPositiveButton("Copy to Clipboard", (dialog, which) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Vehicle Profile", finalJson));
                    Toast.makeText(this, "Profile JSON Copied to Clipboard", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Share", (dialog, which) -> {
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, finalJson);
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, "Share Vehicle Profile"));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showImportDialog() {
        EditText input = new EditText(this);
        input.setHint("Paste Vehicle Profile JSON here...");
        input.setLines(8);
        input.setTextColor(Color.WHITE);
        input.setBackgroundColor(Color.rgb(20, 26, 36));

        new AlertDialog.Builder(this)
                .setTitle("Import Vehicle Profile")
                .setView(input)
                .setPositiveButton("Import", (dialog, which) -> {
                    String json = input.getText().toString().trim();
                    try {
                        VehicleProfile imported = ProfileSharingService.importProfile(this, json);
                        if (imported != null) {
                            activeProfile = imported;
                            profileManager.setActiveProfile(imported.id);
                            setupProfileSpinner();
                            Toast.makeText(this, "Imported: " + imported.manufacturer + " " + imported.model, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable t) {
                        Toast.makeText(this, "Invalid Profile JSON: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Paste Clipboard", (dialog, which) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                        CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
                        input.setText(clip);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDuplicateDialog() {
        if (activeProfile == null) return;
        EditText input = new EditText(this);
        input.setText(activeProfile.model + " (Custom)");
        input.setTextColor(Color.WHITE);

        new AlertDialog.Builder(this)
                .setTitle("Duplicate Profile")
                .setView(input)
                .setPositiveButton("Duplicate", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    VehicleProfile clone = ProfileSharingService.duplicateProfile(this, activeProfile, newName, "Community");
                    activeProfile = clone;
                    profileManager.setActiveProfile(clone.id);
                    setupProfileSpinner();
                    Toast.makeText(this, "Created Profile: " + newName, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startCameraStream() {
        if (forceMockAdapter) {
            cameraAdapter = new MockCameraFrameAdapter();
            cameraAdapter.initialize(this, activeProfile);
        } else {
            cameraAdapter = CameraAdapterFactory.createAdapter(this, activeProfile);
        }

        cameraAdapter.setOnFrameAvailableListener((role, frame) -> {
            runOnUiThread(() -> studioView.setCameraFrame(role, frame));
        });
        cameraAdapter.start();
    }

    private void restartCameraStream() {
        if (cameraAdapter != null) {
            cameraAdapter.stop();
        }
        startCameraStream();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (cameraAdapter != null) {
            cameraAdapter.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraAdapter != null) {
            cameraAdapter.stop();
        }
    }

    // Callbacks from StudioView
    @Override
    public void onCalibrationChanged(CameraRole role, CameraCalibration calibration) {
        updateNumericReadouts();
    }

    @Override
    public void onHandleSelected(int handleIndex, Point2D point) {
        updateHandleSelectionUI(handleIndex);
    }

    @Override
    public void onSectorSelected(CameraRole role) {
        updateActiveRoleUI(role);
    }

    @Override
    public void onHistoryStateChanged(boolean canUndo, boolean canRedo) {
        btnUndo.setEnabled(canUndo);
        btnUndo.setTextColor(canUndo ? Color.WHITE : Color.rgb(100, 116, 139));
        btnRedo.setEnabled(canRedo);
        btnRedo.setTextColor(canRedo ? Color.WHITE : Color.rgb(100, 116, 139));
    }
}
