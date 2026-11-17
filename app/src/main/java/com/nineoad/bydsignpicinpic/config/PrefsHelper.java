package com.nineoad.bydsignpicinpic.config;

import android.content.SharedPreferences;
import android.util.Log;

/**
 * Robust SharedPreferences helper that guarantees type safety across JSON backups
 * and auto-heals corrupted types (e.g. integer saved to float key by legacy import).
 */
public class PrefsHelper {
    private static final String TAG = "PrefsHelper";

    public static float getFloat(SharedPreferences sp, String key, float def) {
        if (sp == null) return def;
        try {
            return sp.getFloat(key, def);
        } catch (ClassCastException e) {
            try {
                int i = sp.getInt(key, (int) def);
                sp.edit().putFloat(key, (float) i).apply();
                Log.w(TAG, "Auto-healed corrupted float preference for key '" + key + "' (was int: " + i + ")");
                return (float) i;
            } catch (Exception ex) {
                try {
                    long l = sp.getLong(key, (long) def);
                    sp.edit().putFloat(key, (float) l).apply();
                    return (float) l;
                } catch (Exception ex2) {
                    try {
                        String s = sp.getString(key, null);
                        if (s != null) {
                            float f = Float.parseFloat(s);
                            sp.edit().putFloat(key, f).apply();
                            return f;
                        }
                    } catch (Exception ignored) {}
                }
            }
            return def;
        }
    }

    public static int getInt(SharedPreferences sp, String key, int def) {
        if (sp == null) return def;
        try {
            return sp.getInt(key, def);
        } catch (ClassCastException e) {
            try {
                float f = sp.getFloat(key, (float) def);
                int i = Math.round(f);
                sp.edit().putInt(key, i).apply();
                Log.w(TAG, "Auto-healed corrupted int preference for key '" + key + "' (was float: " + f + ")");
                return i;
            } catch (Exception ex) {
                try {
                    long l = sp.getLong(key, (long) def);
                    sp.edit().putInt(key, (int) l).apply();
                    return (int) l;
                } catch (Exception ex2) {
                    try {
                        String s = sp.getString(key, null);
                        if (s != null) {
                            int i = Integer.parseInt(s);
                            sp.edit().putInt(key, i).apply();
                            return i;
                        }
                    } catch (Exception ignored) {}
                }
            }
            return def;
        }
    }

    public static boolean isFloatKey(String k) {
        if (k == null) return false;
        if ("lingerSec".equals(k)) return true;
        if (k.startsWith("bev_angle_") || k.startsWith("bev_crop_") || k.startsWith("bev_car_")) return true;
        if ("bev_steering_angle".equals(k) || "bev_speed_limit_kmh".equals(k) || "bev_exit_park_duration_sec".equals(k) || "scale_bev".equals(k)) return true;
        if (k.startsWith("scale") || "scale".equals(k)) return true;
        if (k.startsWith("dr_fn_") || k.startsWith("rear_fn_") || k.startsWith("dr_str_") || k.startsWith("rear_str_")) return true;
        if (k.startsWith("dr_zoom_") || k.startsWith("rear_zoom_") || k.startsWith("dr_cx_") || k.startsWith("rear_cx_") || k.startsWith("dr_cy_") || k.startsWith("rear_cy_")) return true;
        if (k.startsWith("dr_l") || k.startsWith("dr_r") || k.startsWith("rear_l")) {
            char last = k.charAt(k.length() - 1);
            if (Character.isDigit(last)) return true;
        }
        if (k.startsWith("quad_str_") || k.startsWith("quad_zoom_") || k.startsWith("quad_cx_") || k.startsWith("quad_cy_") || k.startsWith("quad_rot_") || k.startsWith("quad_fn_") || k.startsWith("quad_crop_")) return true;
        if (k.startsWith("L_") || k.startsWith("R_")) {
            return !k.endsWith("_enabled");
        }
        return false;
    }

    public static boolean isIntKey(String k) {
        if (k == null) return false;
        if (k.startsWith("dr_rot_") || "rear_rot_l".equals(k)) return true;
        if ("mode".equals(k) || "theme_mode".equals(k) || "app_lang".equals(k) || "last_nav_tab".equals(k)) return true;
        if ("pip_corner_radius".equals(k) || "rear_aspect".equals(k) || "rear_cam_index".equals(k) || "left_cam_index".equals(k) || "right_cam_index".equals(k)) return true;
        if ("remap_rear_keycode".equals(k) || "last_run_version_code".equals(k) || "car_model_color".equals(k) || "quad_wheel_size".equals(k)) return true;
        return false;
    }
}
