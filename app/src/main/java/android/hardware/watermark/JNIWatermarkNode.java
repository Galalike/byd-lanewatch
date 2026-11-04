package android.hardware.watermark;

import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class JNIWatermarkNode {
    private long mNativeReference;

    private static native void nativeInit();

    native boolean nativeDisable();

    native boolean nativeEnable();

    native boolean nativeIsEnable();

    native boolean nativeRender(byte[] bArr, int i, int i2, int i3, long j);

    native boolean nativeRenderDirect(ByteBuffer byteBuffer, int i, int i2, int i3, long j);

    native boolean nativeSetFontPath(String str, int i, String str2, String str3);

    native boolean nativeSetImgArea(int i, int i2, int i3, int i4);

    native boolean nativeSetImgPath(int i, String str);

    native boolean nativeSetTextArea(int i, int i2, int i3, int i4);

    native boolean nativeSetTxt(String str);

    native void nativeSetup(int i);

    native void nativeUninstall();
}
