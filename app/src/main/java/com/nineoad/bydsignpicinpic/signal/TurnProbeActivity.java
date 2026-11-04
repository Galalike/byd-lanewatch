package com.nineoad.bydsignpicinpic.signal;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import java.lang.reflect.Method;

/* JADX INFO: loaded from: classes.dex */
public class TurnProbeActivity extends AppCompatActivity {
    private static final int FID_LEFT = 321912844;
    private static final int FID_RIGHT = 321912845;
    private static final String LIGHT_CLS = "android.hardware.bydauto.light.BYDAutoLightDevice";
    private static final int MAX_DEV = 15;
    private static final int TX = 5;
    private IBinder autoservice;
    private Method getService;
    private Object lightDevice;
    private Method mDouble;
    private Method mFeat;
    private Method mFlash;
    private Method mTurn;
    private Method mTurnIdx;
    private volatile boolean running;
    private TextView tv;

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        this.tv = textView;
        textView.setTextSize(13.0f);
        int iRound = Math.round(getResources().getDisplayMetrics().density * 12.0f);
        this.tv.setPadding(iRound, iRound, iRound, iRound);
        scrollView.addView(this.tv);
        setContentView(scrollView);
        initSdk();
        initAutoservice();
        this.running = true;
        new Thread(new Runnable() { // from class: com.nineoad.bydsignpicinpic.signal.TurnProbeActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                TurnProbeActivity.this.loop();
            }
        }, "turnprobe").start();
    }

    private void initSdk() {
        try {
            Class<?> cls = Class.forName(LIGHT_CLS);
            this.lightDevice = cls.getMethod("getInstance", Context.class).invoke(null, this);
            this.mTurn = tryMethod(cls, "getTurnLightState", new Class[0]);
            this.mTurnIdx = tryMethod(cls, "getTurnLightState", Integer.TYPE);
            this.mFlash = tryMethod(cls, "getTurnLightFlashState", new Class[0]);
            this.mDouble = tryMethod(cls, "getDoubleFlashLightState", new Class[0]);
            this.mFeat = tryMethod(cls, "getBydAutoFeatureValue", Integer.TYPE);
        } catch (Throwable unused) {
        }
    }

    private void initAutoservice() {
        try {
            Method method = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            this.getService = method;
            this.autoservice = (IBinder) method.invoke(null, "autoservice");
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loop() {
        while (this.running) {
            StringBuilder sb = new StringBuilder("=== TURN PROBE (toggle signal & watch) ===\n\n[A] getTurnLightState()      = ");
            sb.append(call(this.mTurn, new Object[0])).append("\n[A2] getTurnLightState(0)    = ");
            sb.append(call(this.mTurnIdx, 0)).append("\n     getTurnLightState(1)    = ");
            sb.append(call(this.mTurnIdx, 1)).append("\n[F] getTurnLightFlashState() = ");
            sb.append(call(this.mFlash, new Object[0])).append("\n    getDoubleFlashLightState = ");
            sb.append(call(this.mDouble, new Object[0])).append("\n[B] feat(left 0x1330000C)    = ");
            sb.append(call(this.mFeat, Integer.valueOf(FID_LEFT))).append("\n    feat(right 0x1330000D)   = ");
            sb.append(call(this.mFeat, Integer.valueOf(FID_RIGHT))).append("\n\n[C] autoservice transact(5, devId, fid):\n");
            if (this.autoservice == null) {
                sb.append("    autoservice binder null\n");
            } else {
                sb.append("    devId : left / right\n");
                for (int i = 0; i <= 15; i++) {
                    String strAuto = auto(i, FID_LEFT);
                    String strAuto2 = auto(i, FID_RIGHT);
                    if (!strAuto.equals(NotificationCompat.CATEGORY_ERROR) || !strAuto2.equals(NotificationCompat.CATEGORY_ERROR)) {
                        sb.append("    ").append(i).append("     : ").append(strAuto).append(" / ").append(strAuto2).append("\n");
                    }
                }
            }
            final String string = sb.toString();
            runOnUiThread(new Runnable() { // from class: com.nineoad.bydsignpicinpic.signal.TurnProbeActivity$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    TurnProbeActivity.this.lambda$loop$0(string);
                }
            });
            try {
                Thread.sleep(400L);
            } catch (InterruptedException unused) {
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loop$0(String str) {
        this.tv.setText(str);
    }

    private static Method tryMethod(Class<?> cls, String str, Class<?>... clsArr) {
        try {
            return cls.getMethod(str, clsArr);
        } catch (Throwable unused) {
            return null;
        }
    }

    private String call(Method method, Object... objArr) {
        if (method == null) {
            return "no-method";
        }
        Object obj = this.lightDevice;
        if (obj == null) {
            return "no-device";
        }
        try {
            return String.valueOf(method.invoke(obj, objArr));
        } catch (Throwable th) {
            th = th;
            if (th.getCause() != null) {
                th = th.getCause();
            }
            String message = th.getMessage();
            String simpleName = th.getClass().getSimpleName();
            if (message == null) {
                return simpleName;
            }
            StringBuilder sbAppend = new StringBuilder().append(simpleName).append(": ");
            if (message.length() > 90) {
                message = message.substring(0, 90);
            }
            return sbAppend.append(message).toString();
        }
    }

    private String auto(int i, int i2) {
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInt(i);
            parcelObtain.writeInt(i2);
            if (this.autoservice.transact(5, parcelObtain, parcelObtain2, 0)) {
                String strValueOf = String.valueOf(parcelObtain2.readInt());
                parcelObtain.recycle();
                parcelObtain2.recycle();
                return strValueOf;
            }
        } catch (Throwable unused) {
        }
        parcelObtain.recycle();
        parcelObtain2.recycle();
        return NotificationCompat.CATEGORY_ERROR;
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        this.running = false;
        super.onDestroy();
    }
}
