package com.nineoad.bydsignpicinpic.overlay;

import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import org.json.JSONObject;

/**
 * 3D Perspective & Lens Rectification Parameters.
 * Stores optical calibration constants for vehicle side cameras.
 */
public class Perspective3DParams {
    private static final String TAG = "Perspective3DParams";

    public boolean enabled = true;

    // 3D Perspective & Keystone Parameters
    public float pitchKeystone = 0.12f;
    public float carBodyTilt = 0.53f;
    public float poleTilt = 1.69f;
    public float horizonStraighten = -0.26f;
    public float rollDeg = 7.5f;
    public float cylindrical = 0.0f;
    public float trueLensK1 = -0.28f;
    public float trueLensCx = 0.50f;
    public float trueLensCy = 0.50f;
    public float zoom3D = 2.71f;
    public float panInOut = -0.325f;
    public float panY = -0.83f;
    public float panX = 0.0f;
    public float globalScale = 1.06f;
    public float[] wideCrop = new float[]{0.04f, 0.01f, 0.96f, 0.99f};

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

    public float getSensorWidth() {
        return Math.max(0.10f, Math.min(1.00f, wideCrop[2] - wideCrop[0]));
    }

    public float getSensorHeight() {
        return Math.max(0.10f, Math.min(1.00f, wideCrop[3] - wideCrop[1]));
    }

    public void setSensorWidth(float width) {
        float half = Math.max(0.05f, Math.min(0.50f, width * 0.5f));
        wideCrop[0] = Math.max(0.0f, 0.50f - half);
        wideCrop[2] = Math.min(1.0f, 0.50f + half);
    }

    public void setSensorHeight(float height) {
        float half = Math.max(0.05f, Math.min(0.50f, height * 0.5f));
        wideCrop[1] = Math.max(0.0f, 0.50f - half);
        wideCrop[3] = Math.min(1.0f, 0.50f + half);
    }

    /**
     * Factory Recommended Preset for BYD ATTO 3 - Left Camera.
     */
    public static Perspective3DParams createAtto3LeftDefault() {
        Perspective3DParams p = new Perspective3DParams();
        p.enabled = true;
        p.pitchKeystone = 0.12f;
        p.carBodyTilt = 0.53f;
        p.poleTilt = 1.69f;
        p.horizonStraighten = -0.26f;
        p.rollDeg = 7.5f;
        p.cylindrical = 0.0f;
        p.trueLensK1 = -0.28f;
        p.zoom3D = 2.71f;
        p.globalScale = 1.06f;
        p.panInOut = -0.325f;
        p.panY = -0.83f;
        p.panX = 0.0f;
        p.wideCrop = new float[]{0.04f, 0.01f, 0.96f, 0.99f};
        return p;
    }

    /**
     * Factory Recommended Preset for BYD ATTO 3 - Right Camera.
     */
    public static Perspective3DParams createAtto3RightDefault() {
        Perspective3DParams p = new Perspective3DParams();
        p.enabled = true;
        p.pitchKeystone = 0.10f;
        p.carBodyTilt = 0.77f;
        p.poleTilt = 1.91f;
        p.horizonStraighten = -0.34f;
        p.rollDeg = -13.9f;
        p.cylindrical = 0.0f;
        p.trueLensK1 = -0.32f;
        p.zoom3D = 3.09f;
        p.globalScale = 1.03f;
        p.panInOut = -0.325f;
        p.panY = -0.775f;
        p.panX = 0.03f;
        p.wideCrop = new float[]{0.09f, 0.04f, 0.91f, 0.96f};
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
        this.pitchKeystone = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_pitchKeystone", def.pitchKeystone);
        this.carBodyTilt = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_carBodyTilt", def.carBodyTilt);
        this.poleTilt = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_poleTilt", def.poleTilt);
        this.horizonStraighten = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_horizonStraighten", def.horizonStraighten);
        this.rollDeg = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_rollDeg", def.rollDeg);
        this.cylindrical = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_cylindrical", def.cylindrical);
        this.trueLensK1 = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_trueLensK1", def.trueLensK1);
        this.zoom3D = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_zoom3D", def.zoom3D);
        this.globalScale = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_globalScale", def.globalScale);
        this.panInOut = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_panInOut", def.panInOut);
        this.panY = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_panY", def.panY);
        this.panX = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_panX", def.panX);
        this.wideCrop[0] = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_w0", def.wideCrop[0]);
        this.wideCrop[1] = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_w1", def.wideCrop[1]);
        this.wideCrop[2] = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_w2", def.wideCrop[2]);
        this.wideCrop[3] = com.nineoad.bydsignpicinpic.config.PrefsHelper.getFloat(sp, prefix + "_w3", def.wideCrop[3]);
    }

