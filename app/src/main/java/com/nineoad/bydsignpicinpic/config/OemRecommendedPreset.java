package com.nineoad.bydsignpicinpic.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Pre-calibrated OEM defaults matching the user's tested BYD DiLink 3.0 configuration.
 */
public class OemRecommendedPreset {
    public static final String PREF_NAME = "pnp";
    public static final int PRESET_VERSION = 1;

    public static void applyRecommendedPreset(SharedPreferences sp) {
        if (sp == null) return;
        sp.edit()
            // --- Mode & Global ---
            .putInt("mode", 2) // Default Balance Mode (สมดุล)
            .putFloat("lingerSec", 2.0f)
            .putInt("pip_corner_radius", 22)
            .putBoolean("pip_anchor_right", true)
            .putInt("pip_margin_left", 1236)
            .putInt("pip_margin_right", 0)
            .putInt("pip_anchor_y", 0)
            .putBoolean("master_turn_enabled", true)
            .putString("steering_pos", "auto")
            .putString("ota_channel", "stable")
            .putBoolean("auto_check_update", true)

            // --- Turn Signal PIP (Mode 1 / Mode 2) ---
            // Left Turn (dr_..._l)
            .putInt("dr_rot_l", 0)
            .putFloat("dr_fn_l", 48.0f)
            .putBoolean("dr_mir_l", true)
            .putBoolean("dr_fv_l", false)
            .putFloat("dr_l0", 0.22f)
            .putFloat("dr_l1", 0.57f)
            .putFloat("dr_l2", 0.81f)
            .putFloat("dr_l3", 0.97f)
            .putFloat("dr_str_l", -0.24f)
            .putFloat("dr_zoom_l", 1.14f)
            .putFloat("dr_cx_l", 0.40f)
            .putFloat("dr_cy_l", 0.5601612f)

            // Right Turn (dr_..._r)
            .putInt("dr_rot_r", 270)
            .putFloat("dr_fn_r", 27.0f)
            .putBoolean("dr_mir_r", true)
            .putBoolean("dr_fv_r", false)
            .putFloat("dr_r0", 0.21f)
            .putFloat("dr_r1", 0.62f)
            .putFloat("dr_r2", 0.75f)
            .putFloat("dr_r3", 0.89f)
            .putFloat("dr_str_r", -0.38f)
            .putFloat("dr_zoom_r", 0.91f)
            .putFloat("dr_cx_r", 0.66f)
            .putFloat("dr_cy_r", 0.68f)

            .putFloat("scale_dr", 0.9500799f)
            .putInt("x_dr", 1236)
            .putInt("y_dr", 0)

            // --- Digital Rearview Mirror (Mode 4 / Rear) ---
            .putInt("rear_rot_l", 0)
            .putFloat("rear_fn_l", 0.0f)
            .putBoolean("rear_mir_l", true)
            .putBoolean("rear_fv_l", false)
            .putFloat("rear_l0", 0.0f)
            .putFloat("rear_l1", 0.50f)
            .putFloat("rear_l2", 1.0f)
            .putFloat("rear_l3", 1.0f)
            .putFloat("rear_str_l", -0.02f)
            .putFloat("rear_zoom_l", 2.3f)
            .putFloat("rear_cx_l", 0.50845903f)
            .putFloat("rear_cy_l", 0.6826888f)
            .putFloat("scale_rear", 1.0796669f)
            .putInt("x_rear", 768)
            .putInt("y_rear", 22)
            .putInt("rear_aspect", 0)

            // --- Quad Wheel System (Mode 3) ---
            // Front-Left (FL)
            .putFloat("quad_rot_fl", 270.0f)
            .putFloat("quad_fn_fl", 10.0f)
            .putBoolean("quad_mir_fl", false)
            .putBoolean("quad_fv_fl", false)
            .putFloat("quad_crop_fl0", 0.0f)
            .putFloat("quad_crop_fl1", 0.31f)
            .putFloat("quad_crop_fl2", 1.0f)
            .putFloat("quad_crop_fl3", 1.0f)
            .putFloat("quad_str_fl", -0.37f)
            .putFloat("quad_zoom_fl", 1.20f)
            .putFloat("quad_cx_fl", 0.51f)
            .putFloat("quad_cy_fl", 0.50f)

            // Front-Right (FR)
            .putFloat("quad_rot_fr", 90.0f)
            .putFloat("quad_fn_fr", -24.0f)
            .putBoolean("quad_mir_fr", false)
            .putBoolean("quad_fv_fr", false)
            .putFloat("quad_crop_fr0", 0.0f)
            .putFloat("quad_crop_fr1", 0.32f)
            .putFloat("quad_crop_fr2", 1.0f)
            .putFloat("quad_crop_fr3", 1.0f)
            .putFloat("quad_str_fr", -0.35f)
            .putFloat("quad_zoom_fr", 1.25f)
            .putFloat("quad_cx_fr", 0.46f)
            .putFloat("quad_cy_fr", 0.52f)

            // Rear-Left (RL)
            .putFloat("quad_rot_rl", 0.0f)
            .putFloat("quad_fn_rl", 0.0f)
            .putBoolean("quad_mir_rl", false)
            .putBoolean("quad_fv_rl", false)
            .putFloat("quad_crop_rl0", 0.0f)
            .putFloat("quad_crop_rl1", 0.0f)
            .putFloat("quad_crop_rl2", 1.0f)
            .putFloat("quad_crop_rl3", 1.0f)
            .putFloat("quad_str_rl", 0.0f)
            .putFloat("quad_zoom_rl", 1.0f)
            .putFloat("quad_cx_rl", 0.50f)
            .putFloat("quad_cy_rl", 0.50f)

            // Rear-Right (RR)
            .putFloat("quad_rot_rr", 0.0f)
            .putFloat("quad_fn_rr", 0.0f)
            .putBoolean("quad_mir_rr", false)
            .putBoolean("quad_fv_rr", false)
            .putFloat("quad_crop_rr0", 0.0f)
            .putFloat("quad_crop_rr1", 0.0f)
            .putFloat("quad_crop_rr2", 1.0f)
            .putFloat("quad_crop_rr3", 1.0f)
            .putFloat("quad_str_rr", 0.0f)
            .putFloat("quad_zoom_rr", 1.0f)
            .putFloat("quad_cx_rr", 0.50f)
            .putFloat("quad_cy_rr", 0.50f)
            .putInt("quad_wheel_size", 480)

            // --- BEV 360 Landscape Configuration ---
            .putFloat("scale_bev", 1.502741f)
            .putInt("x_bev", 1514)
            .putInt("y_bev", 0)
            .putInt("bev_steering_angle", 90)
            .putBoolean("bev_auto_steering", true)
            .putBoolean("wheel_trigger_enabled", true)
            .putFloat("bev_speed_limit_kmh", 20.0f)
            .putBoolean("bev_auto_speed_limit", true)
            .putFloat("bev_car_aspect_h", 2.4f)
            .putFloat("bev_car_scale_w", 0.34f)
            .putFloat("bev_car_off_y", 0.035f)
            .putInt("bev_crop_schema_ver", 4)
            .putFloat("bev_crop_left", 0.686f)
            .putFloat("bev_crop_right", 1.0f)
            .putFloat("bev_crop_top", 0.0f)
            .putFloat("bev_crop_bottom", 1.0f)
            .putFloat("bev_angle_front", -4.8f)
            .putFloat("bev_angle_rear", 15.0f)
            .putFloat("bev_angle_left", -3.0f)
            .putFloat("bev_angle_right", 0.0f)
            .putBoolean("bev_auto_exit_park", false)
            .putFloat("bev_exit_park_duration_sec", 10.0f)

            // --- Hazard & Custom ---
            .putInt("x_hz", 460)
            .putInt("y_hz", 6)
            .putFloat("scale_hz", 0.93681604f)
            .putInt("x_cu", 1075)
            .putInt("y_cu", 333)
            .putFloat("scale_cu", 0.95554584f)

            .putInt("recommended_preset_ver", PRESET_VERSION)
            .apply();
    }

    public static void ensureDefaultsInitialized(Context context) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!sp.contains("recommended_preset_ver")) {
            applyRecommendedPreset(sp);
        }
    }
}
