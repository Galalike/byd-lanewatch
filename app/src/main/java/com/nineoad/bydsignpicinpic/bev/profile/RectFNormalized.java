package com.nineoad.bydsignpicinpic.bev.profile;

import org.json.JSONException;
import org.json.JSONObject;

public class RectFNormalized {
    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectFNormalized() {
        this(0.0f, 0.0f, 1.0f, 1.0f);
    }

    public RectFNormalized(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public RectFNormalized copy() {
        return new RectFNormalized(this.left, this.top, this.right, this.bottom);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("left", this.left);
        obj.put("top", this.top);
        obj.put("right", this.right);
        obj.put("bottom", this.bottom);
        return obj;
    }

    public static RectFNormalized fromJson(JSONObject obj) {
        if (obj == null) return new RectFNormalized();
        return new RectFNormalized(
                (float) obj.optDouble("left", 0.0),
                (float) obj.optDouble("top", 0.0),
                (float) obj.optDouble("right", 1.0),
                (float) obj.optDouble("bottom", 1.0)
        );
    }
}
