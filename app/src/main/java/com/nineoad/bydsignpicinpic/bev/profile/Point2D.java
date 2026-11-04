package com.nineoad.bydsignpicinpic.bev.profile;

import org.json.JSONException;
import org.json.JSONObject;

public class Point2D {
    public float x;
    public float y;

    public Point2D() {
        this(0.0f, 0.0f);
    }

    public Point2D(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Point2D copy() {
        return new Point2D(this.x, this.y);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("x", this.x);
        obj.put("y", this.y);
        return obj;
    }

    public static Point2D fromJson(JSONObject obj) {
        if (obj == null) return new Point2D();
        return new Point2D(
                (float) obj.optDouble("x", 0.0),
                (float) obj.optDouble("y", 0.0)
        );
    }
}
