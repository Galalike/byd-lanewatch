package com.nineoad.bydsignpicinpic.bev.profile;

public enum ProfileStatus {
    EXPERIMENTAL("Experimental", "Not yet verified on physical vehicle. Use in preview mode only."),
    COMMUNITY_TESTED("Community Tested", "Reported working by community users."),
    VERIFIED("Verified", "Rigorously validated against ground truth parking line measurements.");

    public final String label;
    public final String description;

    ProfileStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
