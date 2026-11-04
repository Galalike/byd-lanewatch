package com.nineoad.bydsignpicinpic.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public class UpdateChecker {
    private static final int CONN_TIMEOUT_MS = 10000;
    private static final String TAG = "LaneWatch/Update";
    private static final String DEFAULT_STABLE_URL = "https://raw.githubusercontent.com/Galalike/byd-lanewatch/main/ota/{channel}.json";
    private final Context ctx;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Listener {
        void onChecking();
        void onDownloadComplete(File file);
        void onDownloadProgress(int progress, long downloadedBytes, long totalBytes);
        void onError(String str);
        void onUpToDate(int currentVersionCode);
        void onUpdateAvailable(UpdateInfo updateInfo);
    }

    public static class UpdateInfo {
        public int versionCode;
        public String versionName;
        public String url;
        public long size;
        public String notes;
        public String releaseDate;
        public String whatsNew;
        public String channel;
    }

    public UpdateChecker(Context context) {
        this.ctx = context.getApplicationContext();
    }

    public String getOtaServerUrl() {
        android.content.SharedPreferences sp = this.ctx.getSharedPreferences("pnp", Context.MODE_PRIVATE);
        String url = sp.getString("ota_update_url", DEFAULT_STABLE_URL);
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_STABLE_URL;
        }
        return url.trim();
    }

    public String getResolvedOtaUrl() {
        String base = getOtaServerUrl();
        if (base == null || base.trim().isEmpty()) {
            return "";
        }
        base = base.trim();
        String ch = getOtaChannel();
        if (base.contains("{channel}")) {
            return base.replace("{channel}", ch);
        }
        if (base.endsWith("/")) {
            return base + ch + ".json";
        }
        if (base.endsWith("/stable.json") && "beta".equalsIgnoreCase(ch)) {
            return base.substring(0, base.length() - 11) + "beta.json";
        }
        if (base.endsWith("/beta.json") && "stable".equalsIgnoreCase(ch)) {
            return base.substring(0, base.length() - 9) + "stable.json";
        }
        return base;
    }

    public void setOtaServerUrl(String url) {
        android.content.SharedPreferences sp = this.ctx.getSharedPreferences("pnp", Context.MODE_PRIVATE);
        sp.edit().putString("ota_update_url", url != null ? url.trim() : "").apply();
    }

    public String getOtaChannel() {
        android.content.SharedPreferences sp = this.ctx.getSharedPreferences("pnp", Context.MODE_PRIVATE);
        return sp.getString("ota_channel", "stable");
    }

    public void setOtaChannel(String channel) {
        android.content.SharedPreferences sp = this.ctx.getSharedPreferences("pnp", Context.MODE_PRIVATE);
        sp.edit().putString("ota_channel", channel).apply();
    }

    private int currentCode() {
        try {
            return this.ctx.getPackageManager().getPackageInfo(this.ctx.getPackageName(), 0).versionCode;
        } catch (Exception unused) {
            return 0;
        }
    }

    public void check(final Listener listener) {
        if (listener != null) {
            this.mainHandler.post(listener::onChecking);
        }
        this.exec.execute(() -> checkInternal(listener));
    }

    private void checkInternal(final Listener listener) {
        String serverUrl = getResolvedOtaUrl();
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            fail(listener, "ยังไม่ได้กำหนด URL เซิร์ฟเวอร์อัปเดต (OTA Endpoint Not Configured)\nกรุณากำหนด URL หรือ Endpoint ของระบบจัดการอัปเดต");
            return;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(serverUrl).openConnection();
            conn.setConnectTimeout(CONN_TIMEOUT_MS);
            conn.setReadTimeout(CONN_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "BYDLaneWatch-OTA/" + currentCode());
            conn.setRequestProperty("X-Update-Channel", getOtaChannel());
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                fail(listener, "เซิร์ฟเวอร์อัปเดตตอบกลับข้อผิดพลาด (HTTP " + responseCode + ")");
                return;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject jSONObject = new JSONObject(sb.toString());
            final UpdateInfo updateInfo = new UpdateInfo();
            updateInfo.versionCode = jSONObject.getInt("versionCode");
            updateInfo.versionName = jSONObject.optString("versionName", "?");
            updateInfo.url = jSONObject.getString("url");
            updateInfo.size = jSONObject.optLong("size", 0L);
            updateInfo.releaseDate = jSONObject.optString("releaseDate", "Unknown");
            updateInfo.whatsNew = jSONObject.optString("whatsNew", "");
            updateInfo.channel = jSONObject.optString("channel", getOtaChannel());

            JSONArray notesArray = jSONObject.optJSONArray("notes");
            if (notesArray != null) {
                StringBuilder sb2 = new StringBuilder();
                for (int i = 0; i < notesArray.length(); i++) {
                    if (i > 0) sb2.append("\n");
                    sb2.append(notesArray.optString(i, ""));
                }
                updateInfo.notes = sb2.toString();
            } else {
                updateInfo.notes = jSONObject.optString("notes", "");
            }

            final int iCurrentCode = currentCode();
            Log.d(TAG, "OTA remote=" + updateInfo.versionCode + " local=" + iCurrentCode);
            if (updateInfo.versionCode > iCurrentCode) {
                if (listener != null) {
                    this.mainHandler.post(() -> listener.onUpdateAvailable(updateInfo));
                }
            } else if (listener != null) {
                this.mainHandler.post(() -> listener.onUpToDate(iCurrentCode));
            }
        } catch (Exception e) {
            Log.e(TAG, "check failed", e);
            fail(listener, "ไม่สามารถเชื่อมต่อเซิร์ฟเวอร์อัปเดตได้: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void download(final UpdateInfo updateInfo, final Listener listener) {
        this.exec.execute(() -> downloadInternal(updateInfo, listener));
    }

    private void downloadInternal(UpdateInfo updateInfo, final Listener listener) {
        HttpURLConnection conn = null;
        try {
            File dir = new File(this.ctx.getExternalCacheDir(), "updates");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            final File targetApk = new File(dir, "update.apk");
            File dlFile = new File(dir, "update.dl");
            if (targetApk.exists()) {
                targetApk.delete();
            }
            if (dlFile.exists()) {
                dlFile.delete();
            }
            conn = (HttpURLConnection) new URL(updateInfo.url).openConnection();
            conn.setConnectTimeout(CONN_TIMEOUT_MS);
            conn.setReadTimeout(60000);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                fail(listener, "ดาวน์โหลดไม่สำเร็จ (HTTP " + responseCode + ")");
                return;
            }
            long contentLengthLong = conn.getContentLengthLong();
            if (contentLengthLong <= 0 && updateInfo.size > 0) {
                contentLengthLong = updateInfo.size;
            }
            final long totalBytes = contentLengthLong;
            try (InputStream inputStream = conn.getInputStream();
                 FileOutputStream fileOutputStream = new FileOutputStream(dlFile)) {
                byte[] bArr = new byte[8192];
                long downloaded = 0;
                int lastProgress = -1;
                int read;
                while ((read = inputStream.read(bArr)) != -1) {
                    fileOutputStream.write(bArr, 0, read);
                    downloaded += read;
                    if (totalBytes > 0) {
                        final int progress = (int) ((100 * downloaded) / totalBytes);
                        final long curDownloaded = downloaded;
                        if (progress != lastProgress) {
                            if (listener != null) {
                                this.mainHandler.post(() -> listener.onDownloadProgress(progress, curDownloaded, totalBytes));
                            }
                            lastProgress = progress;
                        }
                    }
                }
            }

            if (isGzip(dlFile)) {
                try (GZIPInputStream gZIPInputStream = new GZIPInputStream(new FileInputStream(dlFile));
                     FileOutputStream fileOutputStream2 = new FileOutputStream(targetApk)) {
                    byte[] bArr2 = new byte[8192];
                    int read2;
                    while ((read2 = gZIPInputStream.read(bArr2)) != -1) {
                        fileOutputStream2.write(bArr2, 0, read2);
                    }
                }
                dlFile.delete();
            } else {
                if (targetApk.exists()) {
                    targetApk.delete();
                }
                if (!dlFile.renameTo(targetApk)) {
                    fail(listener, "เตรียมไฟล์ติดตั้งไม่สำเร็จ");
                    return;
                }
            }

            if (listener != null) {
                this.mainHandler.post(() -> listener.onDownloadComplete(targetApk));
            }
        } catch (Exception e) {
            Log.e(TAG, "download failed", e);
            fail(listener, "ดาวน์โหลดไม่สำเร็จ: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean isGzip(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[2];
            if (fis.read(bytes) == 2) {
                return (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139;
            }
        } catch (Exception unused) {
        }
        return false;
    }

    private void fail(final Listener listener, final String msg) {
        if (listener != null) {
            this.mainHandler.post(() -> listener.onError(msg));
        }
    }

    public void install(File file) {
        try {
            Uri uriForFile = FileProvider.getUriForFile(this.ctx, this.ctx.getPackageName() + ".updateprovider", file);
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setDataAndType(uriForFile, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            this.ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "install failed", e);
            Toast.makeText(this.ctx, "เปิด installer ไม่ได้: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
