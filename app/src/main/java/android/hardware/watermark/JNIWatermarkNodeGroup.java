package android.hardware.watermark;

import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class JNIWatermarkNodeGroup {
    private long mNativeReference;

    private static native void nativeInit();

    private native void nativeSetup(int i);

    private native void nativeUninstall();

    native boolean nativeAddFont(String str, int i, String str2, String str3);

    native boolean nativeAddNode(JNIWatermarkNode jNIWatermarkNode);

    native boolean nativeDisable();

    native boolean nativeEnable();

    native boolean nativeIsEnable();

    native boolean nativeLock();

    native boolean nativeRender(byte[] bArr, int i, int i2, int i3, long j);

    native boolean nativeRenderForDirect(ByteBuffer byteBuffer, int i, int i2, int i3, long j);

    native boolean nativeRmNode(JNIWatermarkNode jNIWatermarkNode);

    native boolean nativeSetNodeFont(JNIWatermarkNode jNIWatermarkNode, String str, int i, String str2, String str3);

    native boolean nativeSetPosition(int i, int i2, int i3, int i4);

    native void nativeUnlock();
}
