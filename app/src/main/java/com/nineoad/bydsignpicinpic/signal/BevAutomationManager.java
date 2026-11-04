package com.nineoad.bydsignpicinpic.signal;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * Handles EV-style automations for Bird's Eye View (BEV) 3D Top-Down Camera:
 * 1. Auto-show on shifting out of Park (P) gear (e.g. into D, R, N).
 * 2. Auto-show when steering wheel is turned past N degrees in Drive (D) gear.
 * 3. Auto-hide when vehicle speed exceeds threshold (e.g. > 20 km/h) or after timeout / steering center.
 */
public class BevAutomationManager {
    private static final String TAG = "BevAutomation";

    public static final int TRIGGER_NONE = 0;
    public static final int TRIGGER_EXIT_PARK = 1;
    public static final int TRIGGER_STEERING = 2;
    public static final int TRIGGER_MANUAL = 3;

    public static final int GEAR_UNKNOWN = 0;
    public static final int GEAR_P = 1;
    public static final int GEAR_R = 2;
    public static final int GEAR_N = 3;
    public static final int GEAR_D = 4;

    private final Context ctx;
    private final Callback callback;

    // BYD Hardware Devices via Reflection
    private Object gearDev;
    private Method mGetGearMode;
    private Method mIsInReverse;

    private Object bodyDev;
    private Method mGetSteering;

    private Object speedDev;
    private Method mGetSpeed;

    // Configurable Preferences
    private boolean enableExitPark = true;
    private boolean enableSteering = true;
    private int steeringThresholdDeg = 90;
    private boolean enableSpeedLimit = true;
    private float speedLimitKmh = 20.0f;
    private long exitParkDurationMs = 8000L;
    private long steeringLingerMs = 3500L;

    // Runtime Tracking State
    private int lastGear = GEAR_UNKNOWN;
    private int activeTrigger = TRIGGER_NONE;
    private long exitParkTriggerTime = 0L;
    private long lastSteeringActiveTime = 0L;
    private boolean initialized = false;

    // Diagnostics / Live Values
    private double currentSteeringAngle = 0.0;
    private double currentSpeedKmh = 0.0;
    private int currentGear = GEAR_UNKNOWN;
    private int currentSteeringSide = 0; // 0 = Center, 1 = Left (FL), 2 = Right (FR)

    public interface Callback {
        void onBevTriggerShow(int triggerReason);
        void onBevTriggerHide(int triggerReason);
        void onWheelTriggerShow(int side); // 1 = Left (FL), 2 = Right (FR)
        void onWheelTriggerHide();
    }

    public BevAutomationManager(Context context, Callback callback) {
        this.ctx = context.getApplicationContext();
        this.callback = callback;
        initDevices();
        reloadPreferences();
    }

    public void reloadPreferences() {
        try {
            SharedPreferences sp = this.ctx.getSharedPreferences("pnp", Context.MODE_PRIVATE);
            this.enableExitPark = sp.getBoolean("bev_auto_exit_park", true);
            this.enableSteering = sp.getBoolean("bev_auto_steering", true);
            try {
                this.steeringThresholdDeg = sp.getInt("bev_steering_angle", 90);
            } catch (ClassCastException e) {
                this.steeringThresholdDeg = Math.round(sp.getFloat("bev_steering_angle", 90.0f));
            }
            this.enableSpeedLimit = sp.getBoolean("bev_auto_speed_limit", true);
            try {
                this.speedLimitKmh = sp.getFloat("bev_speed_limit_kmh", 20.0f);
            } catch (ClassCastException e) {
                this.speedLimitKmh = (float) sp.getInt("bev_speed_limit_kmh", 20);
            }
            try {
                this.exitParkDurationMs = (long) (sp.getFloat("bev_exit_park_duration_sec", 8.0f) * 1000.0f);
            } catch (ClassCastException e) {
                this.exitParkDurationMs = (long) (sp.getInt("bev_exit_park_duration_sec", 8) * 1000L);
            }
            try {
                this.steeringLingerMs = (long) (sp.getFloat("lingerSec", 3.5f) * 1000.0f);
            } catch (ClassCastException e) {
                this.steeringLingerMs = (long) (sp.getInt("lingerSec", 3) * 1000L);
            }

            Log.i(TAG, "Prefs reloaded: exitPark=" + enableExitPark +
                    ", steering=" + enableSteering + " (" + steeringThresholdDeg + "°)" +
                    ", speedLimit=" + enableSpeedLimit + " (" + speedLimitKmh + " km/h)" +
                    ", exitDuration=" + exitParkDurationMs + "ms");
        } catch (Throwable t) {
            Log.w(TAG, "reloadPreferences error: " + t);
        }
    }

