package com.nineoad.bydsignpicinpic.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import com.nineoad.bydsignpicinpic.R;

public class CarModelManager {
    public static final int MODEL_ATTO3 = 0;
    public static final int MODEL_DOLPHIN = 1;
    public static final int MODEL_SEAL = 2;
    public static final int MODEL_SEAL_U = 3;
    public static final int MODEL_HAN = 4;
    public static final int MODEL_TANG = 5;
    public static final int MODEL_QIN = 6;
    public static final int MODEL_SONG = 7;
    public static final int MODEL_DEFAULT = 8;

    public static final String PREF_CAR_MODEL = "bev_car_model_index";

    public static int getCarModelDrawable(int index) {
        switch (index) {
            case MODEL_ATTO3:
                return R.drawable.car_top_atto3;
            case MODEL_DOLPHIN:
                return R.drawable.car_top_dolphin;
            case MODEL_SEAL:
                return R.drawable.car_top_seal;
            case MODEL_SEAL_U:
                return R.drawable.car_top_sealu;
            case MODEL_HAN:
                return R.drawable.car_top_han;
            case MODEL_TANG:
                return R.drawable.car_top_tang;
            case MODEL_QIN:
                return R.drawable.car_top_qin;
            case MODEL_SONG:
                return R.drawable.car_top_song;
            case MODEL_DEFAULT:
            default:
                return R.drawable.car_top_generic;
        }
    }

    public static int getSelectedCarModelDrawable(Context ctx) {
        if (ctx == null) return R.drawable.car_top_generic;
        SharedPreferences prefs = ctx.getSharedPreferences("pnp", 0);
        int idx = prefs.getInt(PREF_CAR_MODEL, MODEL_ATTO3);
        return getCarModelDrawable(idx);
    }
}
