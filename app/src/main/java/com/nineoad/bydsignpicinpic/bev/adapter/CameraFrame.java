package com.nineoad.bydsignpicinpic.bev.adapter;

import android.graphics.Bitmap;
import android.view.Surface;
import com.nineoad.bydsignpicinpic.bev.profile.CameraRole;

public class CameraFrame {
    public final CameraRole role;
    public final int width;
    public final int height;
    public final long timestampNs;
    public final Bitmap bitmap;
    public final Surface surface;
    public final int glTextureId;

    public CameraFrame(CameraRole role, int width, int height, long timestampNs, Bitmap bitmap, Surface surface, int glTextureId) {
        this.role = role;
        this.width = width;
        this.height = height;
        this.timestampNs = timestampNs;
        this.bitmap = bitmap;
        this.surface = surface;
        this.glTextureId = glTextureId;
    }
}
