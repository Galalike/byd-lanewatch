package com.pi.vehiclepano;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import java.io.IOException;

/* loaded from: /tmp/autovideo_dex/classes2.dex */
public class PanoAPI extends HandlerThread implements Handler.Callback, SurfaceTexture.OnFrameAvailableListener {
    public static final int CALIBRATION_ERROR_CODE_BAD_PARAM = 1;
    public static final int CALIBRATION_ERROR_CODE_FAILED_BACK_IMAGE = 3;
    public static final int CALIBRATION_ERROR_CODE_FAILED_FRONT_IMAGE = 2;
    public static final int CALIBRATION_ERROR_CODE_FAILED_LEFT_IMAGE = 4;
    public static final int CALIBRATION_ERROR_CODE_FAILED_RIGHT_IMAGE = 5;
    public static final int CALIBRATION_SUCCESS = 0;
    public static final int CAMERA_TYPE_NORMAL = 1;
    public static final int CAMERA_TYPE_X1F = 2;
    public static final int DOOR_LEFT_FORNT = 1;
    public static final int DOOR_LEFT_REAR = 3;
    public static final int DOOR_RIGHT_FORNT = 2;
    public static final int DOOR_RIGHT_REAR = 4;
    public static final int DOOR_THEHOOD = 0;
    public static final int DOOR_TRUNK = 5;
    public static final int LANDSCAPE_3D_FRONT = 16;
    public static final int LANDSCAPE_3D_FRONTLEFT = 18;
    public static final int LANDSCAPE_3D_FRONTRIGHT = 19;
    public static final int LANDSCAPE_3D_REAR = 17;
    public static final int LANDSCAPE_3D_REARLEFT = 20;
    public static final int LANDSCAPE_3D_REARRIGHT = 21;
    public static final int LANDSCAPE_3D_TOP = 22;
    public static final int LANDSCAPE_FRONT = 0;
    public static final int LANDSCAPE_FRONT120 = 6;
    public static final int LANDSCAPE_FRONTLR = 4;
    public static final int LANDSCAPE_LEFT = 2;
    public static final int LANDSCAPE_LR = 8;
    public static final int LANDSCAPE_REAR = 1;
    public static final int LANDSCAPE_REAR120 = 7;
    public static final int LANDSCAPE_REARLR = 5;
    public static final int LANDSCAPE_RIGHT = 3;
    public static final int LANDSCAPE_WIDE_FRONT = 32;
    public static final int LANDSCAPE_WIDE_LEFT = 34;
    public static final int LANDSCAPE_WIDE_REAR = 33;
    public static final int LANDSCAPE_WIDE_RIGHT = 35;
    public static final int LIGHT_CARGO_LIGHT_SIGNAL = 16;
    public static final int LIGHT_DAYTIME_RUNNING = 2;
    public static final int LIGHT_DAYTIME_RUNNING_LEFT = 12;
    public static final int LIGHT_DAYTIME_RUNNING_RIGHT = 13;
    public static final int LIGHT_DIPPED_HEADLIGHT = 0;
    public static final int LIGHT_EMERGENCY_FLASHERS = 10;
    public static final int LIGHT_FOG = 3;
    public static final int LIGHT_FOG_REAR = 9;
    public static final int LIGHT_HIGH_BEAM = 1;
    public static final int LIGHT_OVER = 15;
    public static final int LIGHT_REARLOGO_LIGHT = 17;
    public static final int LIGHT_REARSIDE = 18;
    public static final int LIGHT_REVERSING = 8;
    public static final int LIGHT_SIDE = 11;
    public static final int LIGHT_STOP_LAMP = 6;
    public static final int LIGHT_TAIL_LAMP = 7;
    public static final int LIGHT_TURN_LEFT = 4;
    public static final int LIGHT_TURN_RIGHT = 5;
    public static final int LIGHT_WELCOME = 14;
    private static final int MSG_PIPANO_CALIBRATION = 8;
    private static final int MSG_PIPANO_CHANGE_CAR_MODEL = 7;
    private static final int MSG_PIPANO_FRAME_AVAILABLE = 13;
    private static final int MSG_PIPANO_INIT = 1;
    private static final int MSG_PIPANO_ONE_FRAME = 14;
    private static final int MSG_PIPANO_RELEASE = 4;
    private static final int MSG_PIPANO_SET_PREVIEW_SURFACE = 6;
    public static final int PORTRAIT_LEFT_3D_FRONT = 4624;
    public static final int PORTRAIT_LEFT_3D_FRONTLEFT = 4626;
    public static final int PORTRAIT_LEFT_3D_FRONTRIGHT = 4627;
    public static final int PORTRAIT_LEFT_3D_REAR = 4625;
    public static final int PORTRAIT_LEFT_3D_REARLEFT = 4628;
    public static final int PORTRAIT_LEFT_3D_REARRIGHT = 4629;
    public static final int PORTRAIT_LEFT_3D_TOP = 4630;
    public static final int PORTRAIT_LEFT_FRONT = 4608;
    public static final int PORTRAIT_LEFT_FRONTLR = 4612;
    public static final int PORTRAIT_LEFT_REAR = 4609;
    public static final int PORTRAIT_LEFT_REARLR = 4613;
    public static final int PORTRAIT_RIGHT_FRONT = 4864;
    public static final int PORTRAIT_RIGHT_FRONT120 = 4870;
    public static final int PORTRAIT_RIGHT_FRONTLR = 4868;
    public static final int PORTRAIT_RIGHT_LR = 4872;
    public static final int PORTRAIT_RIGHT_REAR = 4865;
    public static final int PORTRAIT_RIGHT_REAR120 = 4871;
    public static final int PORTRAIT_RIGHT_REARLR = 4869;
    public static final int PORTRAIT_RIGH_3D_FRONT = 4880;
    public static final int PORTRAIT_RIGH_3D_FRONTLEFT = 4882;
    public static final int PORTRAIT_RIGH_3D_FRONTRIGHT = 4883;
    public static final int PORTRAIT_RIGH_3D_REAR = 4881;
    public static final int PORTRAIT_RIGH_3D_REARLEFT = 4884;
    public static final int PORTRAIT_RIGH_3D_REARRIGHT = 4885;
    public static final int PORTRAIT_RIGH_3D_TOP = 4886;
    public static final int PORTRALT_LEFT_FRONT120 = 4614;
    public static final int PORTRALT_LEFT_LR = 4616;
    public static final int PORTRALT_LEFT_REAR120 = 4615;
    public static final int RADAR_AREA_FRONT_LEFT = 1;
    public static final int RADAR_AREA_FRONT_MIDDLE_LEFT = 7;
    public static final int RADAR_AREA_FRONT_MIDDLE_RIGHT = 8;
    public static final int RADAR_AREA_FRONT_RIGHT = 2;
    public static final int RADAR_AREA_LEFT_REAR = 3;
    public static final int RADAR_AREA_MIDDLE_REAR = 9;
    public static final int RADAR_AREA_REAR_MIDDLE_LEFT = 5;
    public static final int RADAR_AREA_REAR_MIDDLE_RIGHT = 6;
    public static final int RADAR_AREA_RIGHT_REAR = 4;
    public static final int RADAR_PROBE_STATE_ABNORMAL = 0;
    public static final int RADAR_PROBE_STATE_GREEN = 2;
    public static final int RADAR_PROBE_STATE_RED = 4;
    public static final int RADAR_PROBE_STATE_SAFE = 1;
    public static final int RADAR_PROBE_STATE_YELLOW = 3;
    private static final String TAG = "VehiclePanoAPI";
    private static int callbackCode;
    private static On3DViewpointChangeListener mOn3DViewpointChangeListener;
    private SurfaceTexture mCameraTexture;
    private int mDebugType;
    private GestureDetector mGestureDetector;
    private Handler mHandler;
    private volatile boolean mHasInit;
    private final Object mLock;
    private long mNativeContext;
    private PictureCallback mPictureCallback;
    private int mViewPoint;
    private OnDrawFrameCallbackListener onDrawFrameCallbackListener;
    private OnErrorListener onErrorListener;
    public static String PanoApiVersion = "202411011131V1.4";
    private static boolean mFreeDisplay = false;

