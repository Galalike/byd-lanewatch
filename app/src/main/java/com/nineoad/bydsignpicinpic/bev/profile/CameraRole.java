package com.nineoad.bydsignpicinpic.bev.profile;

public enum CameraRole {
    FRONT(0, "Front"),
    REAR(1, "Rear"),
    LEFT(2, "Left"),
    RIGHT(3, "Right");

    public final int id;
    public final String label;

    CameraRole(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public static CameraRole fromId(int id) {
        for (CameraRole r : values()) {
            if (r.id == id) return r;
        }
        return FRONT;
    }
}
