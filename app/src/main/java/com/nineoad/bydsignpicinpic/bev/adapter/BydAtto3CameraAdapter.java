package com.nineoad.bydsignpicinpic.bev.adapter;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import com.nineoad.bydsignpicinpic.bev.profile.CameraInputType;
import com.nineoad.bydsignpicinpic.bev.profile.CameraRole;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;
import com.nineoad.bydsignpicinpic.camera.CameraFeed;

public class BydAtto3CameraAdapter implements ICameraInputAdapter {
    private static final String TAG = "BydAtto3CameraAdapter";
    private Context context;
    private VehicleProfile profile;
    private OnFrameAvailableListener listener;
    private CameraFeed feed;
    private Surface sharedSurface;
    private volatile boolean running = false;

    @Override
    public void initialize(Context context, VehicleProfile profile) {
        this.context = context;
        this.profile = profile;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        try {
            // Check direct feed availability from reverse-engineered BYD camera pipeline
            if (feed == null) {
                feed = new CameraFeed();
            }
            feed.setFps(25);
            Log.i(TAG, "BydAtto3CameraAdapter initialized with 1920x972 combined frame");
        } catch (Throwable t) {
            Log.e(TAG, "Error starting BYD camera adapter: " + t);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (feed != null) {
            try {
                feed.stop();
            } catch (Throwable ignored) {}
            feed = null;
        }
    }

    @Override
    public boolean isAvailable() {
        // Checks whether BYD hardware camera service or camera provider is accessible
        try {
            File devPano = new File("/dev/video0"); // Or BYD cameramanager service
            if (devPano.exists()) return true;

            Class<?> smClass = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Method getService = smClass.getMethod("getService", String.class);
            return getService.invoke(null, "autoservice") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public CameraInputType getInputType() {
        return CameraInputType.COMBINED_QUAD_GRID;
    }

    @Override
    public Surface getInputSurface(CameraRole role) {
        // BYD Atto 3 provides a single unified combined Surface for all 4 cameras
        return this.sharedSurface;
    }

    public void setSharedSurface(Surface surface) {
        this.sharedSurface = surface;
        if (this.feed != null && surface != null) {
            try {
                this.feed.addSurface(surface, 0);
            } catch (Throwable t) {
                Log.w(TAG, "addSurface failed: " + t);
            }
        }
    }

    @Override
    public void setOnFrameAvailableListener(OnFrameAvailableListener listener) {
        this.listener = listener;
    }
}