    private void initDevices() {
        CarContext carContext = new CarContext(this.ctx);

        // 1. Gearbox Device
        try {
            Class<?> clsGear = Class.forName("android.hardware.bydauto.gearbox.BYDAutoGearboxDevice");
            this.gearDev = clsGear.getMethod("getInstance", Context.class).invoke(null, carContext);
            if (this.gearDev != null) {
                try {
                    this.gearDev.getClass().getMethod("getAllStatus").invoke(this.gearDev);
                } catch (Throwable ignored) {}
                this.mGetGearMode = clsGear.getMethod("getGearboxAutoModeType");
                this.mIsInReverse = clsGear.getMethod("isInReverseGear");
                Log.i(TAG, "BYDAutoGearboxDevice ready: " + this.gearDev);
            }
        } catch (Throwable t) {
            Log.w(TAG, "init Gearbox failed: " + t);
        }

        // 2. Bodywork Device (Steering Wheel Angle)
        try {
            Class<?> clsBody = Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice");
            this.bodyDev = clsBody.getMethod("getInstance", Context.class).invoke(null, carContext);
            if (this.bodyDev != null) {
                try {
                    this.bodyDev.getClass().getMethod("getAllStatus").invoke(this.bodyDev);
                } catch (Throwable ignored) {}
                this.mGetSteering = clsBody.getMethod("getSteeringWheelValue", Integer.TYPE);
                Log.i(TAG, "BYDAutoBodyworkDevice ready: " + this.bodyDev);
            }
        } catch (Throwable t) {
            Log.w(TAG, "init Bodywork failed: " + t);
        }

        // 3. Speed Device
        try {
            Class<?> clsSpeed = Class.forName("android.hardware.bydauto.speed.BYDAutoSpeedDevice");
            this.speedDev = clsSpeed.getMethod("getInstance", Context.class).invoke(null, carContext);
            if (this.speedDev != null) {
                try {
                    this.speedDev.getClass().getMethod("getAllStatus").invoke(this.speedDev);
                } catch (Throwable ignored) {}
                this.mGetSpeed = clsSpeed.getMethod("getCurrentSpeed");
                Log.i(TAG, "BYDAutoSpeedDevice ready: " + this.speedDev);
            }
        } catch (Throwable t) {
            Log.w(TAG, "init Speed failed: " + t);
        }

        this.initialized = true;
    }

