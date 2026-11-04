package com.nineoad.bydsignpicinpic.bev.profile;

import org.json.JSONException;
import org.json.JSONObject;

public class VehicleDimensions {
    public int wheelbaseMm;
    public int widthMm;
    public int lengthMm;
    public int frontOverhangMm;
    public int rearOverhangMm;

    public VehicleDimensions() {
        this(2720, 1875, 4455, 890, 845); // Defaults for reference compact C-SUV (Atto 3)
    }

    public VehicleDimensions(int wheelbaseMm, int widthMm, int lengthMm, int frontOverhangMm, int rearOverhangMm) {
        this.wheelbaseMm = wheelbaseMm;
        this.widthMm = widthMm;
        this.lengthMm = lengthMm;
        this.frontOverhangMm = frontOverhangMm;
        this.rearOverhangMm = rearOverhangMm;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("wheelbaseMm", this.wheelbaseMm);
        obj.put("widthMm", this.widthMm);
        obj.put("lengthMm", this.lengthMm);
        obj.put("frontOverhangMm", this.frontOverhangMm);
        obj.put("rearOverhangMm", this.rearOverhangMm);
        return obj;
    }

    public static VehicleDimensions fromJson(JSONObject obj) {
        if (obj == null) return new VehicleDimensions();
        return new VehicleDimensions(
                obj.optInt("wheelbaseMm", 2720),
                obj.optInt("widthMm", 1875),
                obj.optInt("lengthMm", 4455),
                obj.optInt("frontOverhangMm", 890),
                obj.optInt("rearOverhangMm", 845)
        );
    }
}
