package com.wingmark.backend.enums;

/** Documents a user can accept. Marketing consent isn't modelled: Wingmark sends no marketing email. */
public enum ConsentType {
    TERMS,
    PRIVACY,
    /** Confirmation that the user is at least the minimum age (13); the version is the minimum age. */
    AGE
}
