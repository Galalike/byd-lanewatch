package com.nineoad.bydsignpicinpic.overlay;

import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.util.Log;
import dalvik.system.PathClassLoader;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.Method;

/** Read-only metadata and resources from the installed OEM AVC. Never calibrates the car. */
public final class OemPanoProfile {
    private static final String TAG = "OemPanoProfile";
    private static volatile String nativeDirectory;
    public static String nativeDirectory() { return nativeDirectory; }
    public String car, subCar, color, country;
    public File model, parameters, panorama, intrinsic;
    public int width, height, cameraType, cameraParameterType;
    public boolean vehicleCalibration;

    public static OemPanoProfile resolve(Context context) throws Exception {
        PackageInfo pkg = context.getPackageManager().getPackageInfo("com.byd.avc", 0);
        ApplicationInfo info = pkg.applicationInfo;
        AssetManager assets = context.createPackageContext("com.byd.avc", 0).getAssets();
        OemPanoProfile p = new OemPanoProfile();
        JSONObject config = null;
        File live = new File("/collect2/autovideo/initSdkParam.json");
        if (live.canRead()) {
            try (InputStream in = new FileInputStream(live)) { config = new JSONObject(readText(in)); }
        }
        String resolution, modelAsset, panoAsset;
        if (config != null) {
            if (!"PI".equals(config.getString("SDK"))) throw new IOException("Unsupported OEM panorama SDK: " + config.optString("SDK"));
            p.car = name(config.has("carTyep") ? config.getString("carTyep") : config.getString("carType"));
            p.subCar = name(config.optString("subCarType", p.car));
            p.color = name(config.optString("carBodyColor", "def"));
            resolution = config.getString("resolution");
            p.cameraType = config.getInt("cameraType");
            p.cameraParameterType = config.getInt("cameraParameterType");
            modelAsset = assetPath(config.getString("resPath"), "resource_pi/");
            panoAsset = config.getString("pano_resources_path").contains("pano_3d_resources") ? "pano_3d_resources/" + p.car : "pano_resources";
        } else {
            java.util.concurrent.FutureTask<String[]> metadata = new java.util.concurrent.FutureTask<>(() -> readMetadata(context, info, p));
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) metadata.run();
            else new android.os.Handler(android.os.Looper.getMainLooper()).post(metadata);
            String[] selected = metadata.get(10, java.util.concurrent.TimeUnit.SECONDS);
            resolution = selected[0]; modelAsset = selected[1]; panoAsset = selected[2];
        }
        switch (resolution) {
            case "960P": p.width = 1280; p.height = 960; break;
            case "720P": p.width = 1280; p.height = 720; break;
            case "480P": p.width = 640; p.height = 480; break;
            default: throw new IOException("Unsupported camera resolution: " + resolution);
        }
        p.country = property("persist.sys.byd.countrycode", "");
        File cache = new File(context.getFilesDir(), "oem_avc/" + pkg.lastUpdateTime + "/" + p.car);
        p.model = new File(cache, modelAsset);
        p.parameters = new File(cache, "resource_pi/" + p.car + "/" + resolution);
        p.panorama = new File(cache, panoAsset);
        // Copies are app-owned: native initialization cannot overwrite the OEM calibration.
        copyTree(assets, modelAsset, p.model);
        copyTree(assets, "resource_pi/" + p.car + "/" + resolution, p.parameters);
        copyTree(assets, panoAsset, p.panorama);
        if (!new File(p.parameters, "param.txt").isFile()) throw new IOException("OEM camera parameter file missing");
        File liveParam = config == null ? new File("/collect2/autovideo/resource_pi/" + p.car + "/" + resolution) : new File(config.getString("paramPath"));
        if (liveParam.canRead()) copyFiles(liveParam, p.parameters);
        File internal = new File(config == null ? "/collect2/autovideo/InternalParameters.txt" : config.optString("internalParamPath", "/collect2/autovideo/InternalParameters.txt"));
        if (!internal.isFile()) internal = new File("/collect2/autovideo/InternalParameters.txt");
        p.intrinsic = new File(cache, "InternalParameters.txt");
        p.vehicleCalibration = internal.canRead();
        if (p.vehicleCalibration) { try (InputStream in = new FileInputStream(internal)) { copy(in, p.intrinsic); } }
        else { try (InputStream in = assets.open("InternalParameters.txt")) { copy(in, p.intrinsic); } }
        File nativeFile = new File(info.nativeLibraryDir, "libVehiclePano.so");
        if (!nativeFile.canRead()) throw new IOException("OEM PI native library is not accessible: " + nativeFile);
        File nativeCache = new File(context.getFilesDir(), "oem_avc/" + pkg.lastUpdateTime + "/native");
        for (String lib : new String[]{"libc++_shared.so", "libopencv_java4.so", "libCarAutoCorrect.so", "libVehiclePano.so"}) {
            File source = new File(info.nativeLibraryDir, lib), target = new File(nativeCache, lib);
            if (source.canRead() && (!target.isFile() || target.length() != source.length())) {
                try (InputStream in = new FileInputStream(source)) { copy(in, target); }
            }
        }
        nativeDirectory = nativeCache.getAbsolutePath();
        String summary = p.car + "/" + p.subCar + " " + resolution + ", camera=" + p.cameraType + "/" + p.cameraParameterType + ", calibration=" + (p.vehicleCalibration ? "vehicle" : "OEM defaults (unverified)");
        context.getSharedPreferences("pnp", 0).edit().putString("pano_profile_summary", summary).apply();
        Log.i(TAG, summary + ", native=" + nativeDirectory);
        return p;
    }
    private static String[] readMetadata(Context context, ApplicationInfo info, OemPanoProfile p) throws Exception {
        Class<?> table = Class.forName("android.provider.CarSettings$UserTableData");
        Method read = table.getMethod("getSystemInt", ContentResolver.class, String.class);
        int value = ((Number) read.invoke(null, context.getContentResolver(), "model_value")).intValue();
        int online = ((Number) read.invoke(null, context.getContentResolver(), "panorama_online")).intValue();
        if (online != 1 && online != 4) throw new IOException("Unsupported panorama_online=" + online + " (PI adapter only)");
        // Model families from the OEM DeviceStates mapping. Unknown values fail closed.
        switch (value) {
            case 81: p.car = "sc3ebe"; break;
            case 90: p.car = "sa2hpq"; break;
            case 127: case 186: p.car = "em_3ui"; break;
            case 128: p.car = "sc2e"; break;
            case 138: p.car = "eke"; break;
            case 141: p.car = "ha2hj"; break;
            case 143: p.car = "pa"; break;
            case 160: p.car = "sc3e"; break;
            case 162: p.car = "sa3hkl"; break;
            case 173: p.car = "sa3ef"; break;
            case 175: p.car = "vde"; break;
            case 191: p.car = "ew"; break;
            case 215: p.car = "eqeal"; break;
            case 217: p.car = "ha6h"; break;
            case 220: p.car = "meey"; break;
            case 230: p.car = "eqe"; break;
            case 244: p.car = "hx"; break;
            case 251: p.car = "eqedu"; break;
            case 353: p.car = "sthny"; break;
            default: throw new IOException("Model " + value + " requires a readable OEM initSdkParam.json");
        }
        Class<?> custom = Class.forName("android.widget.CustomVehicleConfig");
        Object vehicle = custom.getMethod("getInstance", Context.class).invoke(null, context);
        boolean right = (Boolean) custom.getMethod("isRightDriver").invoke(vehicle);
        ClassLoader loader = new PathClassLoader(info.sourceDir, context.getClassLoader());
        // Constants class only. Never initialize OEM controllers/services in our process.
        Class<?> names = loader.loadClass("com.byd.avc.util.CarTypeList");
        String symbolic;
        switch (value) {
            case 81: symbolic = "SC3EBE"; break;
            case 90: symbolic = "SA2HPQ"; break;
            case 127: symbolic = "EM_3UI"; break;
            case 186: symbolic = "EM2EU"; break;
            case 128: symbolic = "SC2E"; break;
            case 162: symbolic = "SA3_HKL"; break;
            case 173: symbolic = "SA3_EF"; break;
            default: symbolic = p.car.toUpperCase(java.util.Locale.ROOT);
        }
        String baseName = name((String) names.getField("CAR_TYPE_" + symbolic).get(null));
        // Family and subtype names differ on some cars (e.g. em_3ui / em2eu_right).
        if (value != 186) p.car = baseName;
        p.subCar = baseName;
        if (right) {
            try { p.subCar = name((String) names.getField("CAR_TYPE_" + symbolic + "_RIGHT").get(null)); }
            catch (NoSuchFieldException ignored) { }
        }
        String modelAsset = "resource_pi/" + p.car + "/CarModel";
        if (value == 128) modelAsset += "/bev";
        else if (value == 127) modelAsset += "/em_3ui";
        else if (value == 186 || value == 81 || value == 90 || value == 138 || value == 162 || value == 175 || value == 191 || value == 220 || value == 251) modelAsset += "/" + p.subCar;
        String camera = property("vehicle.config.pano_cam", "SONY_19");
        switch (camera) {
            case "SONY_19": p.cameraType = 1; break;
            case "OV_0X1F": p.cameraType = 8; break;
            case "TVI": p.cameraType = 3; break;
            case "AHD": p.cameraType = 4; break;
            case "SC120AT_960": p.cameraType = 10; break;
            case "LIUBU_BF314C_960": p.cameraType = 11; break;
            case "JUNGE_0X1F_960": p.cameraType = 12; break;
            default: throw new IOException("Unsupported camera sensor: " + camera);
        }
        p.cameraParameterType = "OV_480P_02".equals(property("vehicle.config.pano_l_cam", "OV_480P_01")) ? 1 : 0;
        p.color = context.getSharedPreferences("pnp", 0).getInt("car_model_color", 0) == 1 ? "white" : "grey";
        Log.w(TAG, "Live OEM calibration unreadable; using this model's OEM assets; model_value=" + value);
        return new String[]{online == 1 ? "960P" : "480P", modelAsset, "pano_3d_resources/" + p.car};
    }
    private static String name(String s) throws IOException {
        if (s == null || !s.matches("[a-zA-Z0-9_-]+")) throw new IOException("Invalid OEM model identifier");
        return s;
    }
    private static String assetPath(String path, String prefix) throws IOException {
        int start = path.indexOf(prefix);
        if (start < 0 || path.contains("..")) throw new IOException("Unknown OEM resource path: " + path);
        return path.substring(start);
    }
    private static String property(String key, String fallback) {
        try { return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class).invoke(null, key, fallback); }
        catch (Exception ignored) { return fallback; }
    }
    private static String readText(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] data = new byte[4096]; int n;
        while ((n = in.read(data)) != -1) { if (out.size() + n > 65536) throw new IOException("OEM profile too large"); out.write(data, 0, n); }
        return out.toString("UTF-8");
    }
    private static void copyTree(AssetManager assets, String path, File target) throws IOException {
        String[] children = assets.list(path);
        if (children != null && children.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create " + target);
            for (String child : children) copyTree(assets, path + "/" + child, new File(target, child));
        } else if (!target.isFile() || target.length() == 0) {
            try (InputStream in = assets.open(path)) { copy(in, target); }
        }
    }
    private static void copyFiles(File source, File target) throws IOException {
        File[] files = source.listFiles();
        if (files == null) throw new IOException("Cannot list OEM parameters");
        for (File f : files) {
            if (f.isDirectory()) copyFiles(f, new File(target, f.getName()));
            else try (InputStream in = new FileInputStream(f)) { copy(in, new File(target, f.getName())); }
        }
    }
    private static void copy(InputStream in, File target) throws IOException {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        File temporary = new File(parent, target.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(temporary)) { byte[] data = new byte[32768]; int n; while ((n = in.read(data)) != -1) out.write(data, 0, n); }
        if (!temporary.renameTo(target)) throw new IOException("Cannot publish " + target);
    }
}
