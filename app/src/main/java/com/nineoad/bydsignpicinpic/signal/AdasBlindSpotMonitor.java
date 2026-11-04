package com.nineoad.bydsignpicinpic.signal;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.nineoad.bydsignpicinpic.autoservice.AutoserviceClient;

/**
 * ADAS Blind Spot Detection (BSD / BSM) monitor for BYD vehicles.
 * Analyzes live hardware register channels as proven in Overdrive / BYD Auto platform:
 *  - Area 1000, Property 0x12d0002a (Cluster / ADAS Left Blind Spot Warning)
 *  - Area 1000, Property 0x40d00010 (Cluster / ADAS Right Blind Spot Warning)
 *  - Area 1038, Property 1098907692 (ADAS_FL_BLIND_SPOT_ALARM)
 *  - Area 1038, Property 1098907694 (ADAS_FR_BLIND_SPOT_ALARM)
 */
public class AdasBlindSpotMonitor {
    private static final String TAG = "AdasBlindSpot";
    private static final long POLL_INTERVAL_MS = 100;

    public interface Callback {
        void onAlertChanged(boolean alertLeft, boolean alertRight);
    }

    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread workerThread;
    private volatile boolean running = false;
    private volatile boolean lastLeftAlert = false;
    private volatile boolean lastRightAlert = false;

    public AdasBlindSpotMonitor(Callback callback) {
        this.callback = callback;
    }

    public synchronized void start() {
        if (this.running) return;
        this.running = true;
        this.workerThread = new Thread(this::pollLoop, "adas-bsd-monitor");
        this.workerThread.start();
        Log.i(TAG, "AdasBlindSpotMonitor started");
    }

    public synchronized void stop() {
        this.running = false;
        if (this.workerThread != null) {
            this.workerThread.interrupt();
            this.workerThread = null;
        }
        Log.i(TAG, "AdasBlindSpotMonitor stopped");
    }

    private void pollLoop() {
        while (this.running) {
            try {
                // 1. Check Left Blind Spot
                Integer leftProp = AutoserviceClient.getInt(1000, 0x12d0002a);
                if (leftProp == null) {
                    leftProp = AutoserviceClient.getInt(1038, 1098907692);
                }

                // 2. Check Right Blind Spot
                Integer rightProp = AutoserviceClient.getInt(1000, 0x40d00010);
                if (rightProp == null) {
                    rightProp = AutoserviceClient.getInt(1038, 1098907694);
                }

                // Threshold: 0 / -10011 / null = clear/idle; values > 0 indicate active detection/warning
                // Note: on some clusters, baseline is recorded at boot; alert is signaled when > 0
                boolean alertL = (leftProp != null && leftProp > 0 && leftProp != 65535 && leftProp != 2); // 2 is idle baseline on some firmware
                boolean alertR = (rightProp != null && rightProp > 0 && rightProp != 65535 && rightProp != 128); // 128 is idle baseline on some firmware

                // Also check LCA / DOW warnings if available
                if (!alertL) {
                    Integer lcaL = AutoserviceClient.getInt(1038, 1098907664); // ADAS_LCA_WARNING_LEFT
                    if (lcaL != null && lcaL > 0 && lcaL != 65535) alertL = true;
                }
                if (!alertR) {
                    Integer lcaR = AutoserviceClient.getInt(1038, 1098907666); // ADAS_LCA_WARNING_RIGHT
                    if (lcaR != null && lcaR > 0 && lcaR != 65535) alertR = true;
                }

                if (alertL != this.lastLeftAlert || alertR != this.lastRightAlert) {
                    this.lastLeftAlert = alertL;
                    this.lastRightAlert = alertR;
                    final boolean fL = alertL;
                    final boolean fR = alertR;
                    this.mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onAlertChanged(fL, fR);
                        }
                    });
                }
            } catch (Throwable t) {
                // Ignore transient bus read timeouts
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public boolean isLeftAlert() {
        return this.lastLeftAlert;
    }

    public boolean isRightAlert() {
        return this.lastRightAlert;
    }
}
