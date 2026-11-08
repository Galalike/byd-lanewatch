package com.nineoad.bydsignpicinpic.overlay;

import android.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.nineoad.avc.AvcClient;
import com.nineoad.bydsignpicinpic.camera.CameraFeed;
import com.nineoad.bydsignpicinpic.signal.BevAutomationManager;
import com.nineoad.bydsignpicinpic.signal.TurnSignalListener;

/* JADX INFO: loaded from: classes.dex */
public class OverlayService extends Service implements TextureView.SurfaceTextureListener {
    public static final String ACTION_HIDE = "HIDE";
    public static final String ACTION_REFRESH = "REFRESH";
    public static final String ACTION_SHOW_HAZARD = "SHOW_HAZARD";
    public static final String ACTION_SHOW_LEFT = "SHOW_LEFT";
    public static final String ACTION_SHOW_RIGHT = "SHOW_RIGHT";
    public static final String ACTION_SHOW_REAR = "SHOW_REAR";
    public static final String ACTION_HIDE_REAR = "HIDE_REAR";
    public static final String ACTION_TOGGLE_REAR = "TOGGLE_REAR";
    public static final String ACTION_SHOW_BEV = "SHOW_BEV";
    public static final String ACTION_HIDE_BEV = "HIDE_BEV";
    public static final String ACTION_TOGGLE_BEV = "TOGGLE_BEV";
    public static volatile boolean isRearviewActive = false;
    public static volatile boolean isBevActive = false;
    public static final int MODE_REARVIEW = 4;
    public static final int MODE_BEV = 5;
    public static volatile int rearIndex = 1;
    public static volatile int rearAspect = 0; // 0 = 3:1, 1 = 4:1
    public static volatile int pipCornerRadiusDp = 16;
    private static final float BAL_FE_CX_L = 0.46f;
    private static final float BAL_FE_CX_R = 0.53f;
    private static final float BAL_FE_CY_L = 0.55f;
    private static final float BAL_FE_CY_R = 0.56f;
    private static final float BAL_FE_STR_L = -0.26f;
    private static final float BAL_FE_STR_R = -0.28f;
    private static final float BAL_FE_ZOOM_L = 0.96f;
    private static final float BAL_FE_ZOOM_R = 0.88f;
    private static final float BAL_FINE_L = -41.0f;
    private static final float BAL_FINE_R = 19.0f;
    private static final boolean BAL_FLIPV = true;
    private static final int BAL_ROT_L = 270;
    private static final int BAL_ROT_R = 90;
    private static final String CH = "signalpnp";
    private static final int HAZARD_BOTTOM_WEIGHT = 55;
    private static final int HAZARD_FRONT_WEIGHT = 45;
    private static final float MAX_SCALE = 2.0f;
    private static final float MIN_SCALE = 0.5f;
    public static final int MODE_BALANCE = 2;
    public static final int MODE_CUSTOM = 3;
    public static final int MODE_DRIVING = 1;
    public static final int MODE_SAFETY = 0;
    private static final int NOTIF = 1;
    public static final int ORIENT_COUNT = 4;
    public static final int ORIENT_LAND = 0;
    public static final int ORIENT_PORTRAIT = 1;
    public static final int ORIENT_SQUARE_L = 2;
    public static final int ORIENT_SQUARE_S = 3;
    private static final int PANELS = 4;
    private static final float PRESET_FE_CX_L = 0.46f;
    private static final float PRESET_FE_CX_R = 0.53f;
    private static final float PRESET_FE_CY_L = 0.55f;
    private static final float PRESET_FE_CY_R = 0.56f;
    private static final float PRESET_FE_STR_L = -0.36f;
    private static final float PRESET_FE_STR_R = -0.4f;
    private static final float PRESET_FE_ZOOM_L = 0.8f;
    private static final float PRESET_FE_ZOOM_R = 0.74f;
    private static final float PRESET_FINE_L = -41.0f;
    private static final float PRESET_FINE_R = 19.0f;
    private static final boolean PRESET_FLIPV = true;
    private static final int PRESET_ROT_L = 270;
    private static final int PRESET_ROT_R = 90;
    private static final int ROLE_FRONT = 0;
    private static final int ROLE_LEFT = 1;
    private static final int ROLE_RIGHT = 2;
    private static final String TAG = "OverlayService";
    public static volatile int avmCamId = 1;
    public static volatile boolean directMode = true;
    public static volatile int dispMode = 0;
    public static volatile int drivingHeightPx = 405;
    public static volatile int drivingRotLeft = 0;
    public static volatile int drivingRotRight = 0;
    public static volatile int drivingWidthPx = 720;
    public static volatile int wheelStripWidthPx = 270;
    private volatile int steeringWheelSide = 0; // 0 = none, 1 = Left (FL), 2 = Right (FR)
    public static volatile int frontIndex = 4;
    public static volatile int glRenderH = 1080;
    public static volatile int glRenderW = 1920;
    public static volatile int leftCrop = 1;
    public static volatile int leftIndex = 2;
    public static volatile int leftRotationDeg = 270;
    public static volatile int rightCrop = 2;
    public static volatile int rightIndex = 3;
    public static volatile int rightRotationDeg = 90;
    private static volatile OverlayService sInstance;
    private BevAutomationManager bevAutomation;

    public static OverlayService getInstance() {
        return sInstance;
    }

    public BevAutomationManager getBevAutomation() {
        return this.bevAutomation;
    }

    public VehiclePanoView getPanoView() {
        return this.panoView;
    }

    public com.nineoad.bydsignpicinpic.signal.TurnSignalListener getTurnSignalListener() {
        return this.listener;
    }

    public com.nineoad.bydsignpicinpic.signal.AdasBlindSpotMonitor getAdasMonitor() {
        return this.adasMonitor;
    }

    private Button btnSwitchCam;
    private WindowManager.LayoutParams btnLp;

    public static volatile boolean isOemCameraForeground = false;
    public static volatile boolean isReverseActive = false;
    public static volatile boolean isStudioActive = false;
    public static volatile boolean allowStudioPipTest = false;

    public boolean isShowing() {
        return this.pendingSide != 0;
    }
    private Surface studioSurfaceL = null;
    private Surface studioSurfaceR = null;
    private Surface studioSurfaceRear = null;
    private boolean glRearAttached = false;
    private boolean glBevAttached = false;
    private Surface glBevSurface;
    private VehiclePanoView panoView;
    private FrameLayout wheelOverlay;
    private volatile float bevScale = 1.0f;
    private volatile boolean userDismissedRear = false;
    private AvcClient avc;
    private LinearLayout camBox;
    private CameraFeed feed;
    private Surface glAttachedSurface;
    private FisheyeGLView glView;
    private TurnSignalListener listener;
    private WindowManager.LayoutParams lp;
    private Panel panelFront;
    private Panel panelLeft;
    private Panel panelRight;
    private SharedPreferences prefs;
    private FrameLayout root;
    private ScaleGestureDetector scaleDetector;
    private SurfaceTexture surfaceTexture;
    private TextureView texture;
    private WindowManager wm;
    private View blindSpotLeftView;
    private View blindSpotRightView;
    private com.nineoad.bydsignpicinpic.signal.AdasBlindSpotMonitor adasMonitor;
    private android.animation.ObjectAnimator pulseAnimLeft;
    private android.animation.ObjectAnimator pulseAnimRight;
    public static volatile float[] drivingCropLeft = {0.05f, 0.24f, 0.92f, 1.0f};
    public static volatile float[] drivingCropRight = {0.08f, 0.24f, 0.95f, 1.0f};
    public static volatile boolean drivingMirrorLeft = false;
    public static volatile boolean drivingMirrorRight = false;
    public static volatile boolean drivingFlipVLeft = false;
    public static volatile boolean drivingFlipVRight = false;
    public static volatile float drivingFineLeft = 0.0f;
    public static volatile float drivingFineRight = 0.0f;
    public static volatile float drivingFeStrengthLeft = 0.0f;
    public static volatile float drivingFeStrengthRight = 0.0f;
    public static volatile float drivingFeZoomLeft = 1.0f;
    public static volatile float drivingFeZoomRight = 1.0f;
    public static volatile float drivingFeCxLeft = 0.5f;
    public static volatile float drivingFeCxRight = 0.5f;
    public static volatile float drivingFeCyLeft = 0.5f;
    public static volatile float drivingFeCyRight = 0.5f;
    public static volatile float[] rearCrop = {0.0f, 0.20f, 1.0f, 0.80f};
    public static volatile boolean rearMirror = true;
    public static volatile boolean rearFlipV = false;
    public static volatile float rearFine = 0.0f;
    public static volatile int rearRot = 0;
    public static volatile float rearFeStrength = 0.0f;
    public static volatile float rearFeZoom = 1.0f;
    public static volatile float rearFeCx = 0.5f;
    public static volatile float rearFeCy = 0.5f;
    private static final float[] PRESET_CROP_L = {0.27f, 0.59f, 0.76f, 0.94f};
    private static final float[] PRESET_CROP_R = {0.24f, 0.66f, 0.66f, 0.95f};
    private static final float[] BAL_CROP_L = {0.22f, 0.63f, 0.85f, 0.97f};
    private static final float[] BAL_CROP_R = {0.15f, 0.55f, 0.85f, 1.0f};
    public static volatile int singleWidthPx = 380;
    public static volatile int singleHeightPx = 660;
    public static volatile int hazardWidthPx = 680;
    public static volatile int hazardHeightPx = 820;
    public static volatile boolean rightSideOfScreen = true;
    public static volatile int customOrient = 0;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int glAttachedIndex = -1;
    private int glIndex = -1;
    private volatile float singleScale = 1.0f;
    private volatile float hazardScale = 1.0f;
    private volatile float drivingScale = 1.0f;
    private volatile float balanceScale = 1.0f;
    private volatile float customScale = 1.0f;
    private volatile float rearScale = 1.0f;
    private volatile int pendingSide = 0;
    private boolean hazardMode = false;
    private boolean isDualView = false;
    private LinearLayout sideBox;
    private boolean glLeftAttached = false;
    private boolean glRightAttached = false;
    private volatile boolean glWarmed = false;

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    public static int customOrientOf(int i) {
        return customOrient;
    }

