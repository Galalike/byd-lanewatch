package com.nineoad.bydsignpicinpic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.nineoad.bydsignpicinpic.overlay.OverlayService;

/* JADX INFO: loaded from: classes.dex */
public class BootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if ("android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
            android.util.Log.i("BootReceiver", "Package replaced / updated -> Triggering autostart configuration");
            try {
                context.getSharedPreferences("pnp", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("pending_autostart_unblock", true)
                        .apply();
            } catch (Throwable ignored) {}
            com.nineoad.bydsignpicinpic.signal.BydKeyRemapService.triggerDisableAutostartAutomation(context.getApplicationContext());
        }
        Intent intent2 = new Intent(context, (Class<?>) OverlayService.class);
        String subAction = intent.getStringExtra("action");
        if (subAction != null) {
            intent2.setAction(subAction);
        } else if (!"android.intent.action.BOOT_COMPLETED".equals(intent.getAction())
                && !"android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())
                && !"ACC_ON".equals(intent.getAction())
                && !"android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
            intent2.setAction(intent.getAction());
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent2);
            } else {
                context.startService(intent2);
            }
        } catch (Throwable unused) {
        }
    }
}
