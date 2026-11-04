package com.nineoad.bydsignpicinpic.bev.profile;

public enum CameraInputType {
    COMBINED_QUAD_GRID(0, "Combined 4-Camera Quad Grid (e.g. BYD 1920x972)"),
    COMBINED_HORIZONTAL(1, "Combined Horizontal Strip (1x4)"),
    COMBINED_VERTICAL(2, "Combined Vertical Strip (4x1)"),
    INDIVIDUAL_STREAMS(3, "Individual Camera Streams (/dev/video0-3 or Camera2)"),
    EXTERNAL_SOURCE(4, "External Network / IPC Stream (RTSP / SharedMem)"),
    MOCK_TEST_STREAM(5, "Mock Calibration Checkerboard (Preview Only)");

    public final int id;
    public final String description;

    CameraInputType(int id, String description) {
        this.id = id;
        this.description = description;
    }
}
