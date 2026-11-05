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
    private volatile boolean lastAnyAlert = false;
    private long lastDaemonPollMs = 0;
    private boolean daemonAlertL = false;
    private boolean daemonAlertR = false;
    private boolean daemonAlertAny = false;

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

                // Check generic / any-side blind spot warning (BSD state > 2, Instrument detection, or daemon blindSpot:side=any)
                boolean alertAny = false;
                Integer bsdState = AutoserviceClient.getInt(1038, 1098907656); // ADAS_BSD_STATE
                if (bsdState != null && bsdState > 2 && bsdState != 65535) {
                    alertAny = true;
                }
                Integer instBsd = AutoserviceClient.getInt(1000, 107920); // INSTRUMENT_BLIND_AREA_DETECTION_SYSTEM
                if (instBsd != null && instBsd > 0 && instBsd != 65535) {
                    alertAny = true;
                }

                // Check local Overdrive daemon loopback states if running
                checkDaemonAlerts();
                if (this.daemonAlertL) alertL = true;
                if (this.daemonAlertR) alertR = true;
                if (this.daemonAlertAny) alertAny = true;

                if (alertL != this.lastLeftAlert || alertR != this.lastRightAlert || alertAny != this.lastAnyAlert) {
                    this.lastLeftAlert = alertL;
                    this.lastRightAlert = alertR;
                    this.lastAnyAlert = alertAny;
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

    private void checkDaemonAlerts() {
        long now = System.currentTimeMillis();
        if (now - this.lastDaemonPollMs < 300) return;
        this.lastDaemonPollMs = now;
        try {
            java.net.URL url = new java.net.URL("http://127.0.0.1:8080/api/automations/state");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(200);
            conn.setReadTimeout(200);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                java.io.InputStream in = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                in.close();
                org.json.JSONObject root = new org.json.JSONObject(sb.toString());
                org.json.JSONObject state = root.optJSONObject("state");
                if (state != null) {
                    this.daemonAlertL = "on".equalsIgnoreCase(state.optString("blindSpot:side=left"))
                            || "on".equalsIgnoreCase(state.optString("rearCrossTraffic:side=left"))
                            || "on".equalsIgnoreCase(state.optString("doorOpenWarning:side=left"));
                    this.daemonAlertR = "on".equalsIgnoreCase(state.optString("blindSpot:side=right"))
                            || "on".equalsIgnoreCase(state.optString("rearCrossTraffic:side=right"))
                            || "on".equalsIgnoreCase(state.optString("doorOpenWarning:side=right"));
                    this.daemonAlertAny = "on".equalsIgnoreCase(state.optString("blindSpot:side=any"));
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        this.daemonAlertL = false;
        this.daemonAlertR = false;
        this.daemonAlertAny = false;
    }

    public boolean isLeftAlert() {
        return this.lastLeftAlert;
    }

    public boolean isRightAlert() {
        return this.lastRightAlert;
    }

    public boolean isAnyAlert() {
        return this.lastAnyAlert || this.daemonAlertAny;
    }
}
