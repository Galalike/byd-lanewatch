package com.nineoad.bydsignpicinpic.signal;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.nineoad.bydsignpicinpic.autoservice.AutoserviceClient;
import com.nineoad.bydsignpicinpic.autoservice.TurnFids;
import java.lang.reflect.Method;

/* JADX INFO: loaded from: classes.dex */
public class TurnSignalListener {
    private static final int EMERG_ON = 2;
    public static final int HAZARD = 3;
    public static final int LEFT = 1;
    public static final int OFF = 0;
    private static final int ON = 1;
    private static final long POLL_MS = 40;
    public static final int RIGHT = 2;
    private static final long SUPPRESS_POLL_MS = 750;
    private static final String TAG = "TurnSignal";
    public static volatile long lingerMs = 3500;
    public static volatile boolean showHazard = true;
    public static volatile boolean showLeft = true;
    public static volatile boolean showRight = true;
    private final Callback callback;
    private final Context ctx;
    private Object gearDev;
    private String lastFgLog;
    private Method mIsReverse;
    private Thread poller;
    private volatile boolean running;
    private UsageStatsManager usm;
    private Thread watchdog;
    private long lastSuppressMs = 0;
    private volatile boolean suppressed = false;
    private volatile int state = 0;
    private volatile long lastActiveMs = 0;

    public interface Callback {
        void onSignal(int i);
    }

    public String learnPath() {
        return null;
    }

    public TurnSignalListener(Context context, Callback callback, boolean z) {
        this.ctx = context.getApplicationContext();
        this.callback = callback;
    }

