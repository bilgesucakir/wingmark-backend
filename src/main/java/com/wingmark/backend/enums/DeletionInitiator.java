package com.wingmark.backend.enums;

/** Who triggered an account deletion. */
public enum DeletionInitiator {
    SELF,
    ADMIN,
    /** Deleted by the scheduled job after two years without activity. */
    INACTIVITY
}
