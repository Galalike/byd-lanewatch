package com.nineoad.bydsignpicinpic.bev.profile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CameraCalibration {
    public CameraRole role;
    public RectFNormalized cropRect;

    // 4-Point Homography Warp Control Handles (Normalized [0, 1] in camera viewport space)
    public Point2D p0; // Top-Left
    public Point2D p1; // Top-Right
    public Point2D p2; // Bottom-Right
    public Point2D p3; // Bottom-Left

    // Computed or calibrated 3x3 Homography Matrix (row-major 9 floats)
    public float[] homographyMatrix;

    // 3D Optical & Extrinsic Parameters
    public float rollDeg;
    public float pitchDeg;
    public float yawDeg;
    public float focalLength;
    public float opticalCenterX;
    public float opticalCenterY;
    public float distortionK1;
    public float distortionK2;

    // Translation & Fine Tuning
    public float fineRotationDeg;
    public float scaleX;
    public float scaleY;
    public float offsetX;
    public float offsetY;

    // Image Adjustments
    public float brightness; // -1.0 to +1.0 (default 0.0)
    public float contrast;   // 0.0 to 2.0 (default 1.0)
    public boolean mirrorHorizontal;
    public boolean flipVertical;

    public CameraCalibration(CameraRole role) {
        this.role = role;
        this.cropRect = new RectFNormalized(0.0f, 0.0f, 1.0f, 1.0f);
        // Default standard quad
        this.p0 = new Point2D(0.0f, 0.0f);
        this.p1 = new Point2D(1.0f, 0.0f);
        this.p2 = new Point2D(1.0f, 1.0f);
        this.p3 = new Point2D(0.0f, 1.0f);

        this.homographyMatrix = new float[]{
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f
        };

        this.rollDeg = 0.0f;
        this.pitchDeg = 0.0f;
        this.yawDeg = 0.0f;
        this.focalLength = 1.0f;
        this.opticalCenterX = 0.5f;
        this.opticalCenterY = 0.5f;
        this.distortionK1 = -0.15f;
        this.distortionK2 = 0.02f;

        this.fineRotationDeg = 0.0f;
        this.scaleX = 1.0f;
        this.scaleY = 1.0f;
        this.offsetX = 0.0f;
        this.offsetY = 0.0f;

        this.brightness = 0.0f;
        this.contrast = 1.0f;
        this.mirrorHorizontal = false;
        this.flipVertical = false;
    }

    public CameraCalibration copy() {
        CameraCalibration c = new CameraCalibration(this.role);
        c.cropRect = this.cropRect.copy();
        c.p0 = this.p0.copy();
        c.p1 = this.p1.copy();
        c.p2 = this.p2.copy();
        c.p3 = this.p3.copy();
        c.homographyMatrix = this.homographyMatrix.clone();
        c.rollDeg = this.rollDeg;
        c.pitchDeg = this.pitchDeg;
        c.yawDeg = this.yawDeg;
        c.focalLength = this.focalLength;
        c.opticalCenterX = this.opticalCenterX;
        c.opticalCenterY = this.opticalCenterY;
        c.distortionK1 = this.distortionK1;
        c.distortionK2 = this.distortionK2;
        c.fineRotationDeg = this.fineRotationDeg;
        c.scaleX = this.scaleX;
        c.scaleY = this.scaleY;
        c.offsetX = this.offsetX;
        c.offsetY = this.offsetY;
        c.brightness = this.brightness;
        c.contrast = this.contrast;
        c.mirrorHorizontal = this.mirrorHorizontal;
        c.flipVertical = this.flipVertical;
        return c;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("role", this.role.name());
        obj.put("cropRect", this.cropRect.toJson());
        obj.put("p0", this.p0.toJson());
        obj.put("p1", this.p1.toJson());
        obj.put("p2", this.p2.toJson());
        obj.put("p3", this.p3.toJson());

        JSONArray hArr = new JSONArray();
        for (float v : this.homographyMatrix) {
            hArr.put(v);
        }
        obj.put("homography", hArr);

        obj.put("rollDeg", this.rollDeg);
        obj.put("pitchDeg", this.pitchDeg);
        obj.put("yawDeg", this.yawDeg);
        obj.put("focalLength", this.focalLength);
        obj.put("opticalCenterX", this.opticalCenterX);
        obj.put("opticalCenterY", this.opticalCenterY);
        obj.put("distortionK1", this.distortionK1);
        obj.put("distortionK2", this.distortionK2);

        obj.put("fineRotationDeg", this.fineRotationDeg);
        obj.put("scaleX", this.scaleX);
        obj.put("scaleY", this.scaleY);
        obj.put("offsetX", this.offsetX);
        obj.put("offsetY", this.offsetY);

        obj.put("brightness", this.brightness);
        obj.put("contrast", this.contrast);
        obj.put("mirrorHorizontal", this.mirrorHorizontal);
        obj.put("flipVertical", this.flipVertical);
        return obj;
    }

    public static CameraCalibration fromJson(JSONObject obj) {
        if (obj == null) return null;
        String roleStr = obj.optString("role", "FRONT");
        CameraRole role = CameraRole.FRONT;
        try {
            role = CameraRole.valueOf(roleStr);
        } catch (Exception ignored) {}

        CameraCalibration c = new CameraCalibration(role);
        c.cropRect = RectFNormalized.fromJson(obj.optJSONObject("cropRect"));
        c.p0 = Point2D.fromJson(obj.optJSONObject("p0"));
        c.p1 = Point2D.fromJson(obj.optJSONObject("p1"));
        c.p2 = Point2D.fromJson(obj.optJSONObject("p2"));
        c.p3 = Point2D.fromJson(obj.optJSONObject("p3"));

        JSONArray hArr = obj.optJSONArray("homography");
        if (hArr != null && hArr.length() == 9) {
            for (int i = 0; i < 9; i++) {
                c.homographyMatrix[i] = (float) hArr.optDouble(i, i % 4 == 0 ? 1.0 : 0.0);
            }
        }

        c.rollDeg = (float) obj.optDouble("rollDeg", 0.0);
        c.pitchDeg = (float) obj.optDouble("pitchDeg", 0.0);
        c.yawDeg = (float) obj.optDouble("yawDeg", 0.0);
        c.focalLength = (float) obj.optDouble("focalLength", 1.0);
        c.opticalCenterX = (float) obj.optDouble("opticalCenterX", 0.5);
        c.opticalCenterY = (float) obj.optDouble("opticalCenterY", 0.5);
        c.distortionK1 = (float) obj.optDouble("distortionK1", -0.15);
        c.distortionK2 = (float) obj.optDouble("distortionK2", 0.02);

        c.fineRotationDeg = (float) obj.optDouble("fineRotationDeg", 0.0);
        c.scaleX = (float) obj.optDouble("scaleX", 1.0);
        c.scaleY = (float) obj.optDouble("scaleY", 1.0);
        c.offsetX = (float) obj.optDouble("offsetX", 0.0);
        c.offsetY = (float) obj.optDouble("offsetY", 0.0);

        c.brightness = (float) obj.optDouble("brightness", 0.0);
        c.contrast = (float) obj.optDouble("contrast", 1.0);
        c.mirrorHorizontal = obj.optBoolean("mirrorHorizontal", false);
        c.flipVertical = obj.optBoolean("flipVertical", false);

        return c;
    }
}
