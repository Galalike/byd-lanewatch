package com.nineoad.bydsignpicinpic.signal;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.nineoad.bydsignpicinpic.autoservice.AutoserviceClient;

/**
 * ADAS Blind Spot Detection (BSD / BSM) monitor for BYD vehicles.
 * Analyzes live hardware register channels from BYD Auto ADAS service (Area 1038):
 *  - Area 1038, Property 1098907692 (ADAS_FL_BLIND_SPOT_ALARM_ACTIVE_SIGNAL_STATUS)
 *  - Area 1038, Property 1098907694 (ADAS_FR_BLIND_SPOT_ALARM_ACTIVE_SIGNAL_STATUS)
 *  - Area 1038, Property 1098907664 (ADAS_RIGHT_RADAR_LCA_WARNINGLEFT)
 *  - Area 1038, Property 1098907666 (ADAS_RIGHT_RADAR_LCA_WARNINGRIGHT)
 *  - Area 1038, Property 1098907680 (ADAS_DOW_WARN_LEFT)
 *  - Area 1038, Property 1098907682 (ADAS_DOW_WARN_RIGHT)
 *
 * NOTE: Area 1000 properties (cluster display/trip registers) and BSD system status
 * (ADAS_BSD_STATE / INSTRUMENT_BLIND_AREA) are NOT active alarms and are excluded
 * to prevent constant false positive flashing on BYD Dolphin / Atto 3 / Seal.
 */
public class AdasBlindSpotMonitor {
    private static final String TAG = "AdasBlindSpot";
    private static final long POLL_INTERVAL_MS = 100L;
    private static final long ALERT_HOLD_MS = 1500L; // Smooth momentary CAN radar pulses

    public interface Callback {
        void onAlertChanged(boolean alertLeft, boolean alertRight);
    }

    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread workerThread;
    private volatile boolean running = false;
    private volatile boolean lastLeftAlert = false;
    private volatile boolean lastRightAlert = false;
    private long lastAlertLTimeMs = 0L;
    private long lastAlertRTimeMs = 0L;

    private long lastDaemonPollMs = 0L;
    private boolean daemonAlertL = false;
    private boolean daemonAlertR = false;

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
                // 1. Check genuine Left Blind Spot Radar & LCA/DOW alarms
                Integer flAlarm = AutoserviceClient.getInt(1038, 1098907692); // ADAS_FL_BLIND_SPOT_ALARM
                Integer lcaL = AutoserviceClient.getInt(1038, 1098907664);    // ADAS_LCA_WARNING_LEFT
                Integer dowL = AutoserviceClient.getInt(1038, 1098907680);    // ADAS_DOW_WARN_LEFT

                boolean rawAlertL = (flAlarm != null && flAlarm > 0 && flAlarm != 65535)
                        || (lcaL != null && lcaL > 0 && lcaL != 65535)
                        || (dowL != null && dowL > 0 && dowL != 65535);

                // 2. Check genuine Right Blind Spot Radar & LCA/DOW alarms
                Integer frAlarm = AutoserviceClient.getInt(1038, 1098907694); // ADAS_FR_BLIND_SPOT_ALARM
                Integer lcaR = AutoserviceClient.getInt(1038, 1098907666);    // ADAS_LCA_WARNING_RIGHT
                Integer dowR = AutoserviceClient.getInt(1038, 1098907682);    // ADAS_DOW_WARN_RIGHT

                boolean rawAlertR = (frAlarm != null && frAlarm > 0 && frAlarm != 65535)
                        || (lcaR != null && lcaR > 0 && lcaR != 65535)
                        || (dowR != null && dowR > 0 && dowR != 65535);

                // 3. Check local Overdrive daemon loopback states if running
                checkDaemonAlerts();
                if (this.daemonAlertL) rawAlertL = true;
                if (this.daemonAlertR) rawAlertR = true;

                // 4. Hold smoothing so fast CAN pulse remains steady during the hazard window
                long now = SystemClock.elapsedRealtime();
                if (rawAlertL) {
                    this.lastAlertLTimeMs = now;
                }
                if (rawAlertR) {
                    this.lastAlertRTimeMs = now;
                }

                boolean alertL = (this.lastAlertLTimeMs > 0 && (now - this.lastAlertLTimeMs) < ALERT_HOLD_MS);
                boolean alertR = (this.lastAlertRTimeMs > 0 && (now - this.lastAlertRTimeMs) < ALERT_HOLD_MS);

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
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        this.daemonAlertL = false;
        this.daemonAlertR = false;
    }

    public boolean isLeftAlert() {
        return this.lastLeftAlert;
    }

    public boolean isRightAlert() {
        return this.lastRightAlert;
    }

    public boolean isAnyAlert() {
        return this.lastLeftAlert || this.lastRightAlert;
    }
}
