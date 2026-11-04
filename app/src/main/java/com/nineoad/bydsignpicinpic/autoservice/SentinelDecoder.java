package com.nineoad.bydsignpicinpic.autoservice;

/* JADX INFO: loaded from: classes.dex */
public final class SentinelDecoder {
    private static final int FEATURE_LINK_ERROR = 65535;
    private static final int NOT_INITIALIZED = 1048575;
    private static final int WRONG_DIRECTION = -10011;
    private static final int WRONG_TRANSACT = -10013;

    public static boolean isSentinel(int i) {
        return i == 65535 || i == NOT_INITIALIZED || i == WRONG_TRANSACT || i == WRONG_DIRECTION;
    }

    private SentinelDecoder() {
    }

    public static Integer decodeInt(int i) {
        if (i == 65535 || i == NOT_INITIALIZED || i == WRONG_TRANSACT || i == WRONG_DIRECTION) {
            return null;
        }
        return Integer.valueOf(i);
    }
}