    /**
     * Imports configuration from Downloads/byd_fisheye3d_lab.json into SharedPreferences.
     */
    public static boolean importFromLabJsonFile(SharedPreferences sp) {
        if (sp == null) return false;
        try {
            File inFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "byd_fisheye3d_lab.json");
            if (!inFile.exists()) return false;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(inFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject root = new JSONObject(sb.toString());
            Perspective3DParams pL = new Perspective3DParams(createAtto3LeftDefault());
            Perspective3DParams pR = new Perspective3DParams(createAtto3RightDefault());

            if (root.has("left3D")) {
                JSONObject jL = root.getJSONObject("left3D");
                if (jL.has("pitchKeystone")) pL.pitchKeystone = (float) jL.getDouble("pitchKeystone");
                if (jL.has("carBodyTilt")) pL.carBodyTilt = (float) jL.getDouble("carBodyTilt");
                if (jL.has("poleTilt")) pL.poleTilt = (float) jL.getDouble("poleTilt");
                if (jL.has("horizonStraighten")) pL.horizonStraighten = (float) jL.getDouble("horizonStraighten");
                if (jL.has("rollDeg")) pL.rollDeg = (float) jL.getDouble("rollDeg");
                if (jL.has("cylindrical")) pL.cylindrical = (float) jL.getDouble("cylindrical");
                if (jL.has("trueLensK1")) pL.trueLensK1 = (float) jL.getDouble("trueLensK1");
                if (jL.has("zoom3D")) pL.zoom3D = (float) jL.getDouble("zoom3D");
                if (jL.has("globalScale")) pL.globalScale = (float) jL.getDouble("globalScale");
                if (jL.has("panInOut")) pL.panInOut = (float) jL.getDouble("panInOut");
                if (jL.has("panY")) pL.panY = (float) jL.getDouble("panY");
                if (jL.has("panX")) pL.panX = (float) jL.getDouble("panX");
                if (jL.has("wideCrop")) parseCropString(jL.getString("wideCrop"), pL.wideCrop);
            }

            if (root.has("right3D")) {
                JSONObject jR = root.getJSONObject("right3D");
                if (jR.has("pitchKeystone")) pR.pitchKeystone = (float) jR.getDouble("pitchKeystone");
                if (jR.has("carBodyTilt")) pR.carBodyTilt = (float) jR.getDouble("carBodyTilt");
                if (jR.has("poleTilt")) pR.poleTilt = (float) jR.getDouble("poleTilt");
                if (jR.has("horizonStraighten")) pR.horizonStraighten = (float) jR.getDouble("horizonStraighten");
                if (jR.has("rollDeg")) pR.rollDeg = (float) jR.getDouble("rollDeg");
                if (jR.has("cylindrical")) pR.cylindrical = (float) jR.getDouble("cylindrical");
                if (jR.has("trueLensK1")) pR.trueLensK1 = (float) jR.getDouble("trueLensK1");
                if (jR.has("zoom3D")) pR.zoom3D = (float) jR.getDouble("zoom3D");
                if (jR.has("globalScale")) pR.globalScale = (float) jR.getDouble("globalScale");
                if (jR.has("panInOut")) pR.panInOut = (float) jR.getDouble("panInOut");
                if (jR.has("panY")) pR.panY = (float) jR.getDouble("panY");
                if (jR.has("panX")) pR.panX = (float) jR.getDouble("panX");
                if (jR.has("wideCrop")) parseCropString(jR.getString("wideCrop"), pR.wideCrop);
            }

            SharedPreferences.Editor ed = sp.edit();
            pL.save(ed, "L");
            pR.save(ed, "R");
            ed.putBoolean("perspective_3d_enabled", true);
            ed.putString("vehicle_model_preset", "atto3");

            // Canonical Base Camera Transform
            ed.putInt("dr_rot_l", 0);
            ed.putFloat("dr_fn_l", 48.0f);
            ed.putBoolean("dr_mir_l", true);
            ed.putBoolean("dr_fv_l", false);
            ed.putFloat("dr_str_l", -0.24f);
            ed.putFloat("dr_zoom_l", 1.14f);
            ed.putFloat("dr_cx_l", 0.40f);
            ed.putFloat("dr_cy_l", 0.5601612f);

            ed.putInt("dr_rot_r", 270);
            ed.putFloat("dr_fn_r", 27.0f);
            ed.putBoolean("dr_mir_r", true);
            ed.putBoolean("dr_fv_r", false);
            ed.putFloat("dr_str_r", -0.38f);
            ed.putFloat("dr_zoom_r", 0.91f);
            ed.putFloat("dr_cx_r", 0.66f);
            ed.putFloat("dr_cy_r", 0.68f);

            ed.apply();
            Log.i(TAG, "Successfully imported lab json calibration into preferences");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to import lab json: " + t);
            return false;
        }
    }

