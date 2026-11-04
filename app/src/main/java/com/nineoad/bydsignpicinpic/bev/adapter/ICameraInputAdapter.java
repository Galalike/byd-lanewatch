package com.nineoad.bydsignpicinpic.bev.adapter;

import android.content.Context;
import android.view.Surface;
import com.nineoad.bydsignpicinpic.bev.profile.CameraInputType;
import com.nineoad.bydsignpicinpic.bev.profile.CameraRole;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;

public interface ICameraInputAdapter {
    interface OnFrameAvailableListener {
        void onFrameAvailable(CameraRole role, CameraFrame frame);
    }

    void initialize(Context context, VehicleProfile profile);

    void start();

    void stop();

    boolean isAvailable();

    CameraInputType getInputType();

    /**
     * If the adapter provides a direct Surface for hardware/GL decoders.
     */
    Surface getInputSurface(CameraRole role);

    void setOnFrameAvailableListener(OnFrameAvailableListener listener);
}
