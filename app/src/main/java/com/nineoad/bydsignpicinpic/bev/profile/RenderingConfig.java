package com.nineoad.bydsignpicinpic.bev.profile;

import org.json.JSONException;
import org.json.JSONObject;

public class RenderingConfig {
    public String carAssetId;           // e.g. "atto3", "dolphin", "seal", "sealion7", "generic"
    public float carWidthRatio;         // Screen/ground ratio for vehicle footprint
    public float carHeightRatio;
    public float carCenterOffsetX;
    public float carCenterOffsetY;
    public float blendOverlapAngleDeg;  // Seam blending angle (default 8.0 deg)
    public boolean showGrid;
    public float gridSpacingMeters;     // e.g. 1.0m
    public int outputWidth;             // Render target BEV texture size (e.g. 1024 or 1920)
    public int outputHeight;

    public RenderingConfig() {
        this.carAssetId = "atto3";
        this.carWidthRatio = 0.22f;
        this.carHeightRatio = 0.44f;
        this.carCenterOffsetX = 0.0f;
        this.carCenterOffsetY = 0.0f;
        this.blendOverlapAngleDeg = 8.0f;
        this.showGrid = true;
        this.gridSpacingMeters = 1.0f;
        this.outputWidth = 1024;
        this.outputHeight = 1024;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("carAssetId", this.carAssetId);
        obj.put("carWidthRatio", this.carWidthRatio);
        obj.put("carHeightRatio", this.carHeightRatio);
        obj.put("carCenterOffsetX", this.carCenterOffsetX);
        obj.put("carCenterOffsetY", this.carCenterOffsetY);
        obj.put("blendOverlapAngleDeg", this.blendOverlapAngleDeg);
        obj.put("showGrid", this.showGrid);
        obj.put("gridSpacingMeters", this.gridSpacingMeters);
        obj.put("outputWidth", this.outputWidth);
        obj.put("outputHeight", this.outputHeight);
        return obj;
    }

    public static RenderingConfig fromJson(JSONObject obj) {
        RenderingConfig r = new RenderingConfig();
        if (obj == null) return r;
        r.carAssetId = obj.optString("carAssetId", "atto3");
        r.carWidthRatio = (float) obj.optDouble("carWidthRatio", 0.22);
        r.carHeightRatio = (float) obj.optDouble("carHeightRatio", 0.44);
        r.carCenterOffsetX = (float) obj.optDouble("carCenterOffsetX", 0.0);
        r.carCenterOffsetY = (float) obj.optDouble("carCenterOffsetY", 0.0);
        r.blendOverlapAngleDeg = (float) obj.optDouble("blendOverlapAngleDeg", 8.0);
        r.showGrid = obj.optBoolean("showGrid", true);
        r.gridSpacingMeters = (float) obj.optDouble("gridSpacingMeters", 1.0);
        r.outputWidth = obj.optInt("outputWidth", 1024);
        r.outputHeight = obj.optInt("outputHeight", 1024);
        return r;
    }
}