    public void start() {
        this.running = true;
        try {
            SharedPreferences sharedPreferences = this.ctx.getSharedPreferences("pnp", 0);
            showLeft = sharedPreferences.getBoolean("trigLeft", true);
            showRight = sharedPreferences.getBoolean("trigRight", true);
            showHazard = sharedPreferences.getBoolean("trigHazard", true);
        } catch (Throwable unused) {
        }
        initGear();
        try {
            this.usm = (UsageStatsManager) this.ctx.getSystemService("usagestats");
        } catch (Throwable unused2) {
        }
        Thread thread = new Thread(new Runnable() { // from class: com.nineoad.bydsignpicinpic.signal.TurnSignalListener$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                TurnSignalListener.this.pollLoop();
            }
        }, "turnsignal-poll");
        this.poller = thread;
        thread.start();
        Thread thread2 = new Thread(new Runnable() { // from class: com.nineoad.bydsignpicinpic.signal.TurnSignalListener$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                TurnSignalListener.this.watchdogLoop();
            }
        }, "turnsignal-watchdog");
        this.watchdog = thread2;
        thread2.start();
    }

    public void stop() {
        this.running = false;
        Thread thread = this.poller;
        if (thread != null) {
            thread.interrupt();
        }
        Thread thread2 = this.watchdog;
        if (thread2 != null) {
            thread2.interrupt();
        }
    }

    public int getState() {
        return this.state;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pollLoop() {
        while (this.running) {
            try {
                long jCurrentTimeMillis = System.currentTimeMillis();
                boolean panoProp = isBydPanoPropActive();
                if (panoProp && !com.nineoad.bydsignpicinpic.overlay.OverlayService.isOemCameraForeground) {
                    com.nineoad.bydsignpicinpic.overlay.OverlayService.setOemCameraForeground(true);
                } else if (isPanoPropClosed() && com.nineoad.bydsignpicinpic.overlay.OverlayService.isOemCameraForeground) {
                    com.nineoad.bydsignpicinpic.overlay.OverlayService.setOemCameraForeground(false);
                }

                boolean oemCameraActive = com.nineoad.bydsignpicinpic.overlay.OverlayService.isOemCameraForeground || panoProp;
                if (jCurrentTimeMillis - this.lastSuppressMs >= SUPPRESS_POLL_MS) {
                    this.lastSuppressMs = jCurrentTimeMillis;
                    boolean fgCam = isCameraAppForeground();
                    if (fgCam && !com.nineoad.bydsignpicinpic.overlay.OverlayService.isOemCameraForeground) {
                        com.nineoad.bydsignpicinpic.overlay.OverlayService.setOemCameraForeground(true);
                    }
                    this.suppressed = oemCameraActive || fgCam;
                } else {
                    this.suppressed = oemCameraActive;
                }
                if (this.suppressed) {
                    if (this.state != 0) {
                        this.state = 0;
                        Log.i(TAG, "Vehicle Image OEM view active -> OFF");
                        Callback callback = this.callback;
                        if (callback != null) {
                            callback.onSignal(0);
                        }
                    }
                } else {
                    Integer num = AutoserviceClient.getInt(1004, TurnFids.FID_TURN_FLASH);
                    Integer num2 = showHazard ? AutoserviceClient.getInt(1001, TurnFids.FID_EMERGENCY_ALARM) : null;
                    if (showHazard && ((num2 != null && num2.intValue() == 2) || (num != null && num.intValue() == 6))) {
                        setActive(3);
                    } else if (showLeft && num != null && (num.intValue() == 2 || num.intValue() == 3)) {
                        setActive(1);
                    } else if (showRight && num != null && (num.intValue() == 4 || num.intValue() == 5)) {
                        setActive(2);
                    }
                }

                try {
                    com.nineoad.bydsignpicinpic.overlay.OverlayService overlay = com.nineoad.bydsignpicinpic.overlay.OverlayService.getInstance();
                    if (overlay != null && overlay.getBevAutomation() != null) {
                        overlay.getBevAutomation().poll(this.suppressed, this.state != 0);
                    }
                } catch (Throwable ignored) {
                }
            } catch (Throwable th) {
                Log.w(TAG, "poll: " + th);
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException unused) {
                return;
            }
        }
    }

    private static Method sSystemPropGet = null;
    private static boolean sSystemPropInit = false;
    private static volatile boolean sPanoPropSupported = false;

    public static boolean isBydPanoPropActive() {
        try {
            if (!sSystemPropInit) {
                sSystemPropInit = true;
                Class<?> spClass = Class.forName("android.os.SystemProperties");
                sSystemPropGet = spClass.getMethod("get", String.class, String.class);
            }
            if (sSystemPropGet != null) {
                String val = (String) sSystemPropGet.invoke(null, "sys.byd.pano", "0");
                if ("1".equals(val) || "true".equalsIgnoreCase(val)) {
                    sPanoPropSupported = true;
                    return true;
                }
            }
        } catch (Throwable unused) {
        }
        return false;
    }

    public static boolean isPanoPropClosed() {
        if (!sPanoPropSupported || sSystemPropGet == null) {
            return false;
        }
        try {
            String val = (String) sSystemPropGet.invoke(null, "sys.byd.pano", "0");
            return "0".equals(val);
        } catch (Throwable unused) {
            return false;
        }
    }

    private void initGear() {
        CarContext carContext = new CarContext(this.ctx);
        try {
            Class<?> cls = Class.forName("android.hardware.bydauto.gearbox.BYDAutoGearboxDevice");
            boolean z = true;
            Object objInvoke = cls.getMethod("getInstance", Context.class).invoke(null, carContext);
            this.gearDev = objInvoke;
            warmup(objInvoke);
            this.mIsReverse = cls.getMethod("isInReverseGear", new Class[0]);
            StringBuilder sb = new StringBuilder("gear ready dev=");
            if (this.gearDev == null) {
                z = false;
            }
            Log.i(TAG, sb.append(z).toString());
        } catch (Throwable th) {
            Log.w(TAG, "gear init failed: " + th);
        }
    }

    private static void warmup(Object obj) {
        if (obj == null) {
            return;
        }
        try {
            obj.getClass().getMethod("getAllStatus", new Class[0]).invoke(obj, new Object[0]);
        } catch (Throwable unused) {
        }
    }

    private boolean isReverseGear() {
        Method method;
        Object obj = this.gearDev;
        if (obj == null || (method = this.mIsReverse) == null) {
            return false;
        }
        try {
            Object objInvoke = method.invoke(obj, new Object[0]);
            return (objInvoke instanceof Boolean) && ((Boolean) objInvoke).booleanValue();
        } catch (Throwable unused) {
            return false;
        }
    }

    private boolean isCameraAppForeground() {
        if (this.usm == null) {
            return false;
        }
        try {
            long jCurrentTimeMillis = System.currentTimeMillis();
            UsageEvents usageEventsQueryEvents = this.usm.queryEvents(jCurrentTimeMillis - 60000, jCurrentTimeMillis);
            if (usageEventsQueryEvents == null) {
                return false;
            }
            UsageEvents.Event event = new UsageEvents.Event();
            String packageName = null;
            long timeStamp = 0;
            while (usageEventsQueryEvents.hasNextEvent()) {
                usageEventsQueryEvents.getNextEvent(event);
                if (event.getEventType() == 1 && event.getTimeStamp() >= timeStamp) {
                    timeStamp = event.getTimeStamp();
                    packageName = event.getPackageName();
                }
            }
            if (packageName == null) {
                return false;
            }
            boolean zIsCameraPkg = isCameraPkg(packageName);
            if (!packageName.equals(this.lastFgLog)) {
                this.lastFgLog = packageName;
                Log.i(TAG, "foreground=" + packageName + " camPage=" + zIsCameraPkg);
            }
            return zIsCameraPkg;
        } catch (Throwable unused) {
            return false;
        }
    }

    private static boolean isCameraPkg(String str) {
        if (str == null) {
            return false;
        }
        str.hashCode();
        switch (str) {
            case "com.byd.panorama":
            case "com.byd.cameramanager":
            case "com.byd.auto_camera":
            case "com.byd.bydcamera":
            case "com.byd.avc":
            case "com.byd.avm":
                return true;
            default:
                String lowerCase = str.toLowerCase();
                if (lowerCase.startsWith("com.byd") || lowerCase.startsWith("com.bydauto")) {
                    return lowerCase.contains("avm") || lowerCase.contains("avc") || lowerCase.contains("panorama") || lowerCase.contains("pano") || lowerCase.contains("camera") || lowerCase.contains("aroundview") || lowerCase.contains("around") || lowerCase.contains("surround") || lowerCase.contains("360");
                }
                return false;
        }
    }

    private void setActive(int i) {
        this.lastActiveMs = System.currentTimeMillis();
        if (i != this.state) {
            this.state = i;
            Log.i(TAG, "state -> " + i);
            Callback callback = this.callback;
            if (callback != null) {
                callback.onSignal(i);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void watchdogLoop() {
        while (this.running) {
            try {
                Thread.sleep(200L);
                if (this.state != 0 && System.currentTimeMillis() - this.lastActiveMs > lingerMs) {
                    this.state = 0;
                    Log.i(TAG, "state -> OFF");
                    Callback callback = this.callback;
                    if (callback != null) {
                        callback.onSignal(0);
                    }
                }
            } catch (InterruptedException unused) {
                return;
            }
        }
    }
}
