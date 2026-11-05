package com.nineoad.bydsignpicinpic.signal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import com.nineoad.bydsignpicinpic.autoservice.AutoserviceClient;
import com.nineoad.bydsignpicinpic.overlay.OverlayService;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/**
 * Real-time Vehicle Signal Probe & ADAS Inspector.
 * Allows monitoring CAN / IVI signals, Turn Signals, BSD Radar, and Overdrive states live while driving.
 */
public class TurnProbeActivity extends AppCompatActivity {
    private static final String LIGHT_CLS = "android.hardware.bydauto.light.BYDAutoLightDevice";
    private static final int FID_LEFT = 321912844;
    private static final int FID_RIGHT = 321912845;

    private TextView tvStatusHeader;
    private TextView tvTurnSignals;
    private TextView tvBsdRadar;
    private TextView tvVehicleDynamics;
    private TextView tvDaemonStatus;
    private TextView tvLiveLogs;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private final ArrayList<String> changeLogs = new ArrayList<>();
    private final Map<String, Object> previousValues = new HashMap<>();

    private volatile boolean running = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread workerThread;

    // Reflection SDK Handles
    private Object lightDevice;
    private Method mTurn;
    private Method mTurnIdx;
    private Method mFlash;
    private Method mDouble;
    private Method mFeat;

    private Object speedDev;
    private Method mGetSpeed;
    private Object bodyDev;
    private Method mGetSteering;
    private Object gearDev;
    private Method mGetGearMode;
    private Method mIsInReverse;

    private IBinder autoservice;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Dark theme background
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A")); // Deep dark blue-gray
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        // 1. Header Toolbar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(10));

        Button btnBack = createButton("⬅️ กลับ", Color.parseColor("#334155"), Color.WHITE);
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(" 📡 Vehicle Signal Probe (ตัวตรวจเช็คสัญญาณสด)");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpTitle.setMarginStart(dp(10));
        tvTitle.setLayoutParams(lpTitle);
        header.addView(tvTitle);

        Button btnTestLeft = createButton("🚨 ทดสอบเตือนซ้าย", Color.parseColor("#991B1B"), Color.WHITE);
        btnTestLeft.setOnClickListener(v -> testAlert(true, false));
        header.addView(btnTestLeft);

        Button btnTestRight = createButton("🚨 ทดสอบเตือนขวา", Color.parseColor("#991B1B"), Color.WHITE);
        LinearLayout.LayoutParams lpBtnR = (LinearLayout.LayoutParams) btnTestRight.getLayoutParams();
        if (lpBtnR != null) lpBtnR.setMarginStart(dp(6));
        btnTestRight.setOnClickListener(v -> testAlert(false, true));
        header.addView(btnTestRight);

        Button btnCopy = createButton("📋 คัดลอก Report", Color.parseColor("#2563EB"), Color.WHITE);
        LinearLayout.LayoutParams lpBtnCopy = (LinearLayout.LayoutParams) btnCopy.getLayoutParams();
        if (lpBtnCopy != null) lpBtnCopy.setMarginStart(dp(6));
        btnCopy.setOnClickListener(v -> copyReportToClipboard());
        header.addView(btnCopy);

        Button btnClearLog = createButton("🧹 ล้าง Log", Color.parseColor("#475569"), Color.WHITE);
        LinearLayout.LayoutParams lpBtnClear = (LinearLayout.LayoutParams) btnClearLog.getLayoutParams();
        if (lpBtnClear != null) lpBtnClear.setMarginStart(dp(6));
        btnClearLog.setOnClickListener(v -> {
            changeLogs.clear();
            tvLiveLogs.setText("Log ถูกล้างเรียบร้อย... กำลังดักฟังสัญญาณใหม่");
        });
        header.addView(btnClearLog);

