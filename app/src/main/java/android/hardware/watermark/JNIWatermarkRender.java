package android.hardware.watermark;

import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class JNIWatermarkRender {
    private long mNativeReference;

    private static native void nativeInit();

    private native void nativeSetup();

    private native void nativeUninstall();

    native boolean nativeAddFont(String str, int i, String str2, String str3);

    native boolean nativeAddNode(JNIWatermarkNode jNIWatermarkNode);

    native boolean nativeAddNodeGroup(JNIWatermarkNodeGroup jNIWatermarkNodeGroup);

    native boolean nativeLock();

    native boolean nativeRender(byte[] bArr, int i, int i2, int i3, long j);

    native boolean nativeRenderForDirect(ByteBuffer byteBuffer, int i, int i2, int i3, long j);

    native boolean nativeRmNode(JNIWatermarkNode jNIWatermarkNode);

    native boolean nativeRmNodeGroup(JNIWatermarkNodeGroup jNIWatermarkNodeGroup);

    native boolean nativeSetNodeFont(JNIWatermarkNode jNIWatermarkNode, String str, int i, String str2, String str3);

    native void nativeUnlock();
}
