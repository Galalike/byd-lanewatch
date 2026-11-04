package com.nineoad.bydsignpicinpic.bev.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VehicleProfileManager {
    private static final String TAG = "VehicleProfileManager";
    private static final String PREF_NAME = "bev_profile_prefs";
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";

    public interface OnProfileChangeListener {
        void onProfileChanged(VehicleProfile activeProfile);
    }

    private static VehicleProfileManager sInstance;

    private final Context context;
    private final SharedPreferences prefs;
    private final File userProfilesDir;
    private final Map<String, VehicleProfile> profiles = new LinkedHashMap<>();
    private VehicleProfile activeProfile;
    private final List<OnProfileChangeListener> listeners = new ArrayList<>();

    public static synchronized VehicleProfileManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new VehicleProfileManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private VehicleProfileManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.userProfilesDir = new File(context.getFilesDir(), "bev_profiles");
        if (!userProfilesDir.exists()) {
            userProfilesDir.mkdirs();
        }
        loadAllProfiles();
    }

    public synchronized void addListener(OnProfileChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(OnProfileChangeListener listener) {
        listeners.remove(listener);
    }

    public synchronized List<VehicleProfile> getAllProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public synchronized VehicleProfile getActiveProfile() {
        if (activeProfile == null) {
            String activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, "byd_atto3");
            activeProfile = profiles.get(activeId);
            if (activeProfile == null && !profiles.isEmpty()) {
                activeProfile = profiles.values().iterator().next();
            }
        }
        return activeProfile;
    }

    public synchronized void setActiveProfile(String profileId) {
        VehicleProfile target = profiles.get(profileId);
        if (target != null) {
            this.activeProfile = target;
            this.prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply();
            notifyListeners();
            Log.i(TAG, "Active profile changed to: " + target.manufacturer + " " + target.model + " (" + target.id + ")");
        }
    }

    public synchronized boolean saveProfile(VehicleProfile profile) {
        if (profile == null || profile.id == null) return false;
        profiles.put(profile.id, profile);
        try {
            File file = new File(userProfilesDir, profile.id + ".json");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(profile.toJsonString(true).getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();
            if (activeProfile != null && activeProfile.id.equals(profile.id)) {
                this.activeProfile = profile;
                notifyListeners();
            }
            Log.i(TAG, "Saved profile: " + file.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed saving profile " + profile.id + ": " + t);
            return false;
        }
    }

    public synchronized boolean deleteProfile(String profileId) {
        // Disallow deleting built-in factory defaults
        if (isBuiltInProfile(profileId)) {
            Log.w(TAG, "Cannot delete built-in profile: " + profileId);
            return false;
        }
        profiles.remove(profileId);
        File file = new File(userProfilesDir, profileId + ".json");
        if (file.exists()) {
            file.delete();
        }
        if (activeProfile != null && activeProfile.id.equals(profileId)) {
            setActiveProfile("byd_atto3");
        }
        return true;
    }

    public boolean isBuiltInProfile(String id) {
        return id.startsWith("byd_") && !id.contains("_custom_");
    }

    public synchronized VehicleProfile importProfileFromJson(String jsonString) throws Exception {
        JSONObject obj = new JSONObject(jsonString);
        VehicleProfile p = VehicleProfile.fromJson(obj);
        if (p != null && p.id != null) {
            // If already exists, generate copy ID unless specified
            if (profiles.containsKey(p.id)) {
                p.id = p.id + "_imported_" + (System.currentTimeMillis() % 10000);
            }
            p.status = ProfileStatus.COMMUNITY_TESTED;
            saveProfile(p);
            return p;
        }
        throw new IllegalArgumentException("Invalid profile JSON payload");
    }

    private void notifyListeners() {
        for (OnProfileChangeListener l : listeners) {
            try {
                l.onProfileChanged(activeProfile);
            } catch (Throwable ignored) {}
        }
    }

    private void loadAllProfiles() {
        profiles.clear();
        // 1. Built-in reference factory profiles
        loadBuiltInProfiles();

        // 2. User saved / modified profiles from internal disk
        loadUserProfiles();

        // 3. Ensure active profile selected
        getActiveProfile();
    }

    private void loadBuiltInProfiles() {
        // A. BYD Atto 3 (Reference Implementation)
        VehicleProfile atto3 = createAtto3Reference();
        profiles.put(atto3.id, atto3);

        // B. BYD Dolphin
        VehicleProfile dolphin = createDolphinReference();
        profiles.put(dolphin.id, dolphin);

        // C. BYD Seal
        VehicleProfile seal = createSealReference();
        profiles.put(seal.id, seal);

        // D. BYD Sealion 7
        VehicleProfile sealion7 = createSealion7Reference();
        profiles.put(sealion7.id, sealion7);

        // E. Universal Generic (4-Camera Quad)
        VehicleProfile generic = createGenericReference();
        profiles.put(generic.id, generic);
    }

    private void loadUserProfiles() {
        File[] files = userProfilesDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().endsWith(".json")) {
                try {
                    FileInputStream fis = new FileInputStream(f);
                    byte[] data = new byte[(int) f.length()];
                    fis.read(data);
                    fis.close();
                    JSONObject obj = new JSONObject(new String(data, StandardCharsets.UTF_8));
                    VehicleProfile p = VehicleProfile.fromJson(obj);
                    if (p != null && p.id != null) {
                        profiles.put(p.id, p);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Could not load user profile " + f.getName() + ": " + t);
                }
            }
        }
    }

    private VehicleProfile createAtto3Reference() {
        VehicleProfile p = new VehicleProfile();
        p.id = "byd_atto3";
        p.manufacturer = "BYD";
        p.model = "Atto 3";
        p.modelYear = 2023;
        p.variant = "Dynamic / Premium / Extended";
        p.profileVersion = 1;
        p.author = "Antigravity Engineering (Verified Reference)";
        p.notes = "Reverse-engineered 4-camera Combined Frame pipeline for BYD DiLink.";
        p.status = ProfileStatus.VERIFIED;
        p.dimensions = new VehicleDimensions(2720, 1875, 4455, 890, 845);
        p.rendering.carAssetId = "atto3";
        p.rendering.carWidthRatio = 0.22f;
        p.rendering.carHeightRatio = 0.44f;

        // Viewports in 1920x972 combined buffer
        setupQuadViewports(p);
        return p;
    }

    private VehicleProfile createDolphinReference() {
        VehicleProfile p = new VehicleProfile();
        p.id = "byd_dolphin";
        p.manufacturer = "BYD";
        p.model = "Dolphin";
        p.modelYear = 2023;
        p.variant = "Standard / Extended Range";
        p.profileVersion = 1;
        p.author = "Antigravity Engineering";
        p.notes = "Compact B-Segment EV profile.";
        p.status = ProfileStatus.COMMUNITY_TESTED;
        p.dimensions = new VehicleDimensions(2700, 1770, 4150, 800, 650);
        p.rendering.carAssetId = "dolphin";
        p.rendering.carWidthRatio = 0.20f;
        p.rendering.carHeightRatio = 0.40f;
        setupQuadViewports(p);
        return p;
    }

    private VehicleProfile createSealReference() {
        VehicleProfile p = new VehicleProfile();
        p.id = "byd_seal";
        p.manufacturer = "BYD";
        p.model = "Seal";
        p.modelYear = 2024;
        p.variant = "Dynamic / Premium / AWD Performance";
        p.profileVersion = 1;
        p.author = "Antigravity Engineering";
        p.notes = "D-Segment Sedan profile.";
        p.status = ProfileStatus.COMMUNITY_TESTED;
        p.dimensions = new VehicleDimensions(2920, 1875, 4800, 940, 940);
        p.rendering.carAssetId = "seal";
        p.rendering.carWidthRatio = 0.23f;
        p.rendering.carHeightRatio = 0.48f;
        setupQuadViewports(p);
        return p;
    }

    private VehicleProfile createSealion7Reference() {
        VehicleProfile p = new VehicleProfile();
        p.id = "byd_sealion7";
        p.manufacturer = "BYD";
        p.model = "Sealion 7";
        p.modelYear = 2024;
        p.variant = "Premium / AWD Performance";
        p.profileVersion = 1;
        p.author = "Antigravity Engineering";
        p.notes = "Mid-size Fastback SUV profile.";
        p.status = ProfileStatus.COMMUNITY_TESTED;
        p.dimensions = new VehicleDimensions(2930, 1925, 4830, 950, 950);
        p.rendering.carAssetId = "sealion7";
        p.rendering.carWidthRatio = 0.24f;
        p.rendering.carHeightRatio = 0.49f;
        setupQuadViewports(p);
        return p;
    }

    private VehicleProfile createGenericReference() {
        VehicleProfile p = new VehicleProfile();
        p.id = "universal_generic";
        p.manufacturer = "Universal";
        p.model = "Generic Vehicle";
        p.modelYear = 2024;
        p.variant = "Standard 4-Camera Layout";
        p.profileVersion = 1;
        p.author = "Universal Platform";
        p.notes = "Universal profile for third-party vehicles or mock testing.";
        p.status = ProfileStatus.EXPERIMENTAL;
        p.dimensions = new VehicleDimensions(2700, 1850, 4500, 900, 900);
        p.rendering.carAssetId = "generic";
        p.rendering.carWidthRatio = 0.22f;
        p.rendering.carHeightRatio = 0.45f;
        setupQuadViewports(p);
        return p;
    }

    private void setupQuadViewports(VehicleProfile p) {
        // Standard 4-quad layout in combined frame:
        // Front: Top-Left (0, 0 to 0.5, 0.5)
        // Rear:  Bottom-Left (0, 0.5 to 0.5, 1.0)
        // Left:  Top-Right (0.5, 0 to 1.0, 0.5)
        // Right: Bottom-Right (0.5, 0.5 to 1.0, 1.0)
        p.getCamera(CameraRole.FRONT).cropRect = new RectFNormalized(0.0f, 0.0f, 0.5f, 0.5f);
        p.getCamera(CameraRole.REAR).cropRect = new RectFNormalized(0.0f, 0.5f, 0.5f, 1.0f);
        p.getCamera(CameraRole.LEFT).cropRect = new RectFNormalized(0.5f, 0.0f, 1.0f, 0.5f);
        p.getCamera(CameraRole.RIGHT).cropRect = new RectFNormalized(0.5f, 0.5f, 1.0f, 1.0f);
    }
}