    /**
     * Polls current sensor values and updates automation triggers.
     * Called periodically (e.g. every 40-50ms).
     *
     * @param oemCameraActive true if OEM reverse camera or 360 app is on screen.
     * @param turnSignalActive true if turn signal (left/right/hazard) is currently active.
     */
    public void poll(boolean oemCameraActive, boolean turnSignalActive) {
        int gear = queryGear();
        double angle = querySteeringAngle();
        double speed = querySpeed();

        this.currentGear = gear;
        this.currentSteeringAngle = angle;
        this.currentSpeedKmh = speed;

        long now = System.currentTimeMillis();

        // 1. Check Exit Park Trigger
        if (this.lastGear == GEAR_P && gear != GEAR_P && gear != GEAR_UNKNOWN) {
            // Gear transitioned from P -> D, R, or N
            Log.i(TAG, "Gear transitioned from P to " + gear);
            if (this.enableExitPark && !oemCameraActive && !turnSignalActive) {
                Log.i(TAG, "Triggering BEV: Exit Park detected!");
                this.activeTrigger = TRIGGER_EXIT_PARK;
                this.exitParkTriggerTime = now;
                if (this.callback != null) {
                    this.callback.onBevTriggerShow(TRIGGER_EXIT_PARK);
                }
            }
        }
        this.lastGear = gear;

        // 2. Handle active Exit Park state
        if (this.activeTrigger == TRIGGER_EXIT_PARK) {
            if (gear == GEAR_P) {
                // Shifted back to Park: dismiss immediately
                Log.i(TAG, "Exit Park dismissed: vehicle shifted back to P");
                this.activeTrigger = TRIGGER_NONE;
                if (this.callback != null) {
                    this.callback.onBevTriggerHide(TRIGGER_EXIT_PARK);
                }
            } else if (this.enableSpeedLimit && speed > this.speedLimitKmh) {
                // Vehicle reached cruising speed: dismiss
                Log.i(TAG, "Exit Park dismissed: speed (" + speed + ") > limit (" + this.speedLimitKmh + ")");
                this.activeTrigger = TRIGGER_NONE;
                if (this.callback != null) {
                    this.callback.onBevTriggerHide(TRIGGER_EXIT_PARK);
                }
            } else if (now - this.exitParkTriggerTime > this.exitParkDurationMs) {
                // Timeout reached
                Log.i(TAG, "Exit Park dismissed: timeout reached (" + (this.exitParkDurationMs / 1000) + "s)");
                this.activeTrigger = TRIGGER_NONE;
                if (this.callback != null) {
                    this.callback.onBevTriggerHide(TRIGGER_EXIT_PARK);
                }
            }
        }

        // 3. Handle Steering Wheel Trigger for Front Wheels (FL / FR)
        double absAngle = Math.abs(angle);
        boolean speedAllowed = !this.enableSpeedLimit || speed <= this.speedLimitKmh;

        int detectedWheelSide = 0;
        if (this.enableSteering && speedAllowed && !oemCameraActive) {
            // In BYD DiLink CAN bus:
            // Negative angle (< 0, Clockwise) = Right turn -> FR (side 2)
            // Positive angle (> 0, Counter-Clockwise) = Left turn -> FL (side 1)
            if (angle <= -this.steeringThresholdDeg) {
                detectedWheelSide = 2; // Right (FR)
            } else if (angle >= this.steeringThresholdDeg) {
                detectedWheelSide = 1; // Left (FL)
            }
        }

        if (detectedWheelSide != 0) {
            this.lastSteeringActiveTime = now;
            this.activeTrigger = TRIGGER_STEERING;
            if (this.currentSteeringSide != detectedWheelSide) {
                this.currentSteeringSide = detectedWheelSide;
                Log.i(TAG, "Steering wheel trigger side=" + detectedWheelSide + " (angle=" + angle + "° threshold=" + this.steeringThresholdDeg + "°)");
                if (this.callback != null) {
                    this.callback.onWheelTriggerShow(detectedWheelSide);
                }
            }
        } else if (this.currentSteeringSide != 0) {
            if (!speedAllowed || oemCameraActive) {
                this.currentSteeringSide = 0;
                if (this.activeTrigger == TRIGGER_STEERING) {
                    this.activeTrigger = TRIGGER_NONE;
                }
                if (this.callback != null) {
                    this.callback.onWheelTriggerHide();
                }
            } else if (absAngle >= (this.steeringThresholdDeg - 15.0)) {
                // Wheel still turned past hysteresis threshold: refresh linger timer
                this.lastSteeringActiveTime = now;
            } else {
                // Wheel returned near center: check linger timeout
                if (now - this.lastSteeringActiveTime > this.steeringLingerMs) {
                    Log.i(TAG, "Steering trigger dismissed: linger expired (" + this.steeringLingerMs + "ms)");
                    this.currentSteeringSide = 0;
                    if (this.activeTrigger == TRIGGER_STEERING) {
                        this.activeTrigger = TRIGGER_NONE;
                    }
                    if (this.callback != null) {
                        this.callback.onWheelTriggerHide();
                    }
                }
            }
        }
    }

    public void setManualActive(boolean active) {
        if (active) {
            this.activeTrigger = TRIGGER_MANUAL;
        } else if (this.activeTrigger == TRIGGER_MANUAL) {
            this.activeTrigger = TRIGGER_NONE;
        }
    }

    public boolean isBevRequested() {
        return this.activeTrigger != TRIGGER_NONE;
    }

    public int getActiveTrigger() {
        return this.activeTrigger;
    }

    public int getGear() {
        return this.currentGear;
    }

    public double getSteeringAngle() {
        return this.currentSteeringAngle;
    }

    public int getCurrentSteeringSide() {
        return this.currentSteeringSide;
    }

    public double getSpeed() {
        return this.currentSpeedKmh;
    }

    private int queryGear() {
        if (this.gearDev != null && this.mGetGearMode != null) {
            try {
                Object res = this.mGetGearMode.invoke(this.gearDev);
                if (res instanceof Integer) {
                    return ((Integer) res).intValue();
                }
            } catch (Throwable ignored) {}
        }
        if (this.gearDev != null && this.mIsInReverse != null) {
            try {
                Object res = this.mIsInReverse.invoke(this.gearDev);
                if (Boolean.TRUE.equals(res)) {
                    return GEAR_R;
                }
            } catch (Throwable ignored) {}
        }
        return GEAR_UNKNOWN;
    }

    private double querySteeringAngle() {
        if (this.bodyDev != null && this.mGetSteering != null) {
            try {
                // 1 = BODYWORK_CMD_STEERING_WHEEL_ANGEL
                Object res = this.mGetSteering.invoke(this.bodyDev, 1);
                if (res instanceof Number) {
                    return ((Number) res).doubleValue();
                }
            } catch (Throwable ignored) {}
        }
        return 0.0;
    }

    private double querySpeed() {
        if (this.speedDev != null && this.mGetSpeed != null) {
            try {
                Object res = this.mGetSpeed.invoke(this.speedDev);
                if (res instanceof Number) {
                    return ((Number) res).doubleValue();
                }
            } catch (Throwable ignored) {}
        }
        return 0.0;
    }
}
