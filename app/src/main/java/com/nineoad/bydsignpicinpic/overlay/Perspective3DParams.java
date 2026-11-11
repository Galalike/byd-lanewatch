package com.nineoad.bydsignpicinpic.overlay;

import android.content.SharedPreferences;

/**
 * 3D Perspective & Lens Rectification Parameters.
 * Stores optical calibration constants for vehicle side cameras.
 */
public class Perspective3DParams {
    public boolean enabled = true;

    // 3D Perspective & Keystone Parameters
    public float pitchKeystone = 0.12f;
    public float carBodyTilt = 0.50f;
    public float poleTilt = 1.50f;
    public float horizonStraighten = -0.26f;
    public float rollDeg = 5.3f;
    public float cylindrical = 0.0f;
    public float trueLensK1 = -0.28f;
    public float trueLensCx = 0.50f;
    public float trueLensCy = 0.50f;
    public float zoom3D = 2.73f;
    public float panInOut = -0.325f;
    public float panY = -0.87f;
    public float panX = 0.0f;
    public float globalScale = 1.07f;
    public float[] wideCrop = new float[]{0.03f, 0.03f, 0.97f, 0.97f};

    public Perspective3DParams() {
    }

    public Perspective3DParams(Perspective3DParams other) {
        copyFrom(other);
    }

    public void copyFrom(Perspective3DParams other) {
        if (other == null) return;
        this.enabled = other.enabled;
        this.pitchKeystone = other.pitchKeystone;
        this.carBodyTilt = other.carBodyTilt;
        this.poleTilt = other.poleTilt;
        this.horizonStraighten = other.horizonStraighten;
        this.rollDeg = other.rollDeg;
        this.cylindrical = other.cylindrical;
        this.trueLensK1 = other.trueLensK1;
        this.trueLensCx = other.trueLensCx;
        this.trueLensCy = other.trueLensCy;
        this.zoom3D = other.zoom3D;
        this.panInOut = other.panInOut;
        this.panY = other.panY;
        this.panX = other.panX;
        this.globalScale = other.globalScale;
        if (other.wideCrop != null && other.wideCrop.length == 4) {
            System.arraycopy(other.wideCrop, 0, this.wideCrop, 0, 4);
        }
    }

    /**
     * Factory Recommended Preset for BYD ATTO 3 - Left Camera.
     */
    public static Perspective3DParams createAtto3LeftDefault() {
        Perspective3DParams p = new Perspective3DParams();
        p.enabled = true;
        p.pitchKeystone = 0.12f;
        p.carBodyTilt = 0.50f;
        p.poleTilt = 1.50f;
        p.horizonStraighten = -0.26f;
        p.rollDeg = 5.3f;
        p.cylindrical = 0.0f;
        p.trueLensK1 = -0.28f;
        p.zoom3D = 2.73f;
        p.globalScale = 1.07f;
        p.panInOut = -0.325f;
        p.panY = -0.87f;
        p.panX = 0.0f;
        p.wideCrop = new float[]{0.03f, 0.03f, 0.97f, 0.97f};
        return p;
    }

    /**
     * Factory Recommended Preset for BYD ATTO 3 - Right Camera.
     */
    public static Perspective3DParams createAtto3RightDefault() {
        Perspective3DParams p = new Perspective3DParams();
        p.enabled = true;
        p.pitchKeystone = 0.10f;
        p.carBodyTilt = 0.75f;
        p.poleTilt = 1.74f;
        p.horizonStraighten = -0.34f;
        p.rollDeg = -15.0f;
        p.cylindrical = 0.26f;
        p.trueLensK1 = -0.30f;
        p.zoom3D = 2.20f;
        p.globalScale = 1.35f;
        p.panInOut = -0.33f;
        p.panY = -1.07f;
        p.panX = 0.025f;
        p.wideCrop = new float[]{0.08f, 0.07f, 0.92f, 0.93f};
        return p;
    }

    public void save(SharedPreferences.Editor ed, String prefix) {
        if (ed == null) return;
        ed.putBoolean(prefix + "_3d_enabled", enabled)
          .putFloat(prefix + "_pitchKeystone", pitchKeystone)
          .putFloat(prefix + "_carBodyTilt", carBodyTilt)
          .putFloat(prefix + "_poleTilt", poleTilt)
          .putFloat(prefix + "_horizonStraighten", horizonStraighten)
          .putFloat(prefix + "_rollDeg", rollDeg)
          .putFloat(prefix + "_cylindrical", cylindrical)
          .putFloat(prefix + "_trueLensK1", trueLensK1)
          .putFloat(prefix + "_zoom3D", zoom3D)
          .putFloat(prefix + "_globalScale", globalScale)
          .putFloat(prefix + "_panInOut", panInOut)
          .putFloat(prefix + "_panY", panY)
          .putFloat(prefix + "_panX", panX)
          .putFloat(prefix + "_w0", wideCrop[0])
          .putFloat(prefix + "_w1", wideCrop[1])
          .putFloat(prefix + "_w2", wideCrop[2])
          .putFloat(prefix + "_w3", wideCrop[3]);
    }

    public void load(SharedPreferences sp, String prefix, Perspective3DParams fallback) {
        if (sp == null) return;
        Perspective3DParams def = fallback != null ? fallback : new Perspective3DParams();
        this.enabled = sp.getBoolean(prefix + "_3d_enabled", def.enabled);
        this.pitchKeystone = sp.getFloat(prefix + "_pitchKeystone", def.pitchKeystone);
        this.carBodyTilt = sp.getFloat(prefix + "_carBodyTilt", def.carBodyTilt);
        this.poleTilt = sp.getFloat(prefix + "_poleTilt", def.poleTilt);
        this.horizonStraighten = sp.getFloat(prefix + "_horizonStraighten", def.horizonStraighten);
        this.rollDeg = sp.getFloat(prefix + "_rollDeg", def.rollDeg);
        this.cylindrical = sp.getFloat(prefix + "_cylindrical", def.cylindrical);
        this.trueLensK1 = sp.getFloat(prefix + "_trueLensK1", def.trueLensK1);
        this.zoom3D = sp.getFloat(prefix + "_zoom3D", def.zoom3D);
        this.globalScale = sp.getFloat(prefix + "_globalScale", def.globalScale);
        this.panInOut = sp.getFloat(prefix + "_panInOut", def.panInOut);
        this.panY = sp.getFloat(prefix + "_panY", def.panY);
        this.panX = sp.getFloat(prefix + "_panX", def.panX);
        this.wideCrop[0] = sp.getFloat(prefix + "_w0", def.wideCrop[0]);
        this.wideCrop[1] = sp.getFloat(prefix + "_w1", def.wideCrop[1]);
        this.wideCrop[2] = sp.getFloat(prefix + "_w2", def.wideCrop[2]);
        this.wideCrop[3] = sp.getFloat(prefix + "_w3", def.wideCrop[3]);
    }
}
