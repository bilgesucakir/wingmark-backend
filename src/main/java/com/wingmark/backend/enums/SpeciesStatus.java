package com.wingmark.backend.enums;

/**
 * Whether the user is confident about the species they selected for a log,
 * or just taking a guess. Only meaningful when a species has actually been
 * selected on the log (null when the species itself is unknown).
 */
public enum SpeciesStatus {
    GUESS,
    CONFIDENT
}
