package com.wingmark.backend.enums;

/** Who triggered an account deletion. */
public enum DeletionInitiator {
    SELF,
    ADMIN,
    /** Deleted by the scheduled job after two years without activity. */
    INACTIVITY,
    /** Deleted by the scheduled job because the email address was never verified. No email is sent. */
    UNVERIFIED
}