    /**
     * Exports current 3D settings from SharedPreferences to Downloads/byd_fisheye3d_lab.json.
     */
    public static boolean exportToLabJsonFile(SharedPreferences sp) {
        if (sp == null) return false;
        try {
            Perspective3DParams pL = new Perspective3DParams();
            pL.load(sp, "L", createAtto3LeftDefault());
            Perspective3DParams pR = new Perspective3DParams();
            pR.load(sp, "R", createAtto3RightDefault());

            JSONObject root = new JSONObject();
            JSONObject jL = new JSONObject();
            jL.put("pitchKeystone", Math.round(pL.pitchKeystone * 1000f) / 1000.0);
            jL.put("carBodyTilt", Math.round(pL.carBodyTilt * 1000f) / 1000.0);
            jL.put("poleTilt", Math.round(pL.poleTilt * 1000f) / 1000.0);
            jL.put("horizonStraighten", Math.round(pL.horizonStraighten * 1000f) / 1000.0);
            jL.put("rollDeg", Math.round(pL.rollDeg * 10f) / 10.0);
            jL.put("cylindrical", Math.round(pL.cylindrical * 1000f) / 1000.0);
            jL.put("trueLensK1", Math.round(pL.trueLensK1 * 1000f) / 1000.0);
            jL.put("zoom3D", Math.round(pL.zoom3D * 100f) / 100.0);
            jL.put("globalScale", Math.round(pL.globalScale * 100f) / 100.0);
            jL.put("panInOut", Math.round(pL.panInOut * 1000f) / 1000.0);
            jL.put("panY", Math.round(pL.panY * 1000f) / 1000.0);
            jL.put("panX", Math.round(pL.panX * 1000f) / 1000.0);
            jL.put("wideCrop", String.format(java.util.Locale.US, "[%.2f, %.2f, %.2f, %.2f]",
                    pL.wideCrop[0], pL.wideCrop[1], pL.wideCrop[2], pL.wideCrop[3]));

            JSONObject jR = new JSONObject();
            jR.put("pitchKeystone", Math.round(pR.pitchKeystone * 1000f) / 1000.0);
            jR.put("carBodyTilt", Math.round(pR.carBodyTilt * 1000f) / 1000.0);
            jR.put("poleTilt", Math.round(pR.poleTilt * 1000f) / 1000.0);
            jR.put("horizonStraighten", Math.round(pR.horizonStraighten * 1000f) / 1000.0);
            jR.put("rollDeg", Math.round(pR.rollDeg * 10f) / 10.0);
            jR.put("cylindrical", Math.round(pR.cylindrical * 1000f) / 1000.0);
            jR.put("trueLensK1", Math.round(pR.trueLensK1 * 1000f) / 1000.0);
            jR.put("zoom3D", Math.round(pR.zoom3D * 100f) / 100.0);
            jR.put("globalScale", Math.round(pR.globalScale * 100f) / 100.0);
            jR.put("panInOut", Math.round(pR.panInOut * 1000f) / 1000.0);
            jR.put("panY", Math.round(pR.panY * 1000f) / 1000.0);
            jR.put("panX", Math.round(pR.panX * 1000f) / 1000.0);
            jR.put("wideCrop", String.format(java.util.Locale.US, "[%.2f, %.2f, %.2f, %.2f]",
                    pR.wideCrop[0], pR.wideCrop[1], pR.wideCrop[2], pR.wideCrop[3]));

            root.put("left3D", jL);
            root.put("right3D", jR);

            File outFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "byd_fisheye3d_lab.json");
            try (FileWriter fw = new FileWriter(outFile)) {
                fw.write(root.toString(2));
            }
            Log.i(TAG, "Successfully exported 3D calibration to " + outFile.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to export lab json: " + t);
            return false;
        }
    }

    private static void parseCropString(String str, float[] outCrop) {
        try {
            str = str.replace("[", "").replace("]", "").trim();
            String[] parts = str.split(",");
            if (parts.length == 4) {
                outCrop[0] = Float.parseFloat(parts[0].trim());
                outCrop[1] = Float.parseFloat(parts[1].trim());
                outCrop[2] = Float.parseFloat(parts[2].trim());
                outCrop[3] = Float.parseFloat(parts[3].trim());
            }
        } catch (Throwable ignored) {}
    }
}
