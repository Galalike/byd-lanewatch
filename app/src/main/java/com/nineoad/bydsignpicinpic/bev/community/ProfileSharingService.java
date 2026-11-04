package com.nineoad.bydsignpicinpic.bev.community;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.nineoad.bydsignpicinpic.bev.profile.CameraCalibration;
import com.nineoad.bydsignpicinpic.bev.profile.CameraRole;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfileManager;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProfileSharingService {

    public static class ProfileDiff {
        public final String field;
        public final String valueA;
        public final String valueB;

        public ProfileDiff(String field, String valueA, String valueB) {
            this.field = field;
            this.valueA = valueA;
            this.valueB = valueB;
        }
    }

    public static String exportProfileToJson(VehicleProfile profile) throws Exception {
        return profile.toJsonString(true);
    }

    public static void copyProfileToClipboard(Context context, VehicleProfile profile) throws Exception {
        String json = exportProfileToJson(profile);
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("BEV_Profile_" + profile.id, json);
        cm.setPrimaryClip(clip);
    }

    public static File exportProfileToFile(VehicleProfile profile, File targetDir) throws Exception {
        if (!targetDir.exists()) targetDir.mkdirs();
        File file = new File(targetDir, "bev_profile_" + profile.id + ".json");
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(exportProfileToJson(profile).getBytes(StandardCharsets.UTF_8));
        fos.flush();
        fos.close();
        return file;
    }

    public static VehicleProfile importProfile(Context context, String jsonString) throws Exception {
        JSONObject obj = new JSONObject(jsonString);
        VehicleProfile profile = VehicleProfile.fromJson(obj);
        if (profile == null || profile.id == null) {
            throw new IllegalArgumentException("Malformed vehicle profile JSON");
        }
        VehicleProfileManager.getInstance(context).saveProfile(profile);
        return profile;
    }

    public static VehicleProfile duplicateProfile(Context context, VehicleProfile source, String newName, String newAuthor) {
        String newId = source.id + "_custom_" + System.currentTimeMillis() % 10000;
        VehicleProfile duplicate = source.copy(newId, newAuthor);
        if (newName != null && !newName.isEmpty()) {
            duplicate.model = newName;
        }
        VehicleProfileManager.getInstance(context).saveProfile(duplicate);
        return duplicate;
    }

    public static List<ProfileDiff> compareProfiles(VehicleProfile a, VehicleProfile b) {
        List<ProfileDiff> diffs = new ArrayList<>();
        if (a == null || b == null) return diffs;

        if (!a.model.equals(b.model)) diffs.add(new ProfileDiff("Model", a.model, b.model));
        if (a.modelYear != b.modelYear) diffs.add(new ProfileDiff("Year", "" + a.modelYear, "" + b.modelYear));
        if (a.profileVersion != b.profileVersion) diffs.add(new ProfileDiff("Version", "v" + a.profileVersion, "v" + b.profileVersion));
        if (a.status != b.status) diffs.add(new ProfileDiff("Status", a.status.label, b.status.label));

        for (CameraRole role : CameraRole.values()) {
            CameraCalibration calA = a.getCamera(role);
            CameraCalibration calB = b.getCamera(role);
            if (Math.abs(calA.rollDeg - calB.rollDeg) > 0.05f) {
                diffs.add(new ProfileDiff(role.label + " Roll", String.format("%.2f°", calA.rollDeg), String.format("%.2f°", calB.rollDeg)));
            }
            if (Math.abs(calA.fineRotationDeg - calB.fineRotationDeg) > 0.05f) {
                diffs.add(new ProfileDiff(role.label + " Fine Rot", String.format("%.2f°", calA.fineRotationDeg), String.format("%.2f°", calB.fineRotationDeg)));
            }
            if (Math.abs(calA.scaleX - calB.scaleX) > 0.01f || Math.abs(calA.scaleY - calB.scaleY) > 0.01f) {
                diffs.add(new ProfileDiff(role.label + " Scale", String.format("%.2f, %.2f", calA.scaleX, calA.scaleY), String.format("%.2f, %.2f", calB.scaleX, calB.scaleY)));
            }
        }
        return diffs;
    }
}