        root.addView(header);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#334155"));
        root.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 2. Scrollable Body
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(20));

        // Subtitle instructions
        TextView tvSub = new TextView(this);
        tvSub.setText("💡 เปิดหน้าจอนี้ไว้ขณะขับขี่ หรือเปิดไฟเลี้ยว แล้วสังเกตว่าสัญญาณตัวไหนเปลี่ยนค่าแบบเรียลไทม์");
        tvSub.setTextColor(Color.parseColor("#94A3B8"));
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, 0, 0, dp(10));
        content.addView(tvSub);

        // Card 1: Turn Signals & Switches
        content.addView(createSectionHeader("🚦 ก้านไฟเลี้ยว & ไฟฉุกเฉิน (Turn & Hazard Signals)"));
        this.tvTurnSignals = createMonospaceCard();
        content.addView(this.tvTurnSignals);

        // Card 2: Blind Spot & ADAS Radar
        content.addView(createSectionHeader("👁️ จุดอับสายตา & เรดาร์ (Blind Spot / BSD & Radar)"));
        this.tvBsdRadar = createMonospaceCard();
        content.addView(this.tvBsdRadar);

        // Card 3: Overdrive Loopback Daemon
        content.addView(createSectionHeader("🔄 Overdrive Local Daemon Loopback (Port 8080)"));
        this.tvDaemonStatus = createMonospaceCard();
        content.addView(this.tvDaemonStatus);

        // Card 4: Vehicle Dynamics (Speed, Gear, Steering)
        content.addView(createSectionHeader("🏎️ การเคลื่อนที่ของรถ (Speed / Gear / Steering)"));
        this.tvVehicleDynamics = createMonospaceCard();
        content.addView(this.tvVehicleDynamics);

        // Card 5: Live Change Watcher Log
        content.addView(createSectionHeader("📜 บันทึกเหตุการณ์สดเมื่อสัญญาณเปลี่ยนค่า (Live Event Stream)"));
        this.tvLiveLogs = createMonospaceCard();
        this.tvLiveLogs.setText("กำลังเริ่มต้นดักฟังสัญญาณ...");
        this.tvLiveLogs.setTextColor(Color.parseColor("#FDE047")); // Warm yellow
        content.addView(this.tvLiveLogs);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        setContentView(root);

        initDevices();
        startPoll();
    }

    private Button createButton(String text, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setBackgroundColor(bgColor);
        b.setTextColor(textColor);
        b.setTextSize(12f);
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        b.setAllCaps(false);
        return b;
    }

    private TextView createSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#38BDF8")); // Bright Sky Blue
        tv.setTextSize(14f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(4), dp(12), dp(4), dp(4));
        return tv;
    }

    private TextView createMonospaceCard() {
        TextView tv = new TextView(this);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12f);
        tv.setTextColor(Color.parseColor("#E2E8F0"));
        tv.setBackgroundColor(Color.parseColor("#1E293B")); // Card dark slate
        int p = dp(12);
        tv.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(6));
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int val) {
        return Math.round(getResources().getDisplayMetrics().density * val);
    }

    private void initDevices() {
        Context carCtx = this;
        // 1. Light Device
        try {
            Class<?> cls = Class.forName(LIGHT_CLS);
            this.lightDevice = cls.getMethod("getInstance", Context.class).invoke(null, carCtx);
            this.mTurn = tryMethod(cls, "getTurnLightState");
            this.mTurnIdx = tryMethod(cls, "getTurnLightState", Integer.TYPE);
            this.mFlash = tryMethod(cls, "getTurnLightFlashState");
            this.mDouble = tryMethod(cls, "getDoubleFlashLightState");
            this.mFeat = tryMethod(cls, "getBydAutoFeatureValue", Integer.TYPE);
        } catch (Throwable ignored) {}

        // 2. Speed Device
        try {
            Class<?> clsSpeed = Class.forName("android.hardware.bydauto.speed.BYDAutoSpeedDevice");
            this.speedDev = clsSpeed.getMethod("getInstance", Context.class).invoke(null, carCtx);
            if (this.speedDev != null) {
                this.mGetSpeed = clsSpeed.getMethod("getCurrentSpeed");
            }
        } catch (Throwable ignored) {}

        // 3. Bodywork Device
        try {
            Class<?> clsBody = Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice");
            this.bodyDev = clsBody.getMethod("getInstance", Context.class).invoke(null, carCtx);
            if (this.bodyDev != null) {
                this.mGetSteering = clsBody.getMethod("getSteeringWheelValue", Integer.TYPE);
            }
        } catch (Throwable ignored) {}

        // 4. Gear Device
        try {
            Class<?> clsGear = Class.forName("android.hardware.bydauto.gearbox.BYDAutoGearboxDevice");
            this.gearDev = clsGear.getMethod("getInstance", Context.class).invoke(null, carCtx);
            if (this.gearDev != null) {
                this.mGetGearMode = clsGear.getMethod("getGearboxAutoModeType");
                this.mIsInReverse = clsGear.getMethod("isInReverseGear");
            }
        } catch (Throwable ignored) {}

        // 5. Autoservice
        try {
            Method m = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            this.autoservice = (IBinder) m.invoke(null, "autoservice");
        } catch (Throwable ignored) {}
    }

    private static Method tryMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getMethod(name, params);
        } catch (Throwable unused) {
            return null;
        }
    }

    private void startPoll() {
        this.running = true;
        this.workerThread = new Thread(this::pollLoop, "signal-probe-worker");
        this.workerThread.start();
    }

    private void pollLoop() {
        while (this.running) {
            try {
                // Collect Turn Signal info
                final String turnText = gatherTurnSignals();
                final String bsdText = gatherBsdRadar();
                final String daemonText = gatherDaemonStatus();
                final String dynText = gatherDynamics();
                final String logText = buildLogText();

                mainHandler.post(() -> {
                    if (!running) return;
                    tvTurnSignals.setText(turnText);
                    tvBsdRadar.setText(bsdText);
                    tvDaemonStatus.setText(daemonText);
                    tvVehicleDynamics.setText(dynText);
                    tvLiveLogs.setText(logText);
                });
            } catch (Throwable ignored) {}

            try {
                Thread.sleep(180);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private String gatherTurnSignals() {
        StringBuilder sb = new StringBuilder();
        int activeState = 0;
        OverlayService os = OverlayService.getInstance();
        if (os != null && os.getTurnSignalListener() != null) {
            activeState = os.getTurnSignalListener().getState();
        }

        String stateStr = "0 (OFF)";
        if (activeState == 1) stateStr = "1 (🟢 เลี้ยวซ้าย - LEFT TURN)";
        else if (activeState == 2) stateStr = "2 (🟢 เลี้ยวขวา - RIGHT TURN)";
        else if (activeState == 3) stateStr = "3 (⚠️ ไฟฉุกเฉิน - HAZARD)";

        sb.append("• Active Turn State : ").append(stateStr).append("\n");
        sb.append("• Accessibility Remap: ").append(BydKeyRemapService.isServiceRunning ? "🟢 RUNNING (ตรวจจับปุ่ม)" : "🔴 STOPPED").append("\n");

        // Light Device
        Object turnObj = call(this.mTurn, new Object[0]);
        Object flashObj = call(this.mFlash, new Object[0]);
        Object dblObj = call(this.mDouble, new Object[0]);
        Object featL = call(this.mFeat, FID_LEFT);
        Object featR = call(this.mFeat, FID_RIGHT);

        sb.append("• getTurnLightState()      : ").append(turnObj).append("\n");
        sb.append("• getTurnLightFlashState() : ").append(flashObj).append("\n");
        sb.append("• getDoubleFlashLightState : ").append(dblObj).append("\n");
        sb.append("• Feat(Left 0x1330000C)    : ").append(featL).append("\n");
        sb.append("• Feat(Right 0x1330000D)   : ").append(featR);

        checkChange("TurnSignal.ActiveState", stateStr);
        checkChange("LightDevice.Turn", String.valueOf(turnObj));
        checkChange("LightDevice.Flash", String.valueOf(flashObj));

        return sb.toString();
    }

    private String gatherBsdRadar() {
        StringBuilder sb = new StringBuilder();

        // 1. ADAS_BSD_STATE
        Integer bsdState = AutoserviceClient.getInt(1038, 1098907656);
        String bsdDesc = (bsdState == null) ? "null" : (bsdState == 1 ? "1 (Standby)" : (bsdState == 2 ? "2 (Active >15km/h)" : (bsdState == 3 ? "3 (🚨 ALERT WARNING!)" : String.valueOf(bsdState))));
        sb.append("• ADAS_BSD_STATE (0x41800008)       : ").append(bsdDesc).append("\n");

        // 2. Instrument Blind Area
        Integer instBsd = AutoserviceClient.getInt(1000, 107920);
        sb.append("• INSTRUMENT_BLIND_AREA (0x1a590)   : ").append(instBsd).append("\n");

        // 3. FL / FR blind spot alarm active
        Integer flAlarm = AutoserviceClient.getInt(1038, 1098907692);
        Integer frAlarm = AutoserviceClient.getInt(1038, 1098907694);
        sb.append("• ADAS_FL_BLIND_SPOT_ALARM (Left)   : ").append(flAlarm).append("\n");
        sb.append("• ADAS_FR_BLIND_SPOT_ALARM (Right)  : ").append(frAlarm).append("\n");

        // 4. LCA warnings
        Integer lcaL = AutoserviceClient.getInt(1038, 1098907664);
        Integer lcaR = AutoserviceClient.getInt(1038, 1098907666);
        sb.append("• ADAS_LCA_WARNING (Left/Right)     : ").append(lcaL).append(" / ").append(lcaR).append("\n");

        // 5. RCTA / DOW
        Integer rctaL = AutoserviceClient.getInt(1038, 1098907672);
        Integer rctaR = AutoserviceClient.getInt(1038, 1098907674);
        sb.append("• ADAS_RCTA_WARNING (Left/Right)    : ").append(rctaL).append(" / ").append(rctaR).append("\n");

        Integer dowL = AutoserviceClient.getInt(1038, 1098907680);
        Integer dowR = AutoserviceClient.getInt(1038, 1098907682);
        sb.append("• ADAS_DOW_WARN (Left/Right)        : ").append(dowL).append(" / ").append(dowR);

        checkChange("ADAS_BSD_STATE", bsdDesc);
        checkChange("INSTRUMENT_BLIND_AREA", String.valueOf(instBsd));
        checkChange("ADAS_FL_BLIND_SPOT_ALARM", String.valueOf(flAlarm));
        checkChange("ADAS_FR_BLIND_SPOT_ALARM", String.valueOf(frAlarm));
        checkChange("ADAS_LCA_WARNING_LEFT", String.valueOf(lcaL));
        checkChange("ADAS_LCA_WARNING_RIGHT", String.valueOf(lcaR));

        return sb.toString();
    }

    private String gatherDaemonStatus() {
        StringBuilder sb = new StringBuilder();
        try {
            URL url = new URL("http://127.0.0.1:8080/api/automations/state");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(250);
            conn.setReadTimeout(250);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) res.append(line);
                r.close();
                conn.disconnect();

                JSONObject root = new JSONObject(res.toString());
                JSONObject st = root.optJSONObject("state");
                if (st != null) {
                    sb.append("• Daemon Status          : 🟢 CONNECTED (Port 8080)\n");
                    sb.append("• blindSpot:side=any     : ").append(st.optString("blindSpot:side=any")).append("\n");
                    sb.append("• blindSpot:side=left    : ").append(st.optString("blindSpot:side=left")).append("\n");
                    sb.append("• blindSpot:side=right   : ").append(st.optString("blindSpot:side=right")).append("\n");
                    sb.append("• rearCrossTraffic (L/R) : ").append(st.optString("rearCrossTraffic:side=left")).append(" / ").append(st.optString("rearCrossTraffic:side=right")).append("\n");
                    sb.append("• doorOpenWarning (L/R)  : ").append(st.optString("doorOpenWarning:side=left")).append(" / ").append(st.optString("doorOpenWarning:side=right")).append("\n");
                    sb.append("• radarNearest (cm)      : ").append(st.optInt("radarNearest", -1)).append(" cm\n");
                    sb.append("• Turn Signal (Daemon)   : L=").append(st.optString("turnSignal:side=left")).append(" | R=").append(st.optString("turnSignal:side=right"));

                    checkChange("Daemon.BS_ANY", st.optString("blindSpot:side=any"));
                    checkChange("Daemon.BS_LEFT", st.optString("blindSpot:side=left"));
                    checkChange("Daemon.BS_RIGHT", st.optString("blindSpot:side=right"));
                    return sb.toString();
                }
            }
        } catch (Throwable ignored) {}

        sb.append("• Daemon Status          : ⚪ Offline / Not Running (Port 8080)");
        return sb.toString();
    }

    private String gatherDynamics() {
        StringBuilder sb = new StringBuilder();
        // Speed
        float speed = 0f;
        if (this.mGetSpeed != null && this.speedDev != null) {
            try {
                Object o = this.mGetSpeed.invoke(this.speedDev);
                if (o instanceof Number) speed = ((Number) o).floatValue();
            } catch (Throwable ignored) {}
        }

        // Steering Angle
        float steering = 0f;
        if (this.mGetSteering != null && this.bodyDev != null) {
            try {
                Object o = this.mGetSteering.invoke(this.bodyDev, 1);
                if (o instanceof Number) steering = ((Number) o).floatValue();
            } catch (Throwable ignored) {}
        }

        // Gear
        String gearStr = "P / N / D";
        if (this.mGetGearMode != null && this.gearDev != null) {
            try {
                Object o = this.mGetGearMode.invoke(this.gearDev);
                gearStr = String.valueOf(o);
                if (this.mIsInReverse != null) {
                    Object rev = this.mIsInReverse.invoke(this.gearDev);
                    if (Boolean.TRUE.equals(rev)) gearStr += " (REVERSE)";
                }
            } catch (Throwable ignored) {}
        }

        sb.append("• ความเร็วรถ (Speed)       : ").append(String.format(Locale.US, "%.1f km/h", speed)).append("\n");
        sb.append("• องศาพวงมาลัย (Steering) : ").append(String.format(Locale.US, "%.1f°", steering)).append("\n");
        sb.append("• ตำแหน่งเกียร์ (Gear)     : ").append(gearStr);

        return sb.toString();
    }

    private void checkChange(String key, String newVal) {
        if (newVal == null) return;
        Object old = previousValues.get(key);
        if (old != null && !old.equals(newVal)) {
            String time = timeFormat.format(new Date());
            String entry = String.format("[%s] ⚡ %s : %s ➔ %s", time, key, old, newVal);
            synchronized (changeLogs) {
                changeLogs.add(0, entry);
                if (changeLogs.size() > 50) {
                    changeLogs.remove(changeLogs.size() - 1);
                }
            }
        }
        previousValues.put(key, newVal);
    }

    private String buildLogText() {
        synchronized (changeLogs) {
            if (changeLogs.isEmpty()) {
                return "ยังไม่พบการเปลี่ยนแปลงสัญญาณ (พร้อมตรวจจับแบบสด...)";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < changeLogs.size(); i++) {
                sb.append(changeLogs.get(i)).append("\n");
            }
            return sb.toString().trim();
        }
    }

    private Object call(Method method, Object... args) {
        if (method == null || this.lightDevice == null) return "N/A";
        try {
            return method.invoke(this.lightDevice, args);
        } catch (Throwable th) {
            Throwable cause = th.getCause() != null ? th.getCause() : th;
            return cause.getClass().getSimpleName();
        }
    }

    private void testAlert(boolean left, boolean right) {
        OverlayService os = OverlayService.getInstance();
        if (os != null) {
            os.testAdasAlert(left, right, 3000L);
            Toast.makeText(this, "🚨 จำลองเตือน " + (left ? "ซ้าย" : "ขวา") + " 3 วินาที", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "OverlayService ยังไม่ทำงาน", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyReportToClipboard() {
        StringBuilder rep = new StringBuilder("=== BYD SIGNAL PROBE REPORT ===\n\n");
        rep.append("[1. TURN SIGNALS]\n").append(gatherTurnSignals()).append("\n\n");
        rep.append("[2. BSD & RADAR]\n").append(gatherBsdRadar()).append("\n\n");
        rep.append("[3. DAEMON STATUS]\n").append(gatherDaemonStatus()).append("\n\n");
        rep.append("[4. VEHICLE DYNAMICS]\n").append(gatherDynamics()).append("\n\n");
        rep.append("[5. RECENT EVENT LOGS]\n").append(buildLogText()).append("\n");

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("SignalProbeReport", rep.toString()));
            Toast.makeText(this, "📋 คัดลอก Report ทั้งหมดลงคลิปบอร์ดแล้ว", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        this.running = false;
        if (this.workerThread != null) {
            this.workerThread.interrupt();
            this.workerThread = null;
        }
        super.onDestroy();
    }
}
