package com.nineoad.bydsignpicinpic.camera;

import android.hardware.JNIBMMCamera;
import android.util.Log;
import android.view.Surface;

/* JADX INFO: loaded from: classes.dex */
public class CameraFeed {
    private static final String TAG = "CameraFeed";
    private JNIBMMCamera cam;
    private volatile boolean running;
    private volatile int currentFps = 25;
    private volatile int previewWidth = 1920;
    private volatile int previewHeight = 1080;
    private volatile int currentCamId = 1;
    private volatile int activeSurfaceMode = 0;

    public int getPreviewWidth() {
        return this.previewWidth;
    }

    public int getPreviewHeight() {
        return this.previewHeight;
    }

    public int getCurrentCamId() {
        return this.currentCamId;
    }

    public int getActiveSurfaceMode() {
        return this.activeSurfaceMode;
    }

    public void setFps(int fps) {
        JNIBMMCamera jNIBMMCamera = this.cam;
        if (jNIBMMCamera == null || !this.running || this.currentFps == fps) {
            return;
        }
        try {
            jNIBMMCamera.nativeSetCameraFPS(fps);
            this.currentFps = fps;
            Log.i(TAG, "setCameraFPS=" + fps);
        } catch (Throwable th) {
            Log.w(TAG, "setCameraFPS(" + fps + ") failed: " + th);
        }
    }

    public boolean start(Surface surface, int i, int i2) {
        try {
            this.currentCamId = i;
            JNIBMMCamera jNIBMMCamera = new JNIBMMCamera(i);
            this.cam = jNIBMMCamera;
            try {
                if (!jNIBMMCamera.nativeOpen(i)) {
                    Log.w(TAG, "nativeOpen(" + i + ") = false");
                    safeClose();
                    return false;
                }
                int iNativeGetPreviewWidth = this.cam.nativeGetPreviewWidth();
                int iNativeGetPreviewHeight = this.cam.nativeGetPreviewHeight();
                if (iNativeGetPreviewWidth <= 0 || iNativeGetPreviewHeight <= 0) {
                    iNativeGetPreviewWidth = 1920;
                    iNativeGetPreviewHeight = 1080;
                }
                this.previewWidth = iNativeGetPreviewWidth;
                this.previewHeight = iNativeGetPreviewHeight;
                this.cam.nativeSetPreviewSize(iNativeGetPreviewWidth, iNativeGetPreviewHeight);
                this.cam.nativeSetCameraFPS(25);
                boolean surfaceAdded = false;
                for (int mode = 0; mode <= 3; mode++) {
                    try {
                        boolean ok = this.cam.nativeAddPreviewSurface(surface, mode);
                        Log.i(TAG, "cam " + i + " addPreviewSurface mode=" + mode + " ok=" + ok + " (" + iNativeGetPreviewWidth + "x" + iNativeGetPreviewHeight + ")");
                        if (ok) {
                            this.activeSurfaceMode = mode;
                            surfaceAdded = true;
                            break;
                        }
                    } catch (Throwable thMode) {
                        Log.w(TAG, "cam " + i + " addPreviewSurface mode=" + mode + " threw: " + thMode);
                    }
                }
                if (!surfaceAdded) {
                    Log.w(TAG, "cam " + i + " nativeAddPreviewSurface failed for all modes 0..3");
                    safeClose();
                    return false;
                }
                try {
                    this.cam.nativeSetDisplayOrientation(surface, i2);
                } catch (Throwable unused) {
                }
                this.running = this.cam.nativeStartPreview();
                Log.i(TAG, "cam " + i + " startPreview=" + this.running);
                if (!this.running) {
                    safeClose();
                }
                return this.running;
            } catch (Throwable th) {
                Log.e(TAG, "start failed: " + th);
                safeClose();
                return false;
            }
        } catch (Throwable th2) {
            Log.e(TAG, "load/construct failed: " + th2);
            return false;
        }
    }

    public boolean addSurface(Surface surface, int i) {
        JNIBMMCamera jNIBMMCamera = this.cam;
        if (jNIBMMCamera == null) {
            return false;
        }
        try {
            boolean zNativeAddPreviewSurface = jNIBMMCamera.nativeAddPreviewSurface(surface, i);
            if (!zNativeAddPreviewSurface && i != this.activeSurfaceMode) {
                zNativeAddPreviewSurface = jNIBMMCamera.nativeAddPreviewSurface(surface, this.activeSurfaceMode);
            }
            try {
                this.cam.nativeSetDisplayOrientation(surface, 0);
            } catch (Throwable unused) {
            }
            Log.i(TAG, "addSurface idx=" + i + " (activeMode=" + this.activeSurfaceMode + ") ok=" + zNativeAddPreviewSurface);
            return zNativeAddPreviewSurface;
        } catch (Throwable th) {
            Log.e(TAG, "addSurface(" + i + ") failed: " + th);
            return false;
        }
    }

    public void rmSurface(Surface surface, int i) {
        JNIBMMCamera jNIBMMCamera = this.cam;
        if (jNIBMMCamera == null) {
            return;
        }
        try {
            jNIBMMCamera.nativeRmPreviewSurface(surface, i);
        } catch (Throwable unused) {
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    public void stop() {
        this.running = false;
        JNIBMMCamera jNIBMMCamera = this.cam;
        if (jNIBMMCamera != null) {
            try {
                jNIBMMCamera.nativeStopPreview();
            } catch (Throwable unused) {
            }
            safeClose();
        }
    }

    private void safeClose() {
        JNIBMMCamera jNIBMMCamera = this.cam;
        if (jNIBMMCamera != null) {
            try {
                jNIBMMCamera.nativeClose();
            } catch (Throwable unused) {
            }
            this.cam = null;
        }
    }
}
