package com.nineoad.bydsignpicinpic.bev.compat;

public enum CompatibilityLevel {
    FULLY_SUPPORTED(
            "Fully Supported",
            "Actual hardware camera pipeline accessible. Real-time calibration & image processing operational.",
            0xFF00E676 // Bright Emerald Green
    ),
    PARTIALLY_SUPPORTED(
            "Partially Supported",
            "Camera frames accessible, but some CAN bus signals or vehicle-specific controls are unavailable.",
            0xFFFFD600 // Warning Amber
    ),
    PREVIEW_ONLY(
            "Preview Only",
            "Hardware camera pipeline restricted or vehicle not connected. Calibration Studio fully operational using sample/mock frames.",
            0xFF00B0FF // Electric Cyan
    ),
    UNSUPPORTED(
            "Unsupported",
            "Camera input unavailable, encrypted, or incompatible on this hardware.",
            0xFFFF1744 // Crimson Red
    );

    public final String label;
    public final String details;
    public final int color;

    CompatibilityLevel(String label, String details, int color) {
        this.label = label;
        this.details = details;
        this.color = color;
    }
}
