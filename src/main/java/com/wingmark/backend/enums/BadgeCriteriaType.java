package com.wingmark.backend.enums;

public enum BadgeCriteriaType {
    TOTAL_LOGS,
    UNIQUE_SPECIES,
    BABY_LOGS,
    UNKNOWN_SPECIES_LOGS,
    PET_LOGS,
    SPECIES_IN_RADIUS,
    SIGHTINGS_IN_RADIUS,
    /** Log one specific species (criteriaMetadata.speciesId) criteriaValue times. */
    SPECIES_LOGS
}
