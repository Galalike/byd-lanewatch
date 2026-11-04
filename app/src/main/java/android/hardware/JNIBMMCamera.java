package android.hardware;

import android.graphics.SurfaceTexture;
import android.hardware.watermark.JNIWatermarkRender;
import android.media.MediaCodec;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class JNIBMMCamera {
    private static final String TAG = "J-JNIBMMCamera";
    private IJNIEventCallback mEventCallback;
    private long mNativeReference;
    private IJNIPreviewCallback mPreviewCallback;
    private Object mRender;

    public interface IJNIEventCallback {
        void onEvent(JNIBMMCamera jNIBMMCamera, int i, int i2, int i3);
    }

    public interface IJNIPreviewCallback {
        void onPreview(JNIBMMCamera jNIBMMCamera, ByteBuffer byteBuffer, int i, int i2, int i3, int i4, int i5, long j);

        void onPreview(JNIBMMCamera jNIBMMCamera, byte[] bArr, int i, int i2, int i3, int i4, int i5, long j);
    }

    private native int native_setup(JNIBMMCamera jNIBMMCamera, int i, String str);

    private native int native_setup_render(JNIBMMCamera jNIBMMCamera, JNIWatermarkRender jNIWatermarkRender, int i, String str);

    public native boolean nativeAddPreviewCallbackBuffer(ByteBuffer byteBuffer, int i);

    public native boolean nativeAddPreviewSurface(Surface surface, int i);

    public native boolean nativeAddTexture(SurfaceTexture surfaceTexture, int i);

    public native boolean nativeClearPreviewCallbackBuffer(int i);

    public native boolean nativeClose();

    public native boolean nativeDisablePreviewCallback(int i);

    public native boolean nativeEnablePreviewCallback(int i);

    public native boolean nativeEnablePreviewCallbackWithBuffer(int i);

    public native int nativeGetCameraId();

    public native int nativeGetLogLevel();

    public native int nativeGetPreviewHeight();

    public native int nativeGetPreviewWidth();

    public native boolean nativeIsOpen();

    public native boolean nativeIsPreview();

    public native boolean nativeOpen(int i);

    public native boolean nativeRmPreviewSurface(Surface surface, int i);

    public native boolean nativeRmTexture(SurfaceTexture surfaceTexture, int i);

    public native boolean nativeSetCameraFPS(int i);

    public native boolean nativeSetDisplayOrientation(Surface surface, int i);

    public native void nativeSetLogLevel(int i);

    public native boolean nativeSetMediaCodec(MediaCodec mediaCodec, int i);

    public native boolean nativeSetMediaCodecFPS(MediaCodec mediaCodec, int i);

    public native boolean nativeSetPreviewSize(int i, int i2);

    public native boolean nativeSetPreviewSurface(Surface surface, int i);

    public native boolean nativeSetTexture(SurfaceTexture surfaceTexture, int i);

    public native boolean nativeStartPreview();

    public native boolean nativeStopPreview();

    static {
        System.loadLibrary("bmmcamera_jni");
    }

    public JNIBMMCamera(int i) {
        Log.d(TAG, "JNIBMMCamera: camera id is " + i);
        native_setup(this, i, "JNIBMMCamera-java");
    }

    private static void postByteArrayFromNative(Object obj, byte[] bArr, int i, int i2, int i3, int i4, int i5, long j) {
        IJNIPreviewCallback iJNIPreviewCallback;
        JNIBMMCamera jNIBMMCamera = (JNIBMMCamera) obj;
        if (jNIBMMCamera == null || (iJNIPreviewCallback = jNIBMMCamera.mPreviewCallback) == null) {
            return;
        }
        iJNIPreviewCallback.onPreview(jNIBMMCamera, bArr, i, i2, i3, i4, i5, j);
    }

    private static void postByteBufferFromNative(Object obj, ByteBuffer byteBuffer, int i, int i2, int i3, int i4, int i5, long j) {
        JNIBMMCamera jNIBMMCamera = (JNIBMMCamera) obj;
        if (jNIBMMCamera == null) {
            return;
        }
        IJNIPreviewCallback iJNIPreviewCallback = jNIBMMCamera.mPreviewCallback;
        if (iJNIPreviewCallback != null) {
            iJNIPreviewCallback.onPreview(jNIBMMCamera, byteBuffer, i, i2, i3, i4, i5, j);
        }
        if (byteBuffer != null) {
            byteBuffer.clear();
        }
    }

    private static void postEventFromNative(Object obj, int i, int i2, int i3) {
        IJNIEventCallback iJNIEventCallback;
        JNIBMMCamera jNIBMMCamera = (JNIBMMCamera) obj;
        if (jNIBMMCamera == null || (iJNIEventCallback = jNIBMMCamera.mEventCallback) == null) {
            return;
        }
        iJNIEventCallback.onEvent(jNIBMMCamera, i, i2, i3);
    }

    public void setEventCallback(IJNIEventCallback iJNIEventCallback) {
        this.mEventCallback = iJNIEventCallback;
    }

    public void setPreviewCallback(IJNIPreviewCallback iJNIPreviewCallback) {
        this.mPreviewCallback = iJNIPreviewCallback;
    }
}
