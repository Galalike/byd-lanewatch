package com.nineoad.bydsignpicinpic.signal;

import android.content.Context;
import android.content.ContextWrapper;

/* JADX INFO: loaded from: classes.dex */
public class CarContext extends ContextWrapper {
    public CarContext(Context context) {
        super(context);
    }

    @Override // android.content.ContextWrapper, android.content.Context
    public void enforceCallingOrSelfPermission(String str, String str2) {
        if (str == null || !str.contains("BYDAUTO")) {
            super.enforceCallingOrSelfPermission(str, str2);
        }
    }

    @Override // android.content.ContextWrapper, android.content.Context
    public void enforceCallingPermission(String str, String str2) {
        if (str == null || !str.contains("BYDAUTO")) {
            super.enforceCallingPermission(str, str2);
        }
    }

    @Override // android.content.ContextWrapper, android.content.Context
    public int checkCallingOrSelfPermission(String str) {
        if (str == null || !str.contains("BYDAUTO")) {
            return super.checkCallingOrSelfPermission(str);
        }
        return 0;
    }

    @Override // android.content.ContextWrapper, android.content.Context
    public int checkPermission(String str, int i, int i2) {
        if (str == null || !str.contains("BYDAUTO")) {
            return super.checkPermission(str, i, i2);
        }
        return 0;
    }

    @Override // android.content.ContextWrapper, android.content.Context
    public int checkSelfPermission(String str) {
        if (str == null || !str.contains("BYDAUTO")) {
            return super.checkSelfPermission(str);
        }
        return 0;
    }
}