    public static void setCustomOrient(int i, int i2) {
        customOrient = i2;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        this.wm = (WindowManager) getSystemService("window");
        buildOverlay();
        try {
            TurnSignalListener.lingerMs = (long) (this.prefs.getFloat("lingerSec", 3.5f) * 1000.0f);
        } catch (Throwable unused) {
        }
        this.avc = new AvcClient(this);
        if (!directMode) {
            this.avc.bind();
        }
        this.bevAutomation = new BevAutomationManager(this, new BevAutomationManager.Callback() {
            @Override
            public void onBevTriggerShow(final int triggerReason) {
            }

            @Override
            public void onBevTriggerHide(final int triggerReason) {
            }

            @Override
            public void onWheelTriggerShow(final int side) {
                OverlayService.this.main.post(new Runnable() {
                    @Override
                    public void run() {
                        OverlayService.this.handleWheelTriggerShow(side);
                    }
                });
            }

            @Override
            public void onWheelTriggerHide() {
                OverlayService.this.main.post(new Runnable() {
                    @Override
                    public void run() {
                        OverlayService.this.handleWheelTriggerHide();
                    }
                });
            }
        });

        TurnSignalListener turnSignalListener = new TurnSignalListener(this, new TurnSignalListener.Callback() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda5
            @Override // com.nineoad.bydsignpicinpic.signal.TurnSignalListener.Callback
            public final void onSignal(int i) {
                OverlayService.this.onSignal(i);
            }
        }, false);
        this.listener = turnSignalListener;
        turnSignalListener.start();

