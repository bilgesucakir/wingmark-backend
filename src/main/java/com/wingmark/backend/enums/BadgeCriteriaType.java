package com.wingmark.backend.enums;

/** How a badge is earned. */
public enum BadgeCriteriaType {
    /** Total number of logs. */
    TOTAL_LOGS,
    /** Number of distinct species logged. */
    UNIQUE_SPECIES,
    /** Logs of baby birds. */
    BABY_LOGS,
    /** Logs without an identified species. */
    UNKNOWN_SPECIES_LOGS,
    /** Logs of pet birds. */
    PET_LOGS,
    /** Distinct species within a radius of one place. */
    SPECIES_IN_RADIUS,
    /** Sightings within a radius of one place. */
    SIGHTINGS_IN_RADIUS,
    /** Distinct species of one genus (first word of the scientific name); optional {@code criteriaMetadata.genus}. */
    SAME_GENUS_SPECIES,
    /** Log one specific species (criteriaMetadata.speciesId) criteriaValue times. */
    SPECIES_LOGS
}