    /* loaded from: /tmp/autovideo_dex/classes2.dex */
    public interface On3DViewpointChangeListener {
        void on3DViewPointChange(int i);
    }

    /* loaded from: /tmp/autovideo_dex/classes2.dex */
    public interface OnDrawFrameCallbackListener {
        int onDrawFrameCallback(int i);
    }

    /* loaded from: /tmp/autovideo_dex/classes2.dex */
    public interface OnErrorListener {
        int onError(int i);
    }

    /* loaded from: /tmp/autovideo_dex/classes2.dex */
    public interface PictureCallback {
        void onPictureTaken(byte[] bArr);
    }

    private native void changeCarModel(String str);

    private native void createNativeObj(boolean z);

    private native void doCalibration();

    /* JADX INFO: Access modifiers changed from: private */
    public native void doubleTap();

    /* JADX INFO: Access modifiers changed from: private */
    public native void down(float f, float f2);

    private native int drawPreviewFrame(int i);

    /* JADX INFO: Access modifiers changed from: private */
    public native void fling(float f, float f2);

    private native boolean initDisplayRsa(Context context, Surface surface);

    private native void multipleFingers(int i);

    private native void release();

    private native void saveTransparentBody(int i);

    /* JADX INFO: Access modifiers changed from: private */
    public native void scroll(float f, float f2);

