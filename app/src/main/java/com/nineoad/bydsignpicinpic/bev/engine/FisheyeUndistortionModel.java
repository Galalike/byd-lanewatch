package com.nineoad.bydsignpicinpic.bev.engine;

import com.nineoad.bydsignpicinpic.bev.profile.CameraCalibration;
import com.nineoad.bydsignpicinpic.bev.profile.Point2D;

public class FisheyeUndistortionModel {

    /**
     * Corrects a distorted normalized UV coordinate (0..1) into perspective-rectified coordinate.
     */
    public static Point2D undistort(Point2D uvDistorted, CameraCalibration cal) {
        if (cal == null) return uvDistorted;
        float cx = cal.opticalCenterX;
        float cy = cal.opticalCenterY;
        float k1 = cal.distortionK1;
        float k2 = cal.distortionK2;

        float dx = uvDistorted.x - cx;
        float dy = uvDistorted.y - cy;
        float r2 = dx * dx + dy * dy;
        float r = (float) Math.sqrt(r2);

        if (r < 1e-6f) return uvDistorted;

        // Polynomial radial factor: (1 + k1*r^2 + k2*r^4)
        float factor = 1.0f + k1 * r2 + k2 * r2 * r2;
        float correctedX = cx + dx * factor;
        float correctedY = cy + dy * factor;

        return new Point2D(correctedX, correctedY);
    }
}
