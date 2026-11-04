package com.nineoad.bydsignpicinpic;

import android.app.Application;

/* JADX INFO: loaded from: classes.dex */
public class App extends Application {
    @Override // android.app.Application
    public void onCreate() {
        HiddenApi.exemptAll();
        super.onCreate();
    }
}
