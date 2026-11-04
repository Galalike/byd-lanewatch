package com.nineoad.bydsignpicinpic.bev.profile;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.EnumMap;
import java.util.Map;

public class VehicleProfile {
    public String id;
    public String manufacturer;
    public String model;
    public int modelYear;
    public String variant;
    public int profileVersion;
    public String author;
    public String notes;
    public long createdAt;
    public ProfileStatus status;

    public int screenWidth;
    public int screenHeight;
    public CameraInputType inputType;
    public int inputFrameWidth;
    public int inputFrameHeight;

    public VehicleDimensions dimensions;
    public RenderingConfig rendering;
    public final Map<CameraRole, CameraCalibration> cameras = new EnumMap<>(CameraRole.class);

    public VehicleProfile() {
        this.id = "byd_atto3_default";
        this.manufacturer = "BYD";
        this.model = "Atto 3";
        this.modelYear = 2023;
        this.variant = "Standard / Extended Range";
        this.profileVersion = 1;
        this.author = "System Default";
        this.notes = "Factory reverse-engineered baseline profile.";
        this.createdAt = System.currentTimeMillis();
        this.status = ProfileStatus.VERIFIED;

        this.screenWidth = 1920;
        this.screenHeight = 1080;
        this.inputType = CameraInputType.COMBINED_QUAD_GRID;
        this.inputFrameWidth = 1920;
        this.inputFrameHeight = 972;

        this.dimensions = new VehicleDimensions();
        this.rendering = new RenderingConfig();

        // Initialize 4 cameras with default quad slots
        cameras.put(CameraRole.FRONT, new CameraCalibration(CameraRole.FRONT));
        cameras.put(CameraRole.REAR, new CameraCalibration(CameraRole.REAR));
        cameras.put(CameraRole.LEFT, new CameraCalibration(CameraRole.LEFT));
        cameras.put(CameraRole.RIGHT, new CameraCalibration(CameraRole.RIGHT));
    }

    public CameraCalibration getCamera(CameraRole role) {
        CameraCalibration cal = cameras.get(role);
        if (cal == null) {
            cal = new CameraCalibration(role);
            cameras.put(role, cal);
        }
        return cal;
    }

    public VehicleProfile copy(String newId, String newAuthor) {
        VehicleProfile p = new VehicleProfile();
        p.id = newId;
        p.manufacturer = this.manufacturer;
        p.model = this.model;
        p.modelYear = this.modelYear;
        p.variant = this.variant;
        p.profileVersion = this.profileVersion + 1;
        p.author = newAuthor != null ? newAuthor : this.author;
        p.notes = this.notes;
        p.createdAt = System.currentTimeMillis();
        p.status = ProfileStatus.EXPERIMENTAL;

        p.screenWidth = this.screenWidth;
        p.screenHeight = this.screenHeight;
        p.inputType = this.inputType;
        p.inputFrameWidth = this.inputFrameWidth;
        p.inputFrameHeight = this.inputFrameHeight;

        try {
            p.dimensions = VehicleDimensions.fromJson(this.dimensions.toJson());
            p.rendering = RenderingConfig.fromJson(this.rendering.toJson());
        } catch (JSONException ignored) {}

        for (Map.Entry<CameraRole, CameraCalibration> e : this.cameras.entrySet()) {
            p.cameras.put(e.getKey(), e.getValue().copy());
        }
        return p;
    }

    public String toJsonString(boolean pretty) throws JSONException {
        JSONObject obj = toJson();
        return pretty ? obj.toString(2) : obj.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", this.id);
        obj.put("manufacturer", this.manufacturer);
        obj.put("model", this.model);
        obj.put("modelYear", this.modelYear);
        obj.put("variant", this.variant);
        obj.put("profileVersion", this.profileVersion);
        obj.put("author", this.author);
        obj.put("notes", this.notes);
        obj.put("createdAt", this.createdAt);
        obj.put("status", this.status.name());

        obj.put("screenWidth", this.screenWidth);
        obj.put("screenHeight", this.screenHeight);
        obj.put("inputType", this.inputType.name());
        obj.put("inputFrameWidth", this.inputFrameWidth);
        obj.put("inputFrameHeight", this.inputFrameHeight);

        obj.put("dimensions", this.dimensions.toJson());
        obj.put("rendering", this.rendering.toJson());

        JSONObject camsObj = new JSONObject();
        for (CameraRole r : CameraRole.values()) {
            CameraCalibration c = cameras.get(r);
            if (c != null) {
                camsObj.put(r.name(), c.toJson());
            }
        }
        obj.put("cameras", camsObj);

        return obj;
    }

    public static VehicleProfile fromJson(JSONObject obj) {
        if (obj == null) return null;
        VehicleProfile p = new VehicleProfile();
        p.id = obj.optString("id", "custom_profile");
        p.manufacturer = obj.optString("manufacturer", "Unknown");
        p.model = obj.optString("model", "Vehicle");
        p.modelYear = obj.optInt("modelYear", 2024);
        p.variant = obj.optString("variant", "");
        p.profileVersion = obj.optInt("profileVersion", 1);
        p.author = obj.optString("author", "Community");
        p.notes = obj.optString("notes", "");
        p.createdAt = obj.optLong("createdAt", System.currentTimeMillis());

        String statusStr = obj.optString("status", "EXPERIMENTAL");
        try {
            p.status = ProfileStatus.valueOf(statusStr);
        } catch (Exception e) {
            p.status = ProfileStatus.EXPERIMENTAL;
        }

        p.screenWidth = obj.optInt("screenWidth", 1920);
        p.screenHeight = obj.optInt("screenHeight", 1080);

        String inputTypeStr = obj.optString("inputType", "COMBINED_QUAD_GRID");
        try {
            p.inputType = CameraInputType.valueOf(inputTypeStr);
        } catch (Exception e) {
            p.inputType = CameraInputType.COMBINED_QUAD_GRID;
        }

        p.inputFrameWidth = obj.optInt("inputFrameWidth", 1920);
        p.inputFrameHeight = obj.optInt("inputFrameHeight", 972);

        p.dimensions = VehicleDimensions.fromJson(obj.optJSONObject("dimensions"));
        p.rendering = RenderingConfig.fromJson(obj.optJSONObject("rendering"));

        JSONObject camsObj = obj.optJSONObject("cameras");
        if (camsObj != null) {
            for (CameraRole r : CameraRole.values()) {
                JSONObject cObj = camsObj.optJSONObject(r.name());
                if (cObj != null) {
                    p.cameras.put(r, CameraCalibration.fromJson(cObj));
                }
            }
        }
        return p;
    }
}
