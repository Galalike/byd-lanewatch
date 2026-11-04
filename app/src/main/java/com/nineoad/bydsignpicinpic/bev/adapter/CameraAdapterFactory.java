package com.nineoad.bydsignpicinpic.bev.adapter;

import android.content.Context;
import com.nineoad.bydsignpicinpic.bev.profile.CameraInputType;
import com.nineoad.bydsignpicinpic.bev.profile.VehicleProfile;

public class CameraAdapterFactory {
    public static ICameraInputAdapter createAdapter(Context context, VehicleProfile profile) {
        ICameraInputAdapter adapter;

        if (profile != null && "BYD".equalsIgnoreCase(profile.manufacturer)) {
            BydAtto3CameraAdapter bydAdapter = new BydAtto3CameraAdapter();
            if (bydAdapter.isAvailable()) {
                adapter = bydAdapter;
            } else {
                adapter = new MockCameraFrameAdapter();
            }
        } else if (profile != null && profile.inputType == CameraInputType.MOCK_TEST_STREAM) {
            adapter = new MockCameraFrameAdapter();
        } else {
            // Default universal fallback
            adapter = new MockCameraFrameAdapter();
        }

        adapter.initialize(context, profile);
        return adapter;
    }
}