    private native void setPreviewSurface(Surface surface);

    private native void up(float f, float f2);

    public native float[] getCarPosition();

    public native void initialize(String str, int i, int i2, String str2, Context context);

    public native void refreshBodyMesh(String str);

    public native void refreshWheelMesh(String str);

    public native void set3dFov(float f);

    public native void setAngleAndDirection(int i, boolean z);

    public native void setAutomaticCalibrationStr(String str);

    public native void setCameraType(int i, int i2);

    public native void setCarBodyColor(float f, float f2, float f3, float f4, float f5);

    public native void setCarResources(String str, String str2, String str3);

    public native void setCarResources(String str, String str2, String str3, String str4);

    public native void setCarTransparent(float f);

    public native void setCountryCode(String str);

    public native void setDebugDeltaTime(long j);

    public native void setDoorStatus(int i, int i2);

    public native void setEnableTimestamp(boolean z);

    public native void setEnvTexRotation(float f, float f2, float f3);

    public native void setFrontWheelGuideLine(boolean z, double d);

    public native void setFrontWheelGuideLineWithTimestamp(boolean z, byte[] bArr);

    public native void setLightDir(int i, float f, float f2);

    public native void setLightStatus(int i, int i2);

    public native void setRadarAlertDrawable(int i, int i2);

    public native void setRadarDistance(int[] iArr);

    public native void setRadarDistanceDrawable(int i, int i2);

    public native void setRearWheelGuideLine(boolean z, double d);

    public native void setRearWheelGuideLineWithTimestamp(boolean z, byte[] bArr);

    public native void setSpeed(float f);

    public native void setTimeMarkFontSize(int i);

    public native void setTimeMarkPosition(int i, int i2);

    public native void setTrailerLine(boolean z);

    public native void setVehicleWheelPulse(byte[] bArr);

    public native void setViewpoint(int i);

    public native void setWheelDirectionChanged(byte[] bArr);

    public native void setWheelSpeedChanged(byte[] bArr);

    private static void loadOemLibrary(String name) {
        String directory = com.nineoad.bydsignpicinpic.overlay.OemPanoProfile.nativeDirectory();
        java.io.File library = directory == null ? null : new java.io.File(directory, "lib" + name + ".so");
        if (library != null && library.canRead()) {
            try {
                System.load(library.getAbsolutePath());
                Log.i(TAG, "Loaded OEM native: " + library);
            } catch (UnsatisfiedLinkError failure) {
                // The shipped PI adapter links the project's unwind compatibility shim.
                // Do not modify the OEM binary or its installed dependencies.
                if (!"VehiclePano".equals(name) || !failure.getMessage().contains("_Unwind_Resume")) throw failure;
                Log.w(TAG, "OEM PI requires unwind compatibility; using bundled PI adapter", failure);
                System.loadLibrary(name);
            }
        } else if ("VehiclePano".equals(name) && directory != null) {
            throw new UnsatisfiedLinkError("OEM VehiclePano library unavailable");
        } else {
            System.loadLibrary(name);
        }
    }

