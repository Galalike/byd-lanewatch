package com.nineoad.avc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class AvcClient {
    public static final String ACTION = "com.byd.action.AVCSERVICE";
    public static final int CMD_EXIT_AVC = 52;
    public static final int CMD_OPEN_AVC = 51;
    public static final int CMD_REOPEN_AVC = 53;
    public static final String PACKAGE = "com.byd.avc";
    public static final int Q_CURRENT_MODE = 35;
    private static final String TAG = "AvcClient";
    private final Context appContext;
    private ReplyListener replyListener;
    private Messenger service;
    private volatile boolean bound = false;
    private final ServiceConnection conn = new ServiceConnection() { // from class: com.nineoad.avc.AvcClient.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            AvcClient.this.service = new Messenger(iBinder);
            AvcClient.this.bound = true;
            Log.d(AvcClient.TAG, "connected to " + componentName);
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            AvcClient.this.service = null;
            AvcClient.this.bound = false;
        }
    };
    private final Messenger replyTo = new Messenger(new Handler(Looper.getMainLooper()) { // from class: com.nineoad.avc.AvcClient.2
        @Override // android.os.Handler
        public void handleMessage(Message message) {
            if (AvcClient.this.replyListener != null) {
                AvcClient.this.replyListener.onReply(message.what, message.arg2);
            }
        }
    });

    public interface ReplyListener {
        void onReply(int i, int i2);
    }

    public AvcClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setReplyListener(ReplyListener replyListener) {
        this.replyListener = replyListener;
    }

    public boolean bind() {
        Intent intent = new Intent(ACTION);
        intent.setPackage(PACKAGE);
        if (this.appContext.getPackageManager().resolveService(intent, 0) == null) {
            Log.w(TAG, "AVC service not resolvable");
            return false;
        }
        return this.appContext.bindService(intent, this.conn, 1);
    }

    public void unbind() {
        if (this.bound) {
            try {
                this.appContext.unbindService(this.conn);
            } catch (Exception unused) {
            }
            this.bound = false;
        }
    }

    public boolean isBound() {
        return this.bound;
    }

    public void send(int i) {
        Messenger messenger;
        if (!this.bound || (messenger = this.service) == null) {
            return;
        }
        try {
            messenger.send(Message.obtain((Handler) null, i));
        } catch (RemoteException e) {
            Log.e(TAG, "send " + i, e);
        }
    }

    public void query(int i) {
        if (!this.bound || this.service == null) {
            return;
        }
        Message messageObtain = Message.obtain((Handler) null, i);
        messageObtain.replyTo = this.replyTo;
        try {
            this.service.send(messageObtain);
        } catch (RemoteException e) {
            Log.e(TAG, "query " + i, e);
        }
    }
}
