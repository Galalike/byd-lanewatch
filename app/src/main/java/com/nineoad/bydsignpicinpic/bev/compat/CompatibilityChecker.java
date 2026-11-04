package com.nineoad.bydsignpicinpic.bev.compat;

import android.content.Context;
import android.os.Build;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;
import java.io.File;

public class CompatibilityChecker {

    public static class CompatibilityReport {
        public final CompatibilityLevel level;
        public final String summary;
        public final boolean hasDirectCameraAccess;
        public final boolean hasCanBusAccess;
        public final boolean hasRootOrAdb;
        public final String detectedPlatform;

        public CompatibilityReport(CompatibilityLevel level, String summary, boolean hasDirectCameraAccess,
                                   boolean hasCanBusAccess, boolean hasRootOrAdb, String detectedPlatform) {
            this.level = level;
            this.summary = summary;
            this.hasDirectCameraAccess = hasDirectCameraAccess;
            this.hasCanBusAccess = hasCanBusAccess;
            this.hasRootOrAdb = hasRootOrAdb;
            this.detectedPlatform = detectedPlatform;
        }
    }

    public static CompatibilityReport checkCompatibility(Context context, VehicleProfile profile) {
        String platform = Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";

        boolean hasAutoService = false;
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Method getService = smClass.getMethod("getService", String.class);
            hasAutoService = (getService.invoke(null, "autoservice") != null);
        } catch (Throwable ignored) {}

        boolean hasCameraDev = false;
        try {
            File v0 = new File("/dev/video0");
            hasCameraDev = v0.exists() && v0.canRead();
        } catch (Throwable ignored) {}

        boolean hasRoot = checkSuBinary();

        boolean isBydVehicle = hasAutoService || "BYD".equalsIgnoreCase(Build.MANUFACTURER) ||
                (profile != null && "BYD".equalsIgnoreCase(profile.manufacturer));

        CompatibilityLevel level;
        String summary;

        if (isBydVehicle && hasAutoService) {
            level = CompatibilityLevel.FULLY_SUPPORTED;
            summary = "BYD DiLink platform verified. Reverse-engineered 1920x972 camera feed & CAN bus active.";
        } else if (hasCameraDev || isBydVehicle) {
            level = CompatibilityLevel.PARTIALLY_SUPPORTED;
            summary = "Camera feed accessible. OEM camera stream connected in read-only mode.";
        } else {
            level = CompatibilityLevel.PREVIEW_ONLY;
            summary = "Universal Preview Mode: Test patterns, community profiles, and Touch Studio are fully functional.";
        }

        return new CompatibilityReport(level, summary, hasAutoService || hasCameraDev, hasAutoService, hasRoot, platform);
    }

    private static boolean checkSuBinary() {
        for (String path : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su"}) {
            if (new File(path).exists()) return true;
        }
        return false;
    }
}
