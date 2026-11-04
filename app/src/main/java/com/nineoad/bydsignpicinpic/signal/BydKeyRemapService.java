package com.nineoad.bydsignpicinpic.signal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.nineoad.bydsignpicinpic.overlay.OverlayService;
import java.util.List;

public class BydKeyRemapService extends AccessibilityService {
    private static final String TAG = "BydKeyRemap";
    public static volatile BydKeyRemapService instance = null;
    public static volatile boolean isServiceRunning = false;
    public static volatile boolean isCapturing = false;
    public static volatile KeyCaptureListener captureListener = null;

    public static volatile boolean isHandlingDisableAutostart = false;
    private static int autostartScrollAttempts = 0;
    private static final Handler autostartHandler = new Handler(Looper.getMainLooper());
    private static Runnable autostartTimeoutRunnable = null;

    private static long lastPressTime = 0;
    private static final long DEBOUNCE_MS = 120L; // Swallows mechanical contact bounce

    private static long lastClickInOpenStateTime = 0;
    private static final long DOUBLE_CLICK_WINDOW_MS = 500L; // 500ms window for 2 clicks to close
    private static long lastOpenTriggerTime = 0;
    private static final long OPEN_GRACE_PERIOD_MS = 600L; // Prevents accidental double click immediately after opening

