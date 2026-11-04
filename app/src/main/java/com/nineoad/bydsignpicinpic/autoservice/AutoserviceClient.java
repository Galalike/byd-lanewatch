package com.nineoad.bydsignpicinpic.autoservice;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class AutoserviceClient {
    private static final Pattern PARCEL_REGEX = Pattern.compile("Parcel\\(00000000\\s+([0-9a-fA-F]{8})");
    private static final String TAG = "AutoserviceClient";
    public static final int TX_GET_FLOAT = 7;
    public static final int TX_GET_INT = 5;

    private static IBinder sAutoservice;
    private static boolean sBinderChecked = false;

    private static IBinder getBinder() {
        if (!sBinderChecked || (sAutoservice != null && !sAutoservice.isBinderAlive())) {
            sBinderChecked = true;
            try {
                Method getService = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
                sAutoservice = (IBinder) getService.invoke(null, "autoservice");
                if (sAutoservice != null) {
                    Log.i(TAG, "Direct autoservice IBinder acquired successfully");
                }
            } catch (Throwable th) {
                Log.w(TAG, "getService(autoservice) failed: " + th);
                sAutoservice = null;
            }
        }
        return sAutoservice;
    }

    private AutoserviceClient() {
    }

    public static Integer getInt(int i, int i2) {
        IBinder binder = getBinder();
        if (binder != null) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInt(i);
                data.writeInt(i2);
                if (binder.transact(TX_GET_INT, data, reply, 0)) {
                    reply.readException();
                    int val = reply.readInt();
                    return SentinelDecoder.decodeInt(val);
                }
            } catch (Throwable th) {
                // fallback to exec below
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
        Integer parcelInt;
        String strExec = exec(5, i, i2);
        if (strExec == null || (parcelInt = parseParcelInt(strExec)) == null) {
            return null;
        }
        return SentinelDecoder.decodeInt(parcelInt.intValue());
    }

    public static Integer getIntRaw(int i, int i2) {
        String strExec = exec(5, i, i2);
        if (strExec == null) {
            return null;
        }
        return parseParcelInt(strExec);
    }

    private static String exec(int i, int i2, int i3) {
        String str = "service call autoservice " + i + " i32 " + i2 + " i32 " + i3;
        try {
            Process processExec = Runtime.getRuntime().exec(new String[]{"sh", "-c", str});
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(processExec.getInputStream()));
            StringBuilder sb = new StringBuilder();
            while (true) {
                String line = bufferedReader.readLine();
                if (line == null) {
                    processExec.waitFor();
                    bufferedReader.close();
                    return sb.toString();
                }
                sb.append(line);
            }
        } catch (Exception e) {
            Log.e(TAG, "exec failed: " + str + " → " + e.getMessage());
            return null;
        }
    }

    private static Integer parseParcelInt(String str) {
        Matcher matcher = PARCEL_REGEX.matcher(str);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf((int) Long.parseLong(matcher.group(1), 16));
        } catch (Exception unused) {
            return null;
        }
    }
}