        this.adasMonitor = new com.nineoad.bydsignpicinpic.signal.AdasBlindSpotMonitor(new com.nineoad.bydsignpicinpic.signal.AdasBlindSpotMonitor.Callback() {
            @Override
            public void onAlertChanged(boolean alertLeft, boolean alertRight) {
                OverlayService.this.handleAdasAlert(alertLeft, alertRight);
            }
        });
        this.adasMonitor.start();
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        startForeground(1, notification());
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_SHOW_LEFT.equals(action)) {
            this.main.post(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda6
                @Override // java.lang.Runnable
                public final void run() {
                    OverlayService.this.lambda$onStartCommand$0();
                }
            });
        } else if (ACTION_SHOW_RIGHT.equals(action)) {
            this.main.post(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda7
                @Override // java.lang.Runnable
                public final void run() {
                    OverlayService.this.lambda$onStartCommand$1();
                }
            });
        } else if (ACTION_SHOW_HAZARD.equals(action)) {
            this.main.post(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda8
                @Override // java.lang.Runnable
                public final void run() {
                    OverlayService.this.lambda$onStartCommand$2();
                }
            });
        } else if (ACTION_SHOW_REAR.equals(action)) {
            this.isRearviewActive = true;
            this.userDismissedRear = false;
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.show(4);
                }
            });
        } else if (ACTION_HIDE_REAR.equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.isRearviewActive = false;
                    OverlayService.this.userDismissedRear = true;
                    OverlayService.this.hide();
                }
            });
        } else if (ACTION_TOGGLE_REAR.equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.toggleRearview();
                }
            });
        } else if (ACTION_HIDE_BEV.equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.isBevActive = false;
                    OverlayService.this.hide();
                }
            });
        } else if (ACTION_TOGGLE_BEV.equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.toggleBev();
                }
            });
        } else if (ACTION_SHOW_BEV.equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.showBev();
                }
            });
        } else if ("SHOW_WHEEL_LEFT".equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.handleWheelTriggerShow(1);
                }
            });
        } else if ("SHOW_WHEEL_RIGHT".equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.handleWheelTriggerShow(2);
                }
            });
        } else if ("HIDE_WHEEL".equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.handleWheelTriggerHide();
                }
            });
        } else if ("RELOAD_PREFS".equals(action)) {
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    if (OverlayService.this.bevAutomation != null) {
                        OverlayService.this.bevAutomation.reloadPreferences();
                    }
                    OverlayService.this.refreshView();
                }
            });
        } else if ("TEST_ADAS".equals(action)) {
            final boolean left = intent != null ? intent.getBooleanExtra("left", true) : true;
            final boolean right = intent != null ? intent.getBooleanExtra("right", true) : true;
            final long duration = intent != null ? intent.getLongExtra("duration", 5000L) : 5000L;
            this.main.post(new Runnable() {
                @Override
                public final void run() {
                    OverlayService.this.testAdasAlert(left, right, duration);
                }
            });
        } else if (ACTION_HIDE.equals(action)) {
            this.main.post(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda9
                @Override // java.lang.Runnable
                public final void run() {
                    OverlayService.this.hide();
                }
            });
        } else if (ACTION_REFRESH.equals(action)) {
            this.main.post(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda10
                @Override // java.lang.Runnable
                public final void run() {
                    OverlayService.this.refreshView();
                }
            });
        }
        return 1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onStartCommand$0() {
        show(1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onStartCommand$1() {
        show(2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onStartCommand$2() {
        show(3);
    }

    private void buildOverlay() {
        this.prefs = getSharedPreferences("pnp", 0);
        FrameLayout frameLayout = new FrameLayout(this);
        this.root = frameLayout;
        TextureView textureView = new TextureView(this);
        this.texture = textureView;
        textureView.setAlpha(0.001f);
        textureView.setSurfaceTextureListener(this);
        this.root.addView(this.texture, new FrameLayout.LayoutParams(1, 1));
        View view = new View(this);
        view.setVisibility(8);
        this.root.addView(view, new FrameLayout.LayoutParams(1, 1));
        this.panelFront = new Panel(frontIndex, 0);
        this.panelLeft = new Panel(leftIndex, 1);
        this.panelRight = new Panel(rightIndex, 2);
        LinearLayout linearLayout = new LinearLayout(this);
        this.sideBox = linearLayout;
        linearLayout.setOrientation(0);
        linearLayout.addView(this.panelLeft.view, new LinearLayout.LayoutParams(0, -1, 1.0f));
        linearLayout.addView(this.panelRight.view, new LinearLayout.LayoutParams(0, -1, 1.0f));
        LinearLayout linearLayout2 = new LinearLayout(this);
        this.camBox = linearLayout2;
        linearLayout2.setOrientation(1);
        this.camBox.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
        this.camBox.addView(this.panelFront.view, new LinearLayout.LayoutParams(-1, 0, 45.0f));
        this.camBox.addView(linearLayout, new LinearLayout.LayoutParams(-1, 0, 55.0f));
        this.camBox.setVisibility(8);
        this.root.addView(this.camBox, new FrameLayout.LayoutParams(-1, -1));
        FisheyeGLView fisheyeGLView = new FisheyeGLView(this);
        this.glView = fisheyeGLView;
        fisheyeGLView.setVisibility(0);
        fisheyeGLView.setActiveOnScreen(false);
        this.glView.setRenderSize(glRenderW, glRenderH);
        this.glView.setSurfaceReadyListener(new FisheyeGLView.SurfaceReadyListener() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda3
            @Override // com.nineoad.bydsignpicinpic.overlay.FisheyeGLView.SurfaceReadyListener
            public final void onReady(Surface surface) {
                OverlayService.this.lambda$buildOverlay$4(surface);
            }
        });
        this.root.addView(this.glView, new FrameLayout.LayoutParams(-1, -1));

        VehiclePanoView vehiclePanoView = new VehiclePanoView(this);
        this.panoView = vehiclePanoView;
        vehiclePanoView.setVisibility(0);
        vehiclePanoView.setActiveOnScreen(false);
        this.panoView.setSurfaceReadyListener(new VehiclePanoView.SurfaceReadyListener() {
            @Override
            public void onCameraSurfaceReady(Surface surface) {
                OverlayService.this.glBevSurface = surface;
                if (OverlayService.this.feed == null && !OverlayService.isOemCameraForeground) {
                    OverlayService.this.ensureFeed();
                }
                OverlayService.this.syncGl();
            }

            @Override
            public void onCameraSurfaceError() {
                onCameraSurfaceDestroyed();
                if (OverlayService.this.pendingSide == 5) {
                    OverlayService.isBevActive = false;
                    OverlayService.this.hide();
                }
            }

            @Override
            public void onCameraSurfaceDestroyed() {
                if (OverlayService.this.glBevAttached && OverlayService.this.glBevSurface != null && OverlayService.this.feed != null) {
                    OverlayService.this.feed.rmSurface(OverlayService.this.glBevSurface, 0);
                    OverlayService.this.glBevAttached = false;
                }
                OverlayService.this.glBevSurface = null;
            }
        });
        this.root.addView(this.panoView, new FrameLayout.LayoutParams(-1, -1));

        // 4-Wheel Blindspot Monitor Overlay (Crosshair divider & labels)
        FrameLayout quadOverlay = new FrameLayout(this);
        this.wheelOverlay = quadOverlay;
        quadOverlay.setVisibility(View.GONE);

        // Center Horizontal Divider Line
        View divH = new View(this);
        divH.setBackgroundColor(0x8038BDF8);
        FrameLayout.LayoutParams lpH = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(2));
        lpH.gravity = android.view.Gravity.CENTER_VERTICAL;
        quadOverlay.addView(divH, lpH);

        // Center Vertical Divider Line
        View divV = new View(this);
        divV.setBackgroundColor(0x8038BDF8);
        FrameLayout.LayoutParams lpV = new FrameLayout.LayoutParams(dpToPx(2), FrameLayout.LayoutParams.MATCH_PARENT);
        lpV.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        quadOverlay.addView(divV, lpV);

        // 4 Wheel Corner Badges
        quadOverlay.addView(createWheelBadge("FL · หน้าซ้าย", android.view.Gravity.TOP | android.view.Gravity.LEFT));
        quadOverlay.addView(createWheelBadge("FR · หน้าขวา", android.view.Gravity.TOP | android.view.Gravity.RIGHT));
        quadOverlay.addView(createWheelBadge("RL · หลังซ้าย", android.view.Gravity.BOTTOM | android.view.Gravity.LEFT));
        quadOverlay.addView(createWheelBadge("RR · หลังขวา", android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT));

        this.root.addView(quadOverlay, new FrameLayout.LayoutParams(-1, -1));

        // ADAS Blind Spot & Turn Signal Indicators (Vertical Red Gradient)
        View bsLeft = new View(this);
        this.blindSpotLeftView = bsLeft;
        android.graphics.drawable.GradientDrawable gradL = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFF1744, 0x88FF1744, 0x00FF1744});
        float rPx = dpToPx(pipCornerRadiusDp);
        gradL.setCornerRadii(new float[]{rPx, rPx, 0, 0, 0, 0, rPx, rPx});
        bsLeft.setBackground(gradL);
        FrameLayout.LayoutParams lpBsL = new FrameLayout.LayoutParams(dpToPx(56), FrameLayout.LayoutParams.MATCH_PARENT);
        lpBsL.gravity = android.view.Gravity.LEFT | android.view.Gravity.TOP;
        bsLeft.setLayoutParams(lpBsL);
        bsLeft.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            bsLeft.setElevation(dpToPx(20));
        }
        this.root.addView(bsLeft);

        View bsRight = new View(this);
        this.blindSpotRightView = bsRight;
        android.graphics.drawable.GradientDrawable gradR = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{0xFFFF1744, 0x88FF1744, 0x00FF1744});
        gradR.setCornerRadii(new float[]{0, 0, rPx, rPx, rPx, rPx, 0, 0});
        bsRight.setBackground(gradR);
        FrameLayout.LayoutParams lpBsR = new FrameLayout.LayoutParams(dpToPx(56), FrameLayout.LayoutParams.MATCH_PARENT);
        lpBsR.gravity = android.view.Gravity.RIGHT | android.view.Gravity.TOP;
        bsRight.setLayoutParams(lpBsR);
        bsRight.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            bsRight.setElevation(dpToPx(20));
        }
        this.root.addView(bsRight);

        Button button = new Button(this);
        this.btnSwitchCam = button;
        button.setText("+ 2 กล้อง");
        this.btnSwitchCam.setTextSize(13.0f);
        this.btnSwitchCam.setTypeface(null, android.graphics.Typeface.BOLD);
        this.btnSwitchCam.setTextColor(-1);
        android.graphics.drawable.GradientDrawable gradientDrawable = new android.graphics.drawable.GradientDrawable();
        gradientDrawable.setShape(0);
        gradientDrawable.setColor(0xE6202020);
        gradientDrawable.setCornerRadius(dpToPx(16));
        gradientDrawable.setStroke(dpToPx(1), 0xCCFFFFFF);
        this.btnSwitchCam.setBackground(gradientDrawable);
        this.btnSwitchCam.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
        if (Build.VERSION.SDK_INT >= 21) {
            this.btnSwitchCam.setElevation(dpToPx(8));
        }
        this.btnSwitchCam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                OverlayService.this.toggleDualView();
            }
        });
        this.btnSwitchCam.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((right - left) != (oldRight - oldLeft)) {
                    OverlayService.this.updateButtonPosition();
                }
            }
        });
        WindowManager.LayoutParams btnLayoutParams = new WindowManager.LayoutParams(-2, dpToPx(34), Build.VERSION.SDK_INT >= 26 ? 2038 : 2002, 40, -3);
        this.btnLp = btnLayoutParams;
        btnLayoutParams.gravity = 8388659;
        this.btnLp.x = -500;
        this.btnLp.y = -500;
        this.btnSwitchCam.setVisibility(8);

        // Keep root permanently VISIBLE so EGLSurface and PanoAPI never get destroyed
        this.root.setVisibility(0);
        this.singleScale = clampScale(this.prefs.getFloat("scale", 0.9500799f));
        this.hazardScale = clampScale(this.prefs.getFloat("scale_hz", 0.93681604f));
        this.drivingScale = clampScale(this.prefs.getFloat("scale_dr", 0.9500799f));
        this.balanceScale = clampScale(this.prefs.getFloat("scale_ba", 0.9500799f));
        this.customScale = clampScale(this.prefs.getFloat("scale_cu", 0.95554584f));
        this.rearScale = clampScale(this.prefs.getFloat("scale_rear", 1.0796669f));
        this.bevScale = clampScale(this.prefs.getFloat("scale_bev", 1.502741f));
        dispMode = this.prefs.getInt("mode", 2);
        loadDrivingTuning();
        // Start parked off-screen with FLAG_NOT_FOCUSABLE (8) | FLAG_NOT_TOUCH_MODAL (32) | FLAG_NOT_TOUCHABLE (16) | FLAG_LAYOUT_NO_LIMITS (512)
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(scaled(singleWidthPx), scaled(singleHeightPx), Build.VERSION.SDK_INT >= 26 ? 2038 : 2002, 40 | 16 | 512, -3);
        this.lp = layoutParams;
        layoutParams.gravity = 8388659;
        DisplayMetrics initDm = getResources().getDisplayMetrics();
        this.lp.x = initDm.widthPixels + 500;
        this.lp.y = this.prefs.getInt("pip_anchor_y", 100);
        this.scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService.1
            @Override // android.view.ScaleGestureDetector.SimpleOnScaleGestureListener, android.view.ScaleGestureDetector.OnScaleGestureListener
            public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
                OverlayService overlayService = OverlayService.this;
                overlayService.setCurScale(OverlayService.clampScale(overlayService.curScale() * scaleGestureDetector.getScaleFactor()));
                OverlayService.this.resizeWindow();
                return true;
            }

            @Override // android.view.ScaleGestureDetector.SimpleOnScaleGestureListener, android.view.ScaleGestureDetector.OnScaleGestureListener
            public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
                OverlayService.this.prefs.edit().putFloat(OverlayService.this.keyScale(), OverlayService.this.curScale()).apply();
            }
        });
        final android.view.GestureDetector gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.i(OverlayService.TAG, "Double tap detected on PiP -> closing");
                OverlayService.this.isRearviewActive = false;
                OverlayService.this.userDismissedRear = true;
                OverlayService.this.isBevActive = false;
                if (OverlayService.this.bevAutomation != null) {
                    OverlayService.this.bevAutomation.setManualActive(false);
                }
                OverlayService.this.hide();
                return true;
            }
        });
        this.root.setOnTouchListener(new View.OnTouchListener() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService.2
            float downX;
            float downY;
            int startX;
            int startY;

            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View view2, MotionEvent motionEvent) {
                if (gestureDetector.onTouchEvent(motionEvent)) {
                    return true;
                }
                OverlayService.this.scaleDetector.onTouchEvent(motionEvent);
                if (!OverlayService.this.scaleDetector.isInProgress() && motionEvent.getPointerCount() <= 1) {
                    int actionMasked = motionEvent.getActionMasked();
                    if (actionMasked != 0) {
                        if (actionMasked == 1) {
                            DisplayMetrics dm = new DisplayMetrics();
                            OverlayService.this.wm.getDefaultDisplay().getMetrics(dm);
                            int screenW = dm.widthPixels;
                            int curX = OverlayService.this.lp.x;
                            int curY = OverlayService.this.lp.y;
                            int curW = OverlayService.this.lp.width;

                            int marginRight = screenW - (curX + curW);
                            int marginLeft = curX;
                            boolean isRight = marginRight <= marginLeft;

                            OverlayService.this.prefs.edit()
                                .putBoolean("pip_anchor_right", isRight)
                                .putInt("pip_margin_right", Math.max(0, marginRight))
                                .putInt("pip_margin_left", Math.max(0, marginLeft))
                                .putInt("pip_anchor_y", curY)
                                .putInt(OverlayService.this.keyX(), curX)
                                .putInt(OverlayService.this.keyY(), curY)
                                .apply();
                            OverlayService.this.updateButtonPosition();
                            return true;
                        }
                        if (actionMasked != 2) {
                            return false;
                        }
                        DisplayMetrics dm = new DisplayMetrics();
                        OverlayService.this.wm.getDefaultDisplay().getMetrics(dm);
                        int newX = this.startX + ((int) (motionEvent.getRawX() - this.downX));
                        int newY = this.startY + ((int) (motionEvent.getRawY() - this.downY));
                        OverlayService.this.lp.x = Math.max(0, Math.min(dm.widthPixels - OverlayService.this.lp.width, newX));
                        OverlayService.this.lp.y = Math.max(0, Math.min(dm.heightPixels - OverlayService.this.lp.height, newY));
                        try {
                            OverlayService.this.wm.updateViewLayout(OverlayService.this.root, OverlayService.this.lp);
                            OverlayService.this.updateButtonPosition();
                        } catch (Throwable unused) {
                        }
                        return true;
                    }
                    this.downX = motionEvent.getRawX();
                    this.downY = motionEvent.getRawY();
                    this.startX = OverlayService.this.lp.x;
                    this.startY = OverlayService.this.lp.y;
                }
                return true;
            }
        });
        try {
            this.wm.addView(this.root, this.lp);
        } catch (Throwable th) {
            Log.e(TAG, "addView failed (overlay permission?): " + th);
        }
        try {
            this.wm.addView(this.btnSwitchCam, this.btnLp);
        } catch (Throwable th2) {
            Log.e(TAG, "wm addView btnSwitchCam failed: " + th2);
        }

        pipCornerRadiusDp = this.prefs.getInt("pip_corner_radius", 16);
        rearAspect = this.prefs.getInt("rear_aspect", 0);
        rearIndex = this.prefs.getInt("rear_cam_index", 1);
        applyCornerRadius();
        this.main.postDelayed(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                OverlayService.this.warmGl();
            }
        }, 1200L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildOverlay$4(Surface surface) {
        this.main.post(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                OverlayService.this.lambda$buildOverlay$3();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildOverlay$3() {
        if (isOemCameraForeground) {
            return;
        }
        if (directMode && this.surfaceTexture != null) {
            ensureFeed();
        }
        syncGl();
        pushGlParams();
        if (this.pendingSide == 0) {
            if (this.feed != null) {
                this.feed.setFps(1);
            }
            return;
        }
        if (this.feed != null) {
            this.feed.setFps(25);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void warmGl() {
        if (this.glWarmed || this.glView == null || this.root == null || this.lp == null || this.wm == null) {
            return;
        }
        this.glWarmed = true;
        try {
            if (directMode && !isOemCameraForeground && this.surfaceTexture != null) {
                ensureFeed();
                syncGl();
                pushGlParams();
                if (this.pendingSide == 0 && this.feed != null) {
                    this.feed.setFps(1);
                }
            }
            Log.i(TAG, "Warm camera standby & persistent GL surface active");
        } catch (Throwable th) {
            Log.w(TAG, "warmGl: " + th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSignal(final int i) {
        this.main.post(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                OverlayService.this.lambda$onSignal$5(i);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onSignal$5(int i) {
        if (i == 0) {
            if (this.isRearviewActive && !this.userDismissedRear) {
                show(4);
            } else {
                this.isBevActive = false;
                hide();
            }
        } else {
            show(i);
        }
    }

    public void handleWheelTriggerShow(int side) {
        if (isOemCameraForeground) return;
        if (isStudioActive && !allowStudioPipTest) return;
        if (this.hazardMode || this.pendingSide == 3) {
            // Hazard light active: do not disrupt dual emergency camera view
            return;
        }

        boolean wheelEnabled = this.prefs.getBoolean("wheel_trigger_enabled", true)
                && this.prefs.getBoolean("bev_auto_steering", true);
        if (!wheelEnabled) {
            return;
        }

        this.steeringWheelSide = side;
        if (this.pendingSide == 1 || this.pendingSide == 2) {
            if (this.isDualView) {
                // Expanded dual-camera mode active: do not stretch or attach 3rd camera
                return;
            }
            // Turn signal already active in single mode: expand PIP window to Combined mode
            if (this.btnSwitchCam != null) this.btnSwitchCam.setVisibility(8);
            if (this.glView != null) {
                this.glView.setDualMode(false, this.pendingSide == 1 ? 21 : 22);
            }
            applyWindowForMode();
            updateRenderSize();
            pushGlParams();
            Log.i(TAG, "Attached front wheel (" + side + ") to active turn signal PIP");
        } else {
            // Front wheel camera is only attached when turn signal is active; do not popup standalone
            Log.d(TAG, "Wheel trigger ignored because turn signal is not active (pendingSide=" + this.pendingSide + ")");
        }
    }

    public void handleWheelTriggerHide() {
        this.steeringWheelSide = 0;
        if (this.hazardMode || this.pendingSide == 3) {
            return;
        }
        if (this.pendingSide == 1 || this.pendingSide == 2) {
            if (this.isDualView) {
                return;
            }
            // Turn signal is still active: shrink PIP window back to 16:9
            if (this.btnSwitchCam != null) this.btnSwitchCam.setVisibility(0);
            if (this.glView != null) {
                this.glView.setDualMode(false, this.pendingSide);
            }
            applyWindowForMode();
            updateRenderSize();
            pushGlParams();
            Log.i(TAG, "Detached front wheel from turn signal PIP");
        } else if (this.pendingSide == 5) {
            // Standalone wheel finished: restore rearview or hide
            if (this.isRearviewActive && !this.userDismissedRear) {
                show(4);
            } else {
                hide();
            }
            Log.i(TAG, "Hidden standalone front wheel PIP");
        }
    }

    public void applyCornerRadius() {
        if (this.root == null) return;
        final int radiusPx = dpToPx(pipCornerRadiusDp);
        if (Build.VERSION.SDK_INT >= 21) {
            this.root.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                }
            });
            this.root.setClipToOutline(pipCornerRadiusDp > 0);
        }
        this.root.setBackground(null);
        if (this.glView != null) {
            this.glView.setCornerRadiusPx(radiusPx);
        }
        if (this.panoView != null) {
            this.panoView.setCornerRadiusPx(radiusPx);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                this.panoView.setClipToOutline(radiusPx > 0);
                this.panoView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                    }
                });
            }
        }
    }

    private final Runnable feedHealthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (OverlayService.this.pendingSide == 0 || OverlayService.this.pendingSide == 5 || OverlayService.isOemCameraForeground || !OverlayService.directMode) {
                return;
            }
            if (OverlayService.this.glView != null) {
                long lastFrame = OverlayService.this.glView.getLastFrameArrivalMs();
                long age = android.os.SystemClock.uptimeMillis() - lastFrame;
                if (lastFrame == 0L || age > 650L) {
                    Log.w(TAG, "Camera producer health check: no frame in " + age + "ms -> self-healing CameraFeed");
                    OverlayService.this.detachGl();
                    OverlayService.this.stopFeed();
                    OverlayService.this.glView.resetWarmState();
                    OverlayService.this.ensureFeed();
                    if (OverlayService.this.feed != null) {
                        OverlayService.this.feed.setFps(25);
                    }
                    OverlayService.this.syncGl();
                    OverlayService.this.pushGlParams();
                }
            }
        }
    };

    private void show(int i) {
        if (isOemCameraForeground) {
            Log.i(TAG, "show(" + i + ") blocked because Vehicle Image (OEM Camera) is active on screen");
            return;
        }
        if (isStudioActive && !allowStudioPipTest) {
            Log.i(TAG, "show(" + i + ") skipped because Camera Studio Activity is in foreground");
            return;
        }
        dispMode = this.prefs.getInt("mode", 3);
        loadDrivingTuning();
        if (this.glView != null) {
            loadQuadWheelTuning(this.prefs, this.glView);
        }
        this.pendingSide = i;
        boolean zHazard = i == 3;
        boolean zRear = i == 4;
        boolean zBev = i == 5;
        this.hazardMode = zHazard;
        if (zHazard) {
            // Hazard mode: show dual camera view with Custom settings!
            this.isDualView = true;
        } else if (zRear || zBev) {
            this.isDualView = false;
        }

        if (directMode) {
            if (this.surfaceTexture != null) {
                ensureFeed();
            }
            if (this.feed != null) {
                this.feed.setFps(25);
            }
            pushGlParams();
            syncGl();
        }

        detachPanels();
        this.panelFront.view.setVisibility(8);
        this.panelLeft.view.setVisibility(8);
        this.panelRight.view.setVisibility(8);
        this.camBox.setVisibility(8);
        updateBlindSpotViews(0);

        // Mode 5 = Front Wheel Monitor (FL = 11, FR = 12)
        if (zBev) {
            if (this.panoView != null) {
                this.panoView.setActiveOnScreen(false);
                this.panoView.setVisibility(View.GONE);
            }
            this.glView.setVisibility(View.VISIBLE);
            this.glView.setActiveOnScreen(true);
            int wheelSideMode = (this.steeringWheelSide == 2) ? 12 : 11;
            this.glView.setDualMode(false, wheelSideMode);
            if (this.wheelOverlay != null) {
                this.wheelOverlay.setVisibility(View.GONE);
            }
        } else {
            if (this.panoView != null) {
                this.panoView.setActiveOnScreen(false);
                this.panoView.setVisibility(View.GONE);
            }
            if (this.wheelOverlay != null) {
                this.wheelOverlay.setVisibility(View.GONE);
            }
            this.glView.setVisibility(View.VISIBLE);
            this.glView.setActiveOnScreen(true);
            int targetGlSide = i;
            boolean wheelEnabled = this.prefs.getBoolean("wheel_trigger_enabled", true)
                    && this.prefs.getBoolean("bev_auto_steering", true);
            if (wheelEnabled && !this.isDualView && i == 1 && this.steeringWheelSide != 0) {
                targetGlSide = 21;
            } else if (wheelEnabled && !this.isDualView && i == 2 && this.steeringWheelSide != 0) {
                targetGlSide = 22;
            }
            this.glView.setDualMode(this.isDualView, targetGlSide);
        }

        updateRenderSize();
        updateSwitchButtonText();
        applyWindowForMode();
        this.root.setVisibility(0);
        updateBlindSpotViews(i);
        if (this.btnSwitchCam != null) {
            if (zRear || zBev || this.steeringWheelSide != 0) {
                this.btnSwitchCam.setVisibility(8);
            } else {
                this.btnSwitchCam.setVisibility(0);
            }
            updateButtonPosition();
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    OverlayService.this.updateButtonPosition();
                }
            });
        }

        if (directMode) {
            this.main.removeCallbacks(this.feedHealthCheckRunnable);
            if (!zBev) {
                this.main.postDelayed(this.feedHealthCheckRunnable, 550L);
            }
            return;
        }
        if (!this.avc.isBound()) {
            this.avc.bind();
        }
        this.avc.send(i == 1 ? 2 : 3);
    }

    private void syncGl() {
        if (this.feed == null || isOemCameraForeground) {
            return;
        }
        Surface surfaceL = this.glView != null ? this.glView.getCameraSurfaceLeft() : null;
        Surface surfaceR = this.glView != null ? this.glView.getCameraSurfaceRight() : null;
        Surface surfaceRear = this.glView != null ? this.glView.getCameraSurfaceRear() : null;

        boolean needRear = (!this.isDualView && this.pendingSide == 4);
        boolean needBev = false;
        // Keep Left & Right surfaces attached for single, dual, hazard, and 4-wheel (pendingSide == 5) modes!
        boolean needLeft = !needRear;
        boolean needRight = !needRear;

        // 1. Detach unneeded surfaces first (prevents any HAL state conflicts)
        if (!needRear && this.glRearAttached) {
            if (surfaceRear != null) {
                this.feed.rmSurface(surfaceRear, rearIndex);
            }
            this.glRearAttached = false;
        }
        if (!needBev && this.glBevAttached) {
            if (this.glBevSurface != null) {
                this.feed.rmSurface(this.glBevSurface, 0);
            }
            this.glBevAttached = false;
        }
        if (!needLeft && this.glLeftAttached) {
            if (surfaceL != null) {
                this.feed.rmSurface(surfaceL, leftIndex);
            }
            this.glLeftAttached = false;
        }
        if (!needRight && this.glRightAttached) {
            if (surfaceR != null) {
                this.feed.rmSurface(surfaceR, rightIndex);
            }
            this.glRightAttached = false;
        }

        // 2. Attach needed surfaces cleanly
        if (needRear && !this.glRearAttached && surfaceRear != null) {
            this.glRearAttached = this.feed.addSurface(surfaceRear, rearIndex);
            Log.i(TAG, "gl sync rear ok=" + this.glRearAttached);
        }
        if (needBev && !this.glBevAttached && this.glBevSurface != null) {
            this.glBevAttached = this.feed.addSurface(this.glBevSurface, 0);
            Log.i(TAG, "gl sync bev ok=" + this.glBevAttached);
        }
        if (needLeft && !this.glLeftAttached && surfaceL != null) {
            this.glLeftAttached = this.feed.addSurface(surfaceL, leftIndex);
            Log.i(TAG, "gl sync left ok=" + this.glLeftAttached);
        }
        if (needRight && !this.glRightAttached && surfaceR != null) {
            this.glRightAttached = this.feed.addSurface(surfaceR, rightIndex);
            Log.i(TAG, "gl sync right ok=" + this.glRightAttached);
        }
    }

    private void detachGl() {
        if (this.feed != null) {
            if (this.glBevAttached && this.glBevSurface != null) {
                this.feed.rmSurface(this.glBevSurface, 0);
                this.glBevAttached = false;
            }
            if (this.glView != null) {
                Surface surfaceL = this.glView.getCameraSurfaceLeft();
                Surface surfaceR = this.glView.getCameraSurfaceRight();
                Surface surfaceRear = this.glView.getCameraSurfaceRear();
                if (this.glRearAttached && surfaceRear != null) {
                    this.feed.rmSurface(surfaceRear, rearIndex);
                    this.glRearAttached = false;
                }
                if (this.glLeftAttached && surfaceL != null) {
                    this.feed.rmSurface(surfaceL, leftIndex);
                    this.glLeftAttached = false;
                }
                if (this.glRightAttached && surfaceR != null) {
                    this.feed.rmSurface(surfaceR, rightIndex);
                    this.glRightAttached = false;
                }
            }
        }
    }

    public void attachStudioSurfaces(Surface sL, Surface sR, Surface sRear) {
        Log.i(TAG, "attachStudioSurfaces sL=" + sL + " sR=" + sR + " sRear=" + sRear);
        this.studioSurfaceL = sL;
        this.studioSurfaceR = sR;
        this.studioSurfaceRear = sRear;
        if (this.feed == null) {
            ensureFeed();
        }
        if (this.feed == null) {
            Surface baseSurface = (this.surfaceTexture != null) ? new Surface(this.surfaceTexture)
                    : (sL != null ? sL : (sR != null ? sR : sRear));
            if (baseSurface != null) {
                CameraFeed cameraFeed = new CameraFeed();
                this.feed = cameraFeed;
                boolean ok = cameraFeed.start(baseSurface, avmCamId, 0);
                Log.i(TAG, "fallback feed start: " + ok);
            }
        }
        if (this.feed != null) {
            this.feed.setFps(25);
            if (sL != null) {
                this.feed.addSurface(sL, leftIndex);
            }
            if (sR != null) {
                this.feed.addSurface(sR, rightIndex);
            }
            if (sRear != null) {
                this.feed.addSurface(sRear, rearIndex);
            }
        }
    }

    public void detachStudioSurfaces() {
        Log.i(TAG, "detachStudioSurfaces");
        if (this.feed != null) {
            if (this.studioSurfaceL != null) {
                this.feed.rmSurface(this.studioSurfaceL, leftIndex);
                this.studioSurfaceL = null;
            }
            if (this.studioSurfaceR != null) {
                this.feed.rmSurface(this.studioSurfaceR, rightIndex);
                this.studioSurfaceR = null;
            }
            if (this.studioSurfaceRear != null) {
                this.feed.rmSurface(this.studioSurfaceRear, rearIndex);
                this.studioSurfaceRear = null;
            }
            if (this.pendingSide == 0) {
                this.feed.setFps(1);
            }
        }
    }

    private void pushGlParams() {
        if (this.glView == null) {
            return;
        }

        float rotL, fineL, strL, zoomL, cxL, cyL;
        boolean mirrorL, flipVL;
        float[] cropL;

        float rotR, fineR, strR, zoomR, cxR, cyR;
        boolean mirrorR, flipVR;
        float[] cropR;

        if (this.pendingSide == 4) {
            rotL = rearRot;
            fineL = rearFine;
            cropL = rearCrop;
            strL = rearFeStrength;
            zoomL = rearFeZoom;
            cxL = rearFeCx;
            cyL = rearFeCy;
            mirrorL = rearMirror;
            flipVL = rearFlipV;
            this.glView.setDualMode(false, 4);
            this.glView.setParamsRear(rotL, fineL, mirrorL, flipVL, cropL, strL, zoomL, cxL, cyL);
            return;
        }

        // Mode 1 (Turn Signal / Single / Dual / Hazard): Strictly use user-calibrated parameters
        rotL = drivingRotLeft;
        fineL = drivingFineLeft;
        cropL = drivingCropLeft;
        strL = drivingFeStrengthLeft;
        zoomL = drivingFeZoomLeft;
        cxL = drivingFeCxLeft;
        cyL = drivingFeCyLeft;
        mirrorL = drivingMirrorLeft;
        flipVL = drivingFlipVLeft;

        rotR = drivingRotRight;
        fineR = drivingFineRight;
        cropR = drivingCropRight;
        strR = drivingFeStrengthRight;
        zoomR = drivingFeZoomRight;
        cxR = drivingFeCxRight;
        cyR = drivingFeCyRight;
        mirrorR = drivingMirrorRight;
        flipVR = drivingFlipVRight;

        loadQuadWheelTuning(this.prefs, this.glView);

        if (this.pendingSide == 5) {
            // Standalone Front Wheel (11 = FL, 12 = FR)
            this.glView.setDualMode(false, this.steeringWheelSide == 2 ? 12 : 11);
            return;
        }

        if (!this.isDualView && this.pendingSide == 1 && this.steeringWheelSide != 0) {
            // Combined: Left Turn Signal (16:9) + Front-Left Wheel (Vertical on Right)
            this.glView.setParamsLeft(rotL, fineL, mirrorL, flipVL, cropL, strL, zoomL, cxL, cyL);
            this.glView.setDualMode(false, 21);
            return;
        }

        if (!this.isDualView && this.pendingSide == 2 && this.steeringWheelSide != 0) {
            // Combined: Right Turn Signal (16:9) + Front-Right Wheel (Vertical on Right)
            this.glView.setParamsRight(rotR, fineR, mirrorR, flipVR, cropR, strR, zoomR, cxR, cyR);
            this.glView.setDualMode(false, 22);
            return;
        }

        this.glView.setDualMode(this.isDualView, this.pendingSide);
        this.glView.setParamsLeft(rotL, fineL, mirrorL, flipVL, cropL, strL, zoomL, cxL, cyL);
        this.glView.setParamsRight(rotR, fineR, mirrorR, flipVR, cropR, strR, zoomR, cxR, cyR);
    }
    /* JADX INFO: Access modifiers changed from: private */
    public void ensureFeed() {
        SurfaceTexture surfaceTexture;
        if (this.feed == null && (surfaceTexture = this.surfaceTexture) != null) {
            surfaceTexture.setDefaultBufferSize(1920, 1080);
            Surface surface = new Surface(this.surfaceTexture);
            CameraFeed cameraFeed = new CameraFeed();
            this.feed = cameraFeed;
            boolean zStart = cameraFeed.start(surface, avmCamId, 0);
            Log.i(TAG, "AVM feed (cam " + avmCamId + ") start = " + zStart);
            if (zStart) {
                return;
            }
            this.feed = null;
            if (!this.avc.isBound()) {
                this.avc.bind();
            }
            this.avc.send(this.pendingSide == 1 ? 2 : 3);
        }
    }

    private void attachPanels() {
        if (this.hazardMode) {
            this.panelFront.attach();
            this.panelLeft.attach();
            this.panelRight.attach();
        } else if (this.isDualView) {
            this.panelFront.detach();
            this.panelLeft.attach();
            this.panelRight.attach();
        } else {
            this.panelFront.detach();
            if (this.pendingSide == 1) {
                this.panelLeft.attach();
                this.panelRight.detach();
            } else {
                this.panelRight.attach();
                this.panelLeft.detach();
            }
        }
    }

    private void detachPanels() {
        this.panelFront.detach();
        this.panelLeft.detach();
        this.panelRight.detach();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resizeWindow() {
        int iScaled = scaled(baseWidth());
        int iScaled2 = scaled(baseHeight());
        if (this.lp.width == iScaled && this.lp.height == iScaled2) {
            return;
        }
        this.lp.width = iScaled;
        this.lp.height = iScaled2;
        try {
            this.wm.updateViewLayout(this.root, this.lp);
        } catch (Throwable unused) {
        }
        updateButtonPosition();
    }

    private android.animation.ValueAnimator slideInAnimator;

    private void animateSlideInFromRight(final int targetX) {
        if (this.slideInAnimator != null && this.slideInAnimator.isRunning()) {
            this.slideInAnimator.cancel();
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int startX = Math.max(targetX + 60, Math.min(dm.widthPixels, targetX + 160));
        this.lp.x = startX;
        try {
            this.wm.updateViewLayout(this.root, this.lp);
            applyCornerRadius();
            updateButtonPosition();
        } catch (Throwable unused) {}

        this.slideInAnimator = android.animation.ValueAnimator.ofInt(startX, targetX);
        this.slideInAnimator.setDuration(160);
        this.slideInAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        this.slideInAnimator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                if (OverlayService.this.lp != null && OverlayService.this.root != null && OverlayService.this.wm != null) {
                    OverlayService.this.lp.x = ((Integer) animation.getAnimatedValue()).intValue();
                    try {
                        OverlayService.this.wm.updateViewLayout(OverlayService.this.root, OverlayService.this.lp);
                        OverlayService.this.updateButtonPosition();
                    } catch (Throwable unused) {}
                }
            }
        });
        this.slideInAnimator.start();
    }

    private void applyWindowForMode() {
        this.lp.flags = 40;
        this.lp.width = scaled(baseWidth());
        this.lp.height = scaled(baseHeight());
        DisplayMetrics displayMetrics = new DisplayMetrics();
        this.wm.getDefaultDisplay().getMetrics(displayMetrics);
        int screenW = displayMetrics.widthPixels;
        int screenH = displayMetrics.heightPixels;

        int targetX;
        int targetY;

        if (this.pendingSide == 4) { // Digital rearview mirror mode
            int defX = Math.max(0, (screenW - this.lp.width) / 2);
            int defY = 24;
            targetX = this.prefs.getInt(keyX(), defX);
            targetY = this.prefs.getInt(keyY(), defY);
        } else {
            // Locked edge-anchored position across single/dual/hazard/BEV modes!
            boolean anchorToRight = this.prefs.getBoolean("pip_anchor_right", true);
            int savedMarginRight = this.prefs.getInt("pip_margin_right", 24);
            int savedMarginLeft = this.prefs.getInt("pip_margin_left", 24);
            int savedY = this.prefs.getInt("pip_anchor_y", 100);

            if (anchorToRight) {
                targetX = screenW - this.lp.width - savedMarginRight;
            } else {
                targetX = savedMarginLeft;
            }
            targetY = savedY;
        }

        // Clamp safely inside visible display bounds
        targetX = Math.max(0, Math.min(screenW - this.lp.width, targetX));
        targetY = Math.max(0, Math.min(screenH - this.lp.height, targetY));

        boolean comingFromOffscreen = (this.lp.x >= screenW || this.lp.x < -1000);
        this.lp.y = targetY;

        if (comingFromOffscreen) {
            animateSlideInFromRight(targetX);
        } else {
            this.lp.x = targetX;
            try {
                this.wm.updateViewLayout(this.root, this.lp);
            } catch (Throwable unused) {}
            applyCornerRadius();
            updateButtonPosition();
        }
    }

    private void parkWindowOffscreen() {
        if (this.lp == null || this.root == null || this.wm == null) {
            return;
        }
        if (this.slideInAnimator != null && this.slideInAnimator.isRunning()) {
            this.slideInAnimator.cancel();
        }
        this.lp.flags = 40 | 16 | 512;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        this.lp.x = dm.widthPixels + 500;
        this.lp.y = this.prefs.getInt("pip_anchor_y", 100);
        try {
            this.wm.updateViewLayout(this.root, this.lp);
        } catch (Throwable unused) {
        }
    }

    private int scaled(int i) {
        return Math.round(i * curScale());
    }

    private boolean isLandscape() {
        return dispMode == 1 || dispMode == 2 || dispMode == 3;
    }

    private int activeCustomOrient() {
        return customOrient;
    }

    private int baseSingleWidth() {
        if (this.pendingSide == 5) {
            return wheelStripWidthPx; // Standalone front wheel curb monitor
        }
        if (this.pendingSide == 4) {
            return 960;
        }
        int baseTurnW;
        int iActiveCustomOrient = activeCustomOrient();
        if (iActiveCustomOrient == 0) {
            baseTurnW = drivingWidthPx;
        } else if (iActiveCustomOrient == 1) {
            baseTurnW = drivingHeightPx;
        } else if (iActiveCustomOrient == 2) {
            baseTurnW = drivingWidthPx;
        } else if (iActiveCustomOrient == 3) {
            baseTurnW = drivingHeightPx;
        } else {
            baseTurnW = isLandscape() ? drivingWidthPx : singleWidthPx;
        }

        // If turn signal is active in single mode AND steering wheel angle is triggered AND wheel feature enabled: attach wheel strip to the right!
        boolean wheelEnabled = this.prefs.getBoolean("wheel_trigger_enabled", true)
                && this.prefs.getBoolean("bev_auto_steering", true);
        if (wheelEnabled && !this.isDualView && (this.pendingSide == 1 || this.pendingSide == 2) && this.steeringWheelSide != 0) {
            return baseTurnW + wheelStripWidthPx;
        }
        return baseTurnW;
    }

    private int baseSingleHeight() {
        if (this.pendingSide == 5) {
            return drivingHeightPx; // Height matches Turn Signal height!
        }
        if (this.pendingSide == 4) {
            return rearAspect == 1 ? 240 : 320;
        }
        int iActiveCustomOrient = activeCustomOrient();
        if (iActiveCustomOrient == 0) {
            return drivingHeightPx;
        }
        if (iActiveCustomOrient == 1) {
            return drivingWidthPx;
        }
        if (iActiveCustomOrient == 2) {
            return drivingWidthPx;
        }
        if (iActiveCustomOrient != 3) {
            return isLandscape() ? drivingHeightPx : singleHeightPx;
        }
        return drivingHeightPx;
    }

    private int baseWidth() {
        int singleW = baseSingleWidth();
        return this.isDualView ? (singleW * 2) : singleW;
    }

    private int baseHeight() {
        return baseSingleHeight();
    }

    private void updateRenderSize() {
        if (this.glView == null) {
            return;
        }
        int singleW = baseSingleWidth();
        int singleH = baseSingleHeight();
        if (this.isDualView) {
            int renderH = Math.max(1, Math.round(1920.0f * (float) singleH / (float) (singleW * 2)));
            this.glView.setRenderSize(1920, renderH);
        } else {
            int renderH = Math.max(1, Math.round(1920.0f * (float) singleH / (float) singleW));
            this.glView.setRenderSize(1920, renderH);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public float curScale() {
        if (this.pendingSide == 5) {
            return this.bevScale;
        }
        if (this.pendingSide == 4) {
            return this.rearScale;
        }
        if (this.hazardMode || this.isDualView) {
            return this.hazardScale;
        }
        return this.drivingScale;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setCurScale(float f) {
        if (this.pendingSide == 5) {
            this.bevScale = f;
            return;
        }
        if (this.pendingSide == 4) {
            this.rearScale = f;
            return;
        }
        if (this.hazardMode || this.isDualView) {
            this.hazardScale = f;
            return;
        }
        this.drivingScale = f;
    }

    private String modeSuffix() {
        if (this.pendingSide == 5) {
            return "_bev";
        }
        if (this.pendingSide == 4) {
            return "_rear";
        }
        if (this.hazardMode || this.isDualView) {
            return "_hz";
        }
        return "_dr";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String keyX() {
        return "x" + modeSuffix();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String keyY() {
        return "y" + modeSuffix();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String keyScale() {
        return "scale" + modeSuffix();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshView() {
        dispMode = this.prefs.getInt("mode", dispMode);
        loadDrivingTuning();
        if (this.bevAutomation != null) {
            this.bevAutomation.reloadPreferences();
        }
        int i = this.pendingSide;
        if (i != 0) {
            show(i);
        }
    }

    public static void reapplyLive() {
        final OverlayService overlayService = sInstance;
        if (overlayService == null) {
            return;
        }
        overlayService.main.post(new Runnable() { // from class: com.nineoad.bydsignpicinpic.overlay.OverlayService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                OverlayService.lambda$reapplyLive$6(overlayService);
            }
        });
    }

    static /* synthetic */ void lambda$reapplyLive$6(OverlayService overlayService) {
        overlayService.dispMode = overlayService.prefs.getInt("mode", 3);
        overlayService.loadDrivingTuning();
        if (overlayService.bevAutomation != null) {
            overlayService.bevAutomation.reloadPreferences();
        }
        if (overlayService.glView != null) {
            loadQuadWheelTuning(overlayService.prefs, overlayService.glView);
        }
        if (overlayService.pendingSide == 0) {
            return;
        }
        overlayService.applyWindowForMode();
        overlayService.updateRenderSize();
        overlayService.pushGlParams();
        if (overlayService.panoView != null) {
            overlayService.panoView.reapplyTuning();
        }
    }

    public static void applyRenderSize() {
        FisheyeGLView fisheyeGLView;
        OverlayService overlayService = sInstance;
        if (overlayService == null || (fisheyeGLView = overlayService.glView) == null) {
            return;
        }
        fisheyeGLView.setRenderSize(glRenderW, glRenderH);
    }

    private void loadDrivingTuning() {
        loadCustomTuning(this.prefs, dispMode);
        loadRearTuning(this.prefs);
    }

    public static String customPrefix(int i) {
        if (i == 4) {
            return "rear_";
        }
        return "dr_";
    }

    public static void loadQuadWheelTuning(SharedPreferences sp, FisheyeGLView glView) {
        if (glView == null || sp == null) return;
        // 0: FL (Front Left from Left camera)
        float[] cropFL = loadCrop(sp, "quad_crop_fl", new float[]{0.0f, 0.31f, 1.0f, 1.0f});
        float strFL = sp.getFloat("quad_str_fl", -0.37f);
        float zoomFL = sp.getFloat("quad_zoom_fl", 1.20f);
        float cxFL = sp.getFloat("quad_cx_fl", 0.51f);
        float cyFL = sp.getFloat("quad_cy_fl", 0.50f);
        float rotFL = sp.getFloat("quad_rot_fl", 270.0f);
        float fnFL = sp.getFloat("quad_fn_fl", 10.0f);
        boolean mirFL = sp.getBoolean("quad_mir_fl", false);
        boolean fvFL = sp.getBoolean("quad_fv_fl", false);
        glView.setParamsQuadWheel(0, rotFL, fnFL, mirFL, fvFL, cropFL, strFL, zoomFL, cxFL, cyFL);

        // 1: FR (Front Right from Right camera)
        float[] cropFR = loadCrop(sp, "quad_crop_fr", new float[]{0.0f, 0.32f, 1.0f, 1.0f});
        float strFR = sp.getFloat("quad_str_fr", -0.35f);
        float zoomFR = sp.getFloat("quad_zoom_fr", 1.25f);
        float cxFR = sp.getFloat("quad_cx_fr", 0.46f);
        float cyFR = sp.getFloat("quad_cy_fr", 0.52f);
        float rotFR = sp.getFloat("quad_rot_fr", 90.0f);
        float fnFR = sp.getFloat("quad_fn_fr", -24.0f);
        boolean mirFR = sp.getBoolean("quad_mir_fr", false);
        boolean fvFR = sp.getBoolean("quad_fv_fr", false);
        glView.setParamsQuadWheel(1, rotFR, fnFR, mirFR, fvFR, cropFR, strFR, zoomFR, cxFR, cyFR);

        // 2: RL (Rear Left from Left camera)
        float[] cropRL = loadCrop(sp, "quad_crop_rl", new float[]{0.0f, 0.0f, 1.0f, 1.0f});
        float strRL = sp.getFloat("quad_str_rl", 0.0f);
        float zoomRL = sp.getFloat("quad_zoom_rl", 1.0f);
        float cxRL = sp.getFloat("quad_cx_rl", 0.5f);
        float cyRL = sp.getFloat("quad_cy_rl", 0.5f);
        float rotRL = sp.getFloat("quad_rot_rl", 0.0f);
        float fnRL = sp.getFloat("quad_fn_rl", 0.0f);
        boolean mirRL = sp.getBoolean("quad_mir_rl", false);
        boolean fvRL = sp.getBoolean("quad_fv_rl", false);
        glView.setParamsQuadWheel(2, rotRL, fnRL, mirRL, fvRL, cropRL, strRL, zoomRL, cxRL, cyRL);

        // 3: RR (Rear Right from Right camera)
        float[] cropRR = loadCrop(sp, "quad_crop_rr", new float[]{0.0f, 0.0f, 1.0f, 1.0f});
        float strRR = sp.getFloat("quad_str_rr", 0.0f);
        float zoomRR = sp.getFloat("quad_zoom_rr", 1.0f);
        float cxRR = sp.getFloat("quad_cx_rr", 0.5f);
        float cyRR = sp.getFloat("quad_cy_rr", 0.5f);
        float rotRR = sp.getFloat("quad_rot_rr", 0.0f);
        float fnRR = sp.getFloat("quad_fn_rr", 0.0f);
        boolean mirRR = sp.getBoolean("quad_mir_rr", false);
        boolean fvRR = sp.getBoolean("quad_fv_rr", false);
        glView.setParamsQuadWheel(3, rotRR, fnRR, mirRR, fvRR, cropRR, strRR, zoomRR, cxRR, cyRR);
    }

    public static void loadRearTuning(SharedPreferences sharedPreferences) {
        rearRot = sharedPreferences.getInt("rear_rot_l", 0);
        rearMirror = sharedPreferences.getBoolean("rear_mir_l", true);
        rearFlipV = sharedPreferences.getBoolean("rear_fv_l", false);
        rearFine = sharedPreferences.getFloat("rear_fn_l", 0.0f);
        rearFeStrength = sharedPreferences.getFloat("rear_str_l", -0.02f);
        rearFeZoom = sharedPreferences.getFloat("rear_zoom_l", 2.30f);
        rearFeCx = sharedPreferences.getFloat("rear_cx_l", 0.50845903f);
        rearFeCy = sharedPreferences.getFloat("rear_cy_l", 0.6826888f);
        rearCrop = loadCrop(sharedPreferences, "rear_l", new float[]{0.0f, 0.50f, 1.0f, 1.0f});
        rearAspect = sharedPreferences.getInt("rear_aspect", 0);
        pipCornerRadiusDp = sharedPreferences.getInt("pip_corner_radius", 22);
        rearIndex = sharedPreferences.getInt("rear_cam_index", 1);
    }

    public static void loadCustomTuning(SharedPreferences sharedPreferences, int i) {
        if (i == 4) {
            loadRearTuning(sharedPreferences);
            return;
        }
        String strCustomPrefix = "dr_";
        drivingRotLeft = sharedPreferences.getInt(strCustomPrefix + "rot_l", 0);
        drivingRotRight = sharedPreferences.getInt(strCustomPrefix + "rot_r", 270);
        drivingMirrorLeft = sharedPreferences.getBoolean(strCustomPrefix + "mir_l", true);
        drivingMirrorRight = sharedPreferences.getBoolean(strCustomPrefix + "mir_r", true);
        drivingFlipVLeft = sharedPreferences.getBoolean(strCustomPrefix + "fv_l", false);
        drivingFlipVRight = sharedPreferences.getBoolean(strCustomPrefix + "fv_r", false);
        drivingFineLeft = sharedPreferences.getFloat(strCustomPrefix + "fn_l", 48.0f);
        drivingFineRight = sharedPreferences.getFloat(strCustomPrefix + "fn_r", 27.0f);
        drivingFeStrengthLeft = sharedPreferences.getFloat(strCustomPrefix + "str_l", -0.24f);
        drivingFeStrengthRight = sharedPreferences.getFloat(strCustomPrefix + "str_r", -0.38f);
        drivingFeZoomLeft = sharedPreferences.getFloat(strCustomPrefix + "zoom_l", 1.14f);
        drivingFeZoomRight = sharedPreferences.getFloat(strCustomPrefix + "zoom_r", 0.91f);
        drivingFeCxLeft = sharedPreferences.getFloat(strCustomPrefix + "cx_l", 0.40f);
        drivingFeCxRight = sharedPreferences.getFloat(strCustomPrefix + "cx_r", 0.66f);
        drivingFeCyLeft = sharedPreferences.getFloat(strCustomPrefix + "cy_l", 0.5601612f);
        drivingFeCyRight = sharedPreferences.getFloat(strCustomPrefix + "cy_r", 0.68f);
        drivingCropLeft = loadCrop(sharedPreferences, strCustomPrefix + "l", new float[]{0.22f, 0.57f, 0.81f, 0.97f});
        drivingCropRight = loadCrop(sharedPreferences, strCustomPrefix + "r", new float[]{0.21f, 0.62f, 0.75f, 0.89f});
        customOrient = sharedPreferences.getInt(strCustomPrefix + "orient", 0);
        pipCornerRadiusDp = sharedPreferences.getInt("pip_corner_radius", 22);
    }

    private static float[] loadCrop(SharedPreferences sharedPreferences, String str, float[] fArr) {
        return new float[]{sharedPreferences.getFloat(str + "0", fArr[0]), sharedPreferences.getFloat(str + "1", fArr[1]), sharedPreferences.getFloat(str + "2", fArr[2]), sharedPreferences.getFloat(str + "3", fArr[3])};
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int rotationForRole(int i) {
        if (i == 0) {
            return 0;
        }
        return i == 1 ? drivingRotLeft : drivingRotRight;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public float[] cropForRole(int i) {
        if (i == 0) {
            return null;
        }
        return i == 1 ? drivingCropLeft : drivingCropRight;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean mirrorForRole(int i) {
        if (i == 0) {
            return false;
        }
        return i == 1 ? drivingMirrorLeft : drivingMirrorRight;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean flipVForRole(int i) {
        if (i == 0) {
            return false;
        }
        return i == 1 ? drivingFlipVLeft : drivingFlipVRight;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public float fineForRole(int i) {
        if (i == 0) {
            return 0.0f;
        }
        return i == 1 ? drivingFineLeft : drivingFineRight;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float clampScale(float f) {
        return Math.max(0.5f, Math.min(MAX_SCALE, f));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hide() {
        allowStudioPipTest = false;
        this.main.removeCallbacks(this.feedHealthCheckRunnable);
        this.pendingSide = 0;
        this.hazardMode = false;
        this.isDualView = false;
        detachPanels();
        if (this.glView != null) {
            this.glView.setActiveOnScreen(false);
        }
        if (this.panoView != null) {
            this.panoView.setActiveOnScreen(false);
        }
        this.panelFront.view.setVisibility(8);
        this.panelLeft.view.setVisibility(8);
        this.panelRight.view.setVisibility(8);
        this.camBox.setVisibility(8);
        updateBlindSpotViews(0);

        // Keep CameraFeed warm at 1 FPS in standby & keep Left/Right GL surfaces attached!
        syncGl();
        if (this.feed != null) {
            this.feed.setFps(1);
        }
        parkWindowOffscreen();
        if (this.btnSwitchCam != null) {
            this.btnSwitchCam.setVisibility(8);
        }

        if (directMode || !this.avc.isBound()) {
            return;
        }
        this.avc.send(52);
    }

    private void stopFeed() {
        detachPanels();
        CameraFeed cameraFeed = this.feed;
        if (cameraFeed != null) {
            cameraFeed.stop();
            this.feed = null;
        }
    }

    private void applyTransforms() {
        this.panelFront.applyRotation();
        this.panelLeft.applyRotation();
        this.panelRight.applyRotation();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Matrix rotationMatrix(float[] fArr, int i, int i2, int i3) {
        float[] fArr2;
        if (i3 == 90) {
            float f = i;
            float f2 = i2;
            fArr2 = new float[]{f, 0.0f, f, f2, 0.0f, f2, 0.0f, 0.0f};
        } else if (i3 == 180) {
            float f3 = i;
            float f4 = i2;
            fArr2 = new float[]{f3, f4, 0.0f, f4, 0.0f, 0.0f, f3, 0.0f};
        } else if (i3 == 270) {
            float f5 = i2;
            float f6 = i;
            fArr2 = new float[]{0.0f, f5, 0.0f, 0.0f, f6, 0.0f, f6, f5};
        } else {
            float f7 = i;
            float f8 = i2;
            fArr2 = new float[]{0.0f, 0.0f, f7, 0.0f, f7, f8, 0.0f, f8};
        }
        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(fArr, 0, fArr2, 0, 4);
        return matrix;
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i2) {
        this.surfaceTexture = surfaceTexture;
        if (!directMode || isOemCameraForeground) {
            return;
        }
        ensureFeed();
        syncGl();
        pushGlParams();
        if (this.pendingSide == 0) {
            if (this.feed != null) {
                this.feed.setFps(1);
            }
            return;
        }
        if (this.feed != null) {
            this.feed.setFps(25);
        }
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i2) {
        applyTransforms();
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        this.surfaceTexture = null;
        return true;
    }

    private final class Panel implements TextureView.SurfaceTextureListener {
        boolean added;
        final int index;
        final int role;
        SurfaceTexture st;
        Surface surface;
        final TextureView view;

        @Override // android.view.TextureView.SurfaceTextureListener
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

        Panel(int i, int i2) {
            this.index = i;
            this.role = i2;
            TextureView textureView = new TextureView(OverlayService.this);
            this.view = textureView;
            textureView.setSurfaceTextureListener(this);
        }

        void attach() {
            SurfaceTexture surfaceTexture;
            if (OverlayService.this.feed == null || (surfaceTexture = this.st) == null || this.added) {
                return;
            }
            surfaceTexture.setDefaultBufferSize(1920, 1080);
            this.surface = new Surface(this.st);
            this.added = OverlayService.this.feed.addSurface(this.surface, this.index);
            Log.i(OverlayService.TAG, "hazard panel index=" + this.index + " add=" + this.added);
            applyRotation();
        }

        void detach() {
            if (OverlayService.this.feed != null && this.surface != null && this.added) {
                OverlayService.this.feed.rmSurface(this.surface, this.index);
            }
            this.added = false;
            this.surface = null;
        }

        void applyRotation() {
            int width = this.view.getWidth();
            int height = this.view.getHeight();
            if (width == 0 || height == 0) {
                return;
            }
            float f = width;
            float f2 = height;
            Matrix matrixRotationMatrix = OverlayService.rotationMatrix(new float[]{0.0f, 0.0f, f, 0.0f, f, f2, 0.0f, f2}, width, height, OverlayService.this.rotationForRole(this.role));
            float fFineForRole = OverlayService.this.fineForRole(this.role);
            if (fFineForRole != 0.0f) {
                matrixRotationMatrix.postRotate(fFineForRole, f / OverlayService.MAX_SCALE, f2 / OverlayService.MAX_SCALE);
            }
            float[] fArrCropForRole = OverlayService.this.cropForRole(this.role);
            if (fArrCropForRole != null) {
                float f3 = fArrCropForRole[2];
                float f4 = fArrCropForRole[0];
                float f5 = (f3 - f4) * f;
                float f6 = fArrCropForRole[3];
                float f7 = fArrCropForRole[1];
                float f8 = (f6 - f7) * f2;
                if (f5 > 1.0f && f8 > 1.0f) {
                    matrixRotationMatrix.postTranslate((-f4) * f, (-f7) * f2);
                    matrixRotationMatrix.postScale(f / f5, f2 / f8, 0.0f, 0.0f);
                }
            }
            if (OverlayService.this.mirrorForRole(this.role)) {
                matrixRotationMatrix.postScale(-1.0f, 1.0f, f / OverlayService.MAX_SCALE, f2 / OverlayService.MAX_SCALE);
            }
            if (OverlayService.this.flipVForRole(this.role)) {
                matrixRotationMatrix.postScale(1.0f, -1.0f, f / OverlayService.MAX_SCALE, f2 / OverlayService.MAX_SCALE);
            }
            this.view.setTransform(matrixRotationMatrix);
            this.view.invalidate();
        }

        @Override // android.view.TextureView.SurfaceTextureListener
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i2) {
            this.st = surfaceTexture;
            if (!OverlayService.directMode || OverlayService.this.pendingSide == 0) {
                return;
            }
            OverlayService.this.ensureFeed();
            attach();
        }

        @Override // android.view.TextureView.SurfaceTextureListener
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i2) {
            applyRotation();
        }

        @Override // android.view.TextureView.SurfaceTextureListener
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            detach();
            this.st = null;
            return true;
        }
    }

    private Notification notification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager = (NotificationManager) getSystemService("notification");
            if (notificationManager.getNotificationChannel(CH) == null) {
                notificationManager.createNotificationChannel(new NotificationChannel(CH, "BYD LaneWatch", 2));
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CH);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle("BYD LaneWatch").setContentText("Watching turn signal").setSmallIcon(R.drawable.ic_menu_camera).setOngoing(true).build();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView createWheelBadge(String text, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(10f);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xB00F172A);
        bg.setCornerRadius(dpToPx(6));
        tv.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = gravity;
        int m = dpToPx(8);
        lp.setMargins(m, m, m, m);
        tv.setLayoutParams(lp);
        return tv;
    }

    public void toggleDualView() {
        this.isDualView = !this.isDualView;
        if (this.pendingSide != 0) {
            show(this.pendingSide);
        }
    }

    public static boolean isRearviewShowing() {
        OverlayService instance = sInstance;
        return instance != null && instance.pendingSide == 4 && !isOemCameraForeground;
    }

    public static boolean isBevShowing() {
        OverlayService instance = sInstance;
        return instance != null && instance.pendingSide == 5 && !isOemCameraForeground;
    }

    public void toggleBev() {
        if (this.pendingSide == 5 && !isOemCameraForeground) {
            this.isBevActive = false;
            if (this.bevAutomation != null) {
                this.bevAutomation.setManualActive(false);
            }
            hide();
        } else {
            if (this.bevAutomation != null) {
                this.bevAutomation.setManualActive(true);
            }
            showBev();
        }
    }

    public void setSteeringWheelSide(int side) {
        this.steeringWheelSide = side;
    }

    public void showBev() {
        this.isBevActive = true;
        if (this.steeringWheelSide == 0) {
            this.steeringWheelSide = 1;
        }
        show(5);
    }

    public void toggleRearview() {
        if (this.pendingSide == 4 && !isOemCameraForeground) {
            this.isRearviewActive = false;
            this.userDismissedRear = true;
            hide();
        } else {
            this.isRearviewActive = true;
            this.userDismissedRear = false;
            show(4);
        }
    }

    public void toggleSide() {
        toggleDualView();
    }

    private void ensureButtonOnTop() {
        // btnSwitchCam is added after root in buildOverlay(), so it stays above root without costly removeView/addView IPC
    }

    public static void setOemCameraForeground(final boolean active) {
        final OverlayService instance = sInstance;
        if (instance == null) return;
        instance.main.post(new Runnable() {
            @Override
            public void run() {
                instance.onOemCameraStateChanged(active);
            }
        });
    }

    public static void setReverseGearActive(final boolean active) {
        // Reverse gear suppression removed per user preference
    }

    private void onOemCameraStateChanged(boolean active) {
        if (isOemCameraForeground == active) return;
        isOemCameraForeground = active;
        updateSuppressionState();
    }

    private void updateSuppressionState() {
        boolean shouldSuppress = isOemCameraForeground;
        if (shouldSuppress) {
            this.main.removeCallbacks(this.feedHealthCheckRunnable);
            if (this.glView != null) {
                this.glView.setActiveOnScreen(false);
                this.glView.resetWarmState();
            }
            if (this.panoView != null) {
                this.panoView.setActiveOnScreen(false);
            }
            parkWindowOffscreen();
            if (this.btnSwitchCam != null) {
                this.btnSwitchCam.setVisibility(View.GONE);
            }
            detachGl();
            stopFeed();
            Log.i(TAG, "Suppressed PiP & released camera HAL for OEM 360 app");
        } else {
            if (directMode && this.surfaceTexture != null) {
                ensureFeed();
                if (this.feed != null) {
                    this.feed.setFps(1);
                }
                syncGl();
            }
            if (this.isRearviewActive && !this.userDismissedRear) {
                show(4);
                Log.i(TAG, "Restored Digital Rearview Mirror after Vehicle Image OEM camera closed");
            } else if (this.bevAutomation != null && this.bevAutomation.isBevRequested()) {
                show(5);
                Log.i(TAG, "Restored BEV after Vehicle Image OEM camera closed");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateButtonPosition() {
        if (this.lp == null) {
            return;
        }
        if (this.btnSwitchCam != null && this.btnLp != null) {
            int btnW = this.btnSwitchCam.getWidth();
            if (btnW <= 0) {
                btnW = dpToPx(85);
            }
            this.btnLp.width = -2;
            this.btnLp.height = dpToPx(34);
            if (this.pendingSide == 1 && !this.isDualView) {
                this.btnLp.x = this.lp.x + dpToPx(8);
            } else {
                this.btnLp.x = (this.lp.x + this.lp.width) - btnW - dpToPx(8);
            }
            this.btnLp.y = this.lp.y + dpToPx(8);
            try {
                this.wm.updateViewLayout(this.btnSwitchCam, this.btnLp);
            } catch (Throwable unused) {
            }
        }

    }

    private void updateSwitchButtonText() {
        if (this.btnSwitchCam == null) {
            return;
        }
        this.btnSwitchCam.setVisibility(0);
        if (this.isDualView) {
            this.btnSwitchCam.setText("− มุมเดี่ยว");
        } else if (this.pendingSide == 1) {
            this.btnSwitchCam.setText("+ ขวา");
        } else if (this.pendingSide == 2) {
            this.btnSwitchCam.setText("+ ซ้าย");
        } else {
            this.btnSwitchCam.setText("+ 2 กล้อง");
        }
        updateButtonPosition();
    }

    private void handleAdasAlert(boolean alertLeft, boolean alertRight) {
        // No auto-popup when driving straight: only update red layer if PIP is already showing
        updateBlindSpotViews(this.pendingSide);
    }

    private void startPulse(View view, boolean isLeft) {
        if (isLeft) {
            if (this.pulseAnimLeft != null && this.pulseAnimLeft.isRunning()) return;
            this.pulseAnimLeft = android.animation.ObjectAnimator.ofFloat(view, "alpha", 0.05f, 1.0f);
            this.pulseAnimLeft.setDuration(260);
            this.pulseAnimLeft.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            this.pulseAnimLeft.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            this.pulseAnimLeft.start();
        } else {
            if (this.pulseAnimRight != null && this.pulseAnimRight.isRunning()) return;
            this.pulseAnimRight = android.animation.ObjectAnimator.ofFloat(view, "alpha", 0.05f, 1.0f);
            this.pulseAnimRight.setDuration(260);
            this.pulseAnimRight.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            this.pulseAnimRight.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            this.pulseAnimRight.start();
        }
    }

    private void stopPulse(View view, boolean isLeft) {
        if (isLeft) {
            if (this.pulseAnimLeft != null) {
                this.pulseAnimLeft.cancel();
                this.pulseAnimLeft = null;
            }
        } else {
            if (this.pulseAnimRight != null) {
                this.pulseAnimRight.cancel();
                this.pulseAnimRight = null;
            }
        }
        view.setAlpha(1.0f);
    }

    public boolean isPipShowing() {
        if (this.pendingSide == 0 || isOemCameraForeground || this.root == null || this.lp == null) {
            return false;
        }
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            if (this.lp.x >= dm.widthPixels || this.lp.x < -1000) {
                return false;
            }
        } catch (Throwable unused) {
            return false;
        }
        return true;
    }

    public static volatile boolean testAdasAlertLeft = false;
    public static volatile boolean testAdasAlertRight = false;
    private Runnable testAdasRunnable = null;

    public void testAdasAlert(final boolean left, final boolean right, long durationMs) {
        testAdasAlertLeft = left;
        testAdasAlertRight = right;
        handleAdasAlert(left, right);
        if (testAdasRunnable != null) {
            this.main.removeCallbacks(testAdasRunnable);
        }
        testAdasRunnable = new Runnable() {
            @Override
            public void run() {
                testAdasAlertLeft = false;
                testAdasAlertRight = false;
                handleAdasAlert(false, false);
            }
        };
        this.main.postDelayed(testAdasRunnable, durationMs);
    }

    private void updateBlindSpotViews(int side) {
        if (this.blindSpotLeftView == null || this.blindSpotRightView == null) return;

        // ถ้า PIP ไม่ได้เปิดอยู่บนหน้าจอ (หรือจอถูกซ่อนอยู่นอกจอ) -> ห้ามแสดงผลเด็ดขาดแม้จะตรวจจับสัญญาณได้ก็ตาม
        if (!isPipShowing() || side == 0) {
            this.blindSpotLeftView.setVisibility(View.GONE);
            this.blindSpotRightView.setVisibility(View.GONE);
            stopPulse(this.blindSpotLeftView, true);
            stopPulse(this.blindSpotRightView, false);
            return;
        }

        boolean alertLeft = (this.adasMonitor != null && this.adasMonitor.isLeftAlert()) || testAdasAlertLeft;
        boolean alertRight = (this.adasMonitor != null && this.adasMonitor.isRightAlert()) || testAdasAlertRight;
        boolean alertAny = (this.adasMonitor != null && this.adasMonitor.isAnyAlert());
        boolean isDualMode = this.isDualView;
        int activeTurnSignal = (this.listener != null) ? this.listener.getState() : 0;

        // 1. เลเยอร์สีแดงฝั่งซ้าย: แสดงเฉพาะเมื่อได้รับสัญญาณเตือนจริง (alertLeft หรือ alertAny เมื่อเปิดกล้อง/ไฟเลี้ยวซ้าย)
        boolean leftAllowed = (side == 1 || side == 21 || activeTurnSignal == 1 || isDualMode || side == 3 || side == 11 || (side == 5 && this.steeringWheelSide == 1));
        boolean showLeftRed = leftAllowed && (alertLeft || alertAny);
        if (showLeftRed) {
            this.blindSpotLeftView.setVisibility(View.VISIBLE);
            this.blindSpotLeftView.bringToFront();
            if (this.root != null) {
                this.root.bringChildToFront(this.blindSpotLeftView);
            }
            startPulse(this.blindSpotLeftView, true);
        } else {
            this.blindSpotLeftView.setVisibility(View.GONE);
            stopPulse(this.blindSpotLeftView, true);
        }

        // 2. เลเยอร์สีแดงฝั่งขวา: แสดงเฉพาะเมื่อได้รับสัญญาณเตือนจริง (alertRight หรือ alertAny เมื่อเปิดกล้อง/ไฟเลี้ยวขวา)
        boolean rightAllowed = (side == 2 || side == 22 || activeTurnSignal == 2 || isDualMode || side == 3 || side == 12 || (side == 5 && this.steeringWheelSide == 2));
        boolean showRightRed = rightAllowed && (alertRight || alertAny);
        if (showRightRed) {
            this.blindSpotRightView.setVisibility(View.VISIBLE);
            this.blindSpotRightView.bringToFront();
            if (this.root != null) {
                this.root.bringChildToFront(this.blindSpotRightView);
            }
            startPulse(this.blindSpotRightView, false);
        } else {
            this.blindSpotRightView.setVisibility(View.GONE);
            stopPulse(this.blindSpotRightView, false);
        }
    }

    @Override // android.app.Service
    public void onDestroy() {
        sInstance = null;
        if (this.adasMonitor != null) {
            this.adasMonitor.stop();
            this.adasMonitor = null;
        }
        TurnSignalListener turnSignalListener = this.listener;
        if (turnSignalListener != null) {
            turnSignalListener.stop();
        }
        stopFeed();
        AvcClient avcClient = this.avc;
        if (avcClient != null) {
            avcClient.unbind();
        }
        try {
            if (this.btnSwitchCam != null) {
                this.wm.removeView(this.btnSwitchCam);
            }
        } catch (Throwable unused) {
        }
        try {
            FrameLayout frameLayout = this.root;
            if (frameLayout != null) {
                this.wm.removeView(frameLayout);
            }
        } catch (Throwable unused) {
        }
        super.onDestroy();
    }
}