    static {
        try {
            System.loadLibrary("unwind_shim");
        } catch (Throwable t) {
            Log.w(TAG, "unwind_shim load: " + t);
        }
        try {
            loadOemLibrary("c++_shared");
        } catch (Throwable t) {
            Log.w(TAG, "c++_shared load: " + t);
        }
        try {
            loadOemLibrary("opencv_java4");
        } catch (Throwable t) {
            Log.w(TAG, "opencv_java4 load: " + t);
        }
        try {
            loadOemLibrary("CarAutoCorrect");
        } catch (Throwable t) {
            Log.w(TAG, "CarAutoCorrect load: " + t);
        }
        try {
            loadOemLibrary("VehiclePano");
            Log.i(TAG, "VehiclePano loaded successfully");
        } catch (Throwable t) {
            Log.e(TAG, "VehiclePano load failed: " + t);
            throw new ExceptionInInitializerError(t);
        }
    }

    public void setDebugType(int type) {
        this.mDebugType = type;
    }

    public PanoAPI() {
        super("UpdateFrameTask");
        this.mLock = new Object();
        this.mDebugType = 0;
        this.mViewPoint = 0;
        start();
        Log.d(TAG, "PanoAPI start vehiclePano Jar Version:" + PanoApiVersion);
        this.mGestureDetector = new GestureDetector((Context) null, new GestureDetector.SimpleOnGestureListener() { // from class: com.pi.vehiclepano.PanoAPI.1
            @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnGestureListener
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                PanoAPI.this.scroll(distanceX, distanceY);
                return false;
            }

            @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnGestureListener
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                PanoAPI.this.fling(velocityX, velocityY);
                return false;
            }

            @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnGestureListener
            public boolean onDown(MotionEvent e) {
                PanoAPI.this.down(e.getX(), e.getY());
                return false;
            }

            @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnDoubleTapListener
            public boolean onDoubleTap(MotionEvent e) {
                PanoAPI.this.doubleTap();
                return false;
            }
        }, new Handler(Looper.getMainLooper()));
        this.mHasInit = false;
        this.mHandler = new Handler(getLooper(), this);
        sendHandleMessage(1, null);
        synchronized (this.mLock) {
            while (!this.mHasInit) {
                Log.d(TAG, "UpdateFrameTask Thread ID:" + Thread.currentThread().getId());
                Log.d(TAG, "UpdateFrameTask Thread getThreadGroup:" + Thread.currentThread().getThreadGroup());
                Log.d(TAG, "UpdateFrameTask Thread Name:" + Thread.currentThread().getName());
                try {
                    this.mLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setCarModel(String carModeName) {
        this.mHandler.obtainMessage(7, carModeName).sendToTarget();
    }

    public boolean initDisplay(Context context, Surface surface) throws IOException {
        Log.d(TAG, "initDisplay start");
        if (surface == null) {
            Log.e(TAG, "initDisplay surface is null");
            return false;
        }
        synchronized (this.mLock) {
            mFreeDisplay = true;
            sendHandleMessage(6, surface);
        }
        Log.d(TAG, "initDisplay end");
        return true;
    }

    public void freeDisplay() throws IOException {
        Log.d(TAG, "freeDisplay");
        synchronized (this.mLock) {
            mFreeDisplay = false;
            setPreviewSurface(null);
        }
    }

    public void terminate() {
        Log.d(TAG, "terminate");
        mFreeDisplay = false;
        sendHandleMessage(4, null);
    }

    public SurfaceTexture getCameraSurfaceTexture() {
        Log.d(TAG, "getCameraSurfaceTexture");
        if (this.mCameraTexture == null) {
            Log.e(TAG, "getCameraSurfaceTexture is null");
        }
        return this.mCameraTexture;
    }

    public void setOn3DViewpointChangeListener(On3DViewpointChangeListener listener) {
        mOn3DViewpointChangeListener = listener;
    }

    private static void on3DViewpointChange(int viewpoint) {
        On3DViewpointChangeListener on3DViewpointChangeListener = mOn3DViewpointChangeListener;
        if (on3DViewpointChangeListener != null) {
            on3DViewpointChangeListener.on3DViewPointChange(viewpoint);
        }
    }

    public void doAutomaticCalibration(OnErrorListener onerrorListener) throws IOException {
        Log.d(TAG, "doAutomaticCalibration start");
        this.onErrorListener = onerrorListener;
        sendHandleMessage(8, null);
    }

    public int onError(int code) {
        Log.i(TAG, "onError code:" + code);
        OnErrorListener onErrorListener = this.onErrorListener;
        if (onErrorListener != null) {
            onErrorListener.onError(code);
            return 0;
        }
        return 0;
    }

    private int onDrawFrameCallback(int code) throws IOException {
        Log.i(TAG, "onDrawFrameCallbackListener code:" + code);
        OnDrawFrameCallbackListener onDrawFrameCallbackListener = this.onDrawFrameCallbackListener;
        if (onDrawFrameCallbackListener != null) {
            onDrawFrameCallbackListener.onDrawFrameCallback(code);
            return 0;
        }
        return 0;
    }

    public void onDrawFrameCallbakc(OnDrawFrameCallbackListener onDrawFrameCallbackListener) {
        this.onDrawFrameCallbackListener = onDrawFrameCallbackListener;
    }

    public int getDrawFramwCallback() {
        return callbackCode;
    }

    public int takePicture(int viewpoint, byte[] data) {
        Log.i(TAG, "takePicture:" + viewpoint);
        PictureCallback pictureCallback = this.mPictureCallback;
        if (pictureCallback != null) {
            pictureCallback.onPictureTaken(data);
            return -1;
        }
        return 0;
    }

    public void onTakePicture(int viewpoint, PictureCallback callback) {
        this.mPictureCallback = callback;
        this.mViewPoint = viewpoint;
    }

    public void setFrontWheelGuideLine(boolean visible, byte[] angle) {
        setFrontWheelGuideLineWithTimestamp(visible, angle);
    }

    public void setRearWheelGuideLine(boolean visible, byte[] angle) {
        setRearWheelGuideLineWithTimestamp(visible, angle);
    }

    public void saveTransparent(int status) throws IOException {
        if (status == 2) {
            freeDisplay();
        } else {
            saveTransparentBody(status);
        }
    }

    public int getPanoType() {
        return -1;
    }

    public void setPanoType(int mPanoType) {
    }

    public void onTouchEvent(MotionEvent event) {
        if (event.getAction() == 1) {
            up(event.getX(), event.getY());
        } else if (event.getAction() == 5) {
            Log.i(TAG, "MotionEvent.ACTION_POINTER_DOWN");
            multipleFingers(1);
        } else if (event.getAction() == 6) {
            Log.i(TAG, "MotionEvent.ACTION_POINTER_UP");
            multipleFingers(0);
        }
        GestureDetector gestureDetector = this.mGestureDetector;
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
    }

    private boolean sendHandleMessage(int what, Object obj) {
        Message msg = new Message();
        msg.obj = obj;
        msg.arg1 = what;
        msg.what = what;
        if (this.mHandler.sendMessage(msg)) {
            return true;
        }
        if (obj == null) {
            this.mHandler.obtainMessage(what).sendToTarget();
            Log.e(TAG, "send handle message fail :" + what);
        } else {
            this.mHandler.obtainMessage(what, obj).sendToTarget();
        }
        Log.e(TAG, "send handle message fail :" + what + "and obj");
        return false;
    }

    private void drawFrame(SurfaceTexture surfaceTexture) throws IOException {
        if (mFreeDisplay) {
            SurfaceTexture surfaceTexture2 = this.mCameraTexture;
            if (surfaceTexture2 != null) {
                surfaceTexture2.updateTexImage();
            } else {
                Log.e(TAG, "CameraTextture updateTexImage is null");
            }
        }
        callbackCode = drawPreviewFrame(0);
        int i = callbackCode;
        if (i != 0 && mFreeDisplay) {
            OnDrawFrameCallbackListener onDrawFrameCallbackListener = this.onDrawFrameCallbackListener;
            if (onDrawFrameCallbackListener != null) {
                onDrawFrameCallbackListener.onDrawFrameCallback(i);
                Log.e(TAG, "MSG_PIPANO_FRAME_AVAILABLE callbackCode :" + callbackCode);
                return;
            }
            Log.e(TAG, "MSG_PIPANO_FRAME_AVAILABLE null callbackCode :" + callbackCode);
        }
    }

    @Override // android.os.Handler.Callback
    public boolean handleMessage(Message msg) {
        int i = msg.what;
        if (i == 1) {
            Log.d(TAG, "MSG_PIPANO_INIT");
            try {
                createNativeObj(false);
                synchronized (this.mLock) {
                    this.mHasInit = true;
                    this.mLock.notifyAll();
                }
                if (this.mCameraTexture == null) {
                    Log.e(TAG, "mCameraTexture == null");
                }
                this.mCameraTexture.setOnFrameAvailableListener(this);
            } catch (Exception e) {
                Log.e(TAG, "MSG_PIPANO_INIT");
                e.printStackTrace();
            }
            if (!this.mHandler.sendEmptyMessageDelayed(13, 500L)) {
                Log.e(TAG, "fail send message fail MSG_PIPANO_FRAME_AVAILABLE");
            }
        } else if (i == 4) {
            Log.d(TAG, "MSG_PIPANO_RELEASE");
            try {
                release();
            } catch (Exception e2) {
                Log.e(TAG, "MSG_PIPANO_RELEASE");
                e2.printStackTrace();
            }
            getLooper().quit();
        } else if (i == 6) {
            Log.d(TAG, "MSG_PIPANO_SET_PREVIEW_SURFACE");
            try {
                setPreviewSurface((Surface) msg.obj);
                this.mHandler.sendEmptyMessageDelayed(13, 33L);
            } catch (Exception e3) {
                Log.e(TAG, "MSG_PIPANO_SET_PREVIEW_SURFACE");
                e3.printStackTrace();
            }
        } else if (i == 7) {
            Log.e(TAG, "MSG_PIPANO_CHANGE_CAR_MODEL");
            try {
                changeCarModel((String) msg.obj);
            } catch (Exception e4) {
                Log.e(TAG, "MSG_PIPANO_CHANGE_CAR_MODEL");
                e4.printStackTrace();
            }
        } else if (i == 8) {
            Log.e(TAG, "MSG_PIPANO_CALIBRATION");
            try {
                doCalibration();
            } catch (Exception e5) {
                Log.e(TAG, "MSG_PIPANO_CALIBRATION");
                e5.printStackTrace();
            }
        } else if (i == 13) {
            int i2 = this.mDebugType;
            if (i2 != 0 && i2 == 1) {
                this.mHandler.sendEmptyMessageDelayed(13, 33L);
            }
            try {
                if (mFreeDisplay) {
                    this.mCameraTexture.updateTexImage();
                }
            } catch (Exception e6) {
                e6.printStackTrace();
            }
            try {
                callbackCode = drawPreviewFrame(0);
                if (callbackCode != 0 && mFreeDisplay && this.onDrawFrameCallbackListener != null) {
                    this.onDrawFrameCallbackListener.onDrawFrameCallback(callbackCode);
                    Log.e(TAG, "MSG_PIPANO_FRAME_AVAILABLE callbackCode :" + callbackCode);
                }
            } catch (Exception e7) {
                Log.e(TAG, "MSG_PIPANO_FRAME_AVAILABLE");
                e7.printStackTrace();
            }
        } else if (i == 14) {
            Log.d(TAG, "MSG_PIPANO_ONE_FRAME");
            try {
                if (mFreeDisplay) {
                    this.mCameraTexture.updateTexImage();
                }
            } catch (Exception e8) {
                e8.printStackTrace();
            }
            try {
                drawPreviewFrame(0);
            } catch (Exception e9) {
                Log.e(TAG, "MSG_PIPANO_ONE_FRAME");
                e9.printStackTrace();
            }
        }
        return false;
    }

    @Override // android.graphics.SurfaceTexture.OnFrameAvailableListener
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        this.mHandler.removeMessages(13);
        if (surfaceTexture == null) {
            Log.e(TAG, "surfaceTexture is null");
        }
        Message.obtain(this.mHandler, 13, surfaceTexture).sendToTarget();
    }
}