    public interface KeyCaptureListener {
        void onKeyCaptured(int keyCode, String keyName);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        isServiceRunning = true;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                info = new AccessibilityServiceInfo();
            }
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 100;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
            Log.i(TAG, "BydKeyRemapService connected, listening for steering wheel buttons & autostart automation");
            checkAutoStartOnServiceConnected();
        } catch (Throwable th) {
            Log.e(TAG, "onServiceConnected error: " + th);
        }
    }

    private void checkAutoStartOnServiceConnected() {
        SharedPreferences sp = getSharedPreferences("pnp", Context.MODE_PRIVATE);
        int lastVer = sp.getInt("last_disabled_autostart_version", -1);
        int curVer = getAppVersionCode(this);
        boolean pending = sp.getBoolean("pending_autostart_unblock", false);
        if (pending || lastVer != curVer) {
            Log.i(TAG, "Update detected on service connected (pending=" + pending + ", last=" + lastVer + ", cur=" + curVer + ") -> Triggering autostart unlock immediately");
            sp.edit().putBoolean("pending_autostart_unblock", true).apply();
            autostartHandler.postDelayed(() -> triggerDisableAutostartAutomation(this), 600L);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        isServiceRunning = false;
        Log.i(TAG, "BydKeyRemapService destroyed");
    }

    private final Runnable processAutostartRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isHandlingDisableAutostart) return;
            processAppStartManagement();
        }
    };

    public static void triggerDisableAutostartAutomation(Context context) {
        if (!isServiceRunning || instance == null) {
            Log.w(TAG, "BydKeyRemapService not yet active, scheduling retry waiting for service...");
            autostartHandler.postDelayed(new Runnable() {
                int attempts = 0;
                @Override
                public void run() {
                    if (isServiceRunning && instance != null) {
                        Log.i(TAG, "BydKeyRemapService now connected! Running automation immediately");
                        triggerDisableAutostartAutomation(context);
                    } else if (attempts < 20) {
                        attempts++;
                        autostartHandler.postDelayed(this, 500L);
                    } else {
                        Log.w(TAG, "Accessibility service did not connect in time");
                    }
                }
            }, 500L);
            return;
        }
        isHandlingDisableAutostart = true;
        autostartScrollAttempts = 0;
        Log.i(TAG, "Triggering BYD AppStartManagement automation immediately...");

        if (autostartTimeoutRunnable != null) {
            autostartHandler.removeCallbacks(autostartTimeoutRunnable);
        }
        autostartTimeoutRunnable = () -> {
            if (isHandlingDisableAutostart) {
                Log.w(TAG, "Disable autostart automation timed out -> resetting flag");
                isHandlingDisableAutostart = false;
            }
        };
        autostartHandler.postDelayed(autostartTimeoutRunnable, 15000L);

        try {
            Intent intent = new Intent("android.intent.action.BYD_APPSTARTMANAGEMENT");
            intent.setClassName("com.byd.appstartmanagement", "com.byd.appstartmanagement.frame.AppStartManagement");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (instance != null) {
                instance.startActivity(intent);
            } else if (context != null) {
                context.startActivity(intent);
            }
            // Ensure first processing pass starts even if window state change event is coalesced
            autostartHandler.removeCallbacks(instance.processAutostartRunnable);
            autostartHandler.postDelayed(instance.processAutostartRunnable, 800L);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to launch AppStartManagement: " + t);
            isHandlingDisableAutostart = false;
        }
    }

    private void processAppStartManagement() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                if (isHandlingDisableAutostart) {
                    autostartHandler.postDelayed(processAutostartRunnable, 250L);
                }
                return;
            }

            // Search for "BYD LaneWatch" (or fallback "LaneWatch")
            List<AccessibilityNodeInfo> targets = root.findAccessibilityNodeInfosByText("BYD LaneWatch");
            if (targets == null || targets.isEmpty()) {
                targets = root.findAccessibilityNodeInfosByText("LaneWatch");
            }

            if (targets != null && !targets.isEmpty()) {
                AccessibilityNodeInfo textNode = targets.get(0);
                AccessibilityNodeInfo row = textNode.getParent();
                if (row != null) {
                    boolean isCurrentlyOn = false;
                    AccessibilityNodeInfo switchNode = findSwitchChild(row);
                    if (switchNode != null) {
                        CharSequence txt = switchNode.getText();
                        String txtStr = txt != null ? txt.toString().trim() : "";
                        isCurrentlyOn = switchNode.isChecked() 
                                || "ON".equalsIgnoreCase(txtStr) 
                                || "开".equals(txtStr) 
                                || "เปิด".equalsIgnoreCase(txtStr);
                    }

                    if (isCurrentlyOn) {
                        Log.i(TAG, "Automation: Found BYD LaneWatch with Disable Autostart ON -> Clicking row/switch to turn OFF");
                        boolean clicked = false;
                        if (switchNode != null && switchNode.isClickable()) {
                            clicked = switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                        if (!clicked) {
                            row.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                    } else {
                        Log.i(TAG, "Automation: BYD LaneWatch Disable Autostart is already OFF");
                    }

                    // Success! Mark completed and reset pending flag
                    isHandlingDisableAutostart = false;
                    if (autostartTimeoutRunnable != null) {
                        autostartHandler.removeCallbacks(autostartTimeoutRunnable);
                    }
                    SharedPreferences prefs = getSharedPreferences("pnp", Context.MODE_PRIVATE);
                    prefs.edit()
                            .putBoolean("pending_autostart_unblock", false)
                            .putInt("last_disabled_autostart_version", getAppVersionCode(this))
                            .apply();

                    // Smoothly dismiss dialog
                    autostartHandler.postDelayed(() -> {
                        AccessibilityNodeInfo closeRoot = getRootInActiveWindow();
                        if (closeRoot != null) {
                            List<AccessibilityNodeInfo> closeBtns = closeRoot.findAccessibilityNodeInfosByViewId("com.byd.appstartmanagement:id/iv_close_right");
                            if (closeBtns != null && !closeBtns.isEmpty()) {
                                closeBtns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                return;
                            }
                        }
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }, 400L);
                    return;
                }
            }

            // Not yet visible on screen -> scroll ListView using physical swipe gesture
            if (autostartScrollAttempts < 12) {
                autostartScrollAttempts++;
                List<AccessibilityNodeInfo> listViews = root.findAccessibilityNodeInfosByViewId("com.byd.appstartmanagement:id/installed_app");
                AccessibilityNodeInfo listNode = (listViews != null && !listViews.isEmpty()) ? listViews.get(0) : null;
                Log.i(TAG, "Automation: Scrolling ListView with gesture to find BYD LaneWatch (attempt " + autostartScrollAttempts + "/12)");
                performScrollGesture(listNode);
                return;
            }

            Log.w(TAG, "Automation: Exceeded scroll attempts without finding BYD LaneWatch");
            isHandlingDisableAutostart = false;
            SharedPreferences prefs = getSharedPreferences("pnp", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("pending_autostart_unblock", false).apply();
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Throwable t) {
            Log.e(TAG, "processAppStartManagement error: " + t);
            isHandlingDisableAutostart = false;
        }
    }

    private void performScrollGesture(AccessibilityNodeInfo listNode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            float startX = 981f;
            float startY = 780f;
            float endX = 981f;
            float endY = 320f;

            if (listNode != null) {
                Rect rect = new Rect();
                listNode.getBoundsInScreen(rect);
                if (rect.width() > 100 && rect.height() > 200) {
                    startX = rect.centerX();
                    endX = rect.centerX();
                    startY = rect.bottom - (rect.height() * 0.18f);
                    endY = rect.top + (rect.height() * 0.18f);
                }
            }

            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 280L));
            GestureDescription gesture = builder.build();

            boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.i(TAG, "Scroll gesture completed -> schedule next check in 450ms");
                    autostartHandler.postDelayed(processAutostartRunnable, 450L);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.w(TAG, "Scroll gesture cancelled -> retrying in 450ms");
                    autostartHandler.postDelayed(processAutostartRunnable, 450L);
                }
            }, autostartHandler);

            if (!dispatched) {
                Log.w(TAG, "dispatchGesture returned false, trying fallback ACTION_SCROLL_FORWARD");
                if (listNode != null) {
                    listNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                }
                autostartHandler.postDelayed(processAutostartRunnable, 500L);
            }
        } else {
            if (listNode != null) {
                listNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            }
            autostartHandler.postDelayed(processAutostartRunnable, 500L);
        }
    }

    private AccessibilityNodeInfo findSwitchChild(AccessibilityNodeInfo parent) {
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child != null) {
                if ("com.byd.appstartmanagement:id/select_button".equals(child.getViewIdResourceName())
                        || (child.getClassName() != null && child.getClassName().toString().contains("Switch"))
                        || (child.getClassName() != null && child.getClassName().toString().contains("CheckBox"))) {
                    return child;
                }
            }
        }
        return null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence pkgName = event.getPackageName();
        String pkg = pkgName != null ? pkgName.toString() : "";

        // Autostart automation interceptor
        if (isHandlingDisableAutostart && "com.byd.appstartmanagement".equals(pkg)) {
            autostartHandler.removeCallbacks(processAutostartRunnable);
            autostartHandler.postDelayed(processAutostartRunnable, 250L);
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkgName == null) {
                return;
            }
            String lowerPkg = pkg.toLowerCase();

            // Ignore system overlays, notification shade, toasts, and our own app
            if (lowerPkg.equals("com.android.systemui") || lowerPkg.equals("android") || lowerPkg.equals(getPackageName().toLowerCase())) {
                return;
            }

            CharSequence clsName = event.getClassName();
            String cls = clsName != null ? clsName.toString() : "";
            boolean isCamera = isCameraPkg(pkg, cls);
            Log.i(TAG, "Window state changed: " + pkg + " / " + cls + " -> isOemCamera=" + isCamera);
            OverlayService.setOemCameraForeground(isCamera);
        }
    }

    public static int getAppVersionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return 1;
        }
    }

    public static boolean isCameraPkg(String pkg, String clsName) {
        if (pkg == null) return false;
        String lowerPkg = pkg.toLowerCase();
        String lowerCls = clsName != null ? clsName.toLowerCase() : "";

        if (lowerPkg.contains("lanewatch") || lowerPkg.contains("bydsignpicinpic")) {
            return false;
        }

        switch (lowerPkg) {
            case "com.byd.panorama":
            case "com.byd.cameramanager":
            case "com.byd.auto_camera":
            case "com.byd.bydcamera":
            case "com.byd.avc":
            case "com.byd.avm":
            case "com.byd.carcamera":
            case "com.byd.aroundview":
            case "com.byd.surroundview":
            case "com.byd.panoramaview":
                return true;
            default:
                if (lowerPkg.startsWith("com.byd") || lowerPkg.startsWith("com.bydauto")) {
                    if (lowerPkg.contains("avm") || lowerPkg.contains("avc") ||
                        lowerPkg.contains("panorama") || lowerPkg.contains("pano") ||
                        lowerPkg.contains("camera") || lowerPkg.contains("aroundview") ||
                        lowerPkg.contains("around") || lowerPkg.contains("surround") ||
                        lowerPkg.contains("360")) {
                        return true;
                    }
                }
                if (lowerCls.contains("panorama") || lowerCls.contains("aroundview") ||
                    lowerCls.contains("avmactivity") || lowerCls.contains("avcactivity")) {
                    return true;
                }
                return false;
        }
    }

    @Override
    public void onInterrupt() {
        // Not used
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }

        int keyCode = event.getKeyCode();
        int action = event.getAction();
        Log.i(TAG, "onKeyEvent received: keyCode=" + keyCode + " (" + KeyEvent.keyCodeToString(keyCode) + ") action=" + (action == KeyEvent.ACTION_DOWN ? "DOWN" : (action == KeyEvent.ACTION_UP ? "UP" : String.valueOf(action))) + " isCapturing=" + isCapturing);

        // 1. If currently in Capture Mode (user pressed "จับปุ่ม" in MainActivity)
        if (isCapturing) {
            if (action == KeyEvent.ACTION_UP) {
                isCapturing = false;
                String keyName = KeyEvent.keyCodeToString(keyCode);
                Log.i(TAG, "Successfully captured steering button: " + keyName + " (" + keyCode + ")");
                try {
                    SharedPreferences prefs = getSharedPreferences("pnp", Context.MODE_PRIVATE);
                    prefs.edit()
                        .putInt("remap_rear_keycode", keyCode)
                        .putString("remap_rear_keyname", keyName)
                        .putBoolean("remap_rear_enabled", true)
                        .apply();
                } catch (Throwable th) {
                    Log.e(TAG, "failed saving captured key: " + th);
                }
                if (captureListener != null) {
                    captureListener.onKeyCaptured(keyCode, keyName);
                }
            }
            return true; // Consume event during key learning
        }

        // 2. Normal Mode: check if key matches remapped button
        try {
            SharedPreferences prefs = getSharedPreferences("pnp", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("remap_rear_enabled", false);
            int targetKeyCode = prefs.getInt("remap_rear_keycode", -1);

            if (enabled && targetKeyCode != -1 && keyCode == targetKeyCode) {
                if (action == KeyEvent.ACTION_UP) {
                    long now = System.currentTimeMillis();
                    // Debounce mechanical bounce
                    if (now - lastPressTime < DEBOUNCE_MS) {
                        Log.d(TAG, "Ignored steering wheel bounce within debounce window");
                        return true;
                    }
                    lastPressTime = now;

                    boolean isShowing = OverlayService.isRearviewShowing();

                    if (!isShowing) {
                        // กด 1 ครั้งเพื่อเปิด (Press 1 time to OPEN)
                        Log.i(TAG, "Rearview is closed -> 1 press: OPENING digital rearview mirror");
                        lastOpenTriggerTime = now;
                        lastClickInOpenStateTime = 0;
                        sendRearAction(OverlayService.ACTION_SHOW_REAR);
                    } else {
                        // Rearview is currently OPEN: กด 2 ครั้งเพื่อปิด (Press 2 times to CLOSE)
                        if (now - lastOpenTriggerTime < OPEN_GRACE_PERIOD_MS) {
                            Log.d(TAG, "Ignored press during open grace period");
                            return true;
                        }

                        if (now - lastClickInOpenStateTime <= DOUBLE_CLICK_WINDOW_MS) {
                            // กดครั้งที่ 2 ภายใน 500ms -> ปิด!
                            Log.i(TAG, "Double click detected while open -> CLOSING digital rearview mirror");
                            lastClickInOpenStateTime = 0;
                            sendRearAction(OverlayService.ACTION_HIDE_REAR);
                        } else {
                            // กดครั้งที่ 1 ขณะเปิดอยู่ -> บันทึกเวลาเพื่อรอการกดครั้งที่ 2
                            Log.i(TAG, "1st press while open -> waiting for 2nd press within " + DOUBLE_CLICK_WINDOW_MS + "ms to close");
                            lastClickInOpenStateTime = now;
                        }
                    }
                }
                return true; // Consume event so vehicle default action doesn't trigger
            }
        } catch (Throwable th) {
            Log.e(TAG, "onKeyEvent error: " + th);
        }

        return super.onKeyEvent(event);
    }

    private void sendRearAction(String action) {
        try {
            Intent intent = new Intent(this, OverlayService.class);
            intent.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "sendRearAction failed: " + t);
        }
    }
}
