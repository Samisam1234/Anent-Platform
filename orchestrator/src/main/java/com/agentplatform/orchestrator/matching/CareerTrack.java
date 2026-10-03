package com.agentplatform.orchestrator.matching;

public enum CareerTrack {
    SOFTWARE,
    HARDWARE,
    MIXED,
    UNKNOWN;


    public static CareerTrack fromString(String track) {
        if (track == null || track.isBlank()) {
            return UNKNOWN;
        }
        try {
            return CareerTrack.valueOf(track.trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}

