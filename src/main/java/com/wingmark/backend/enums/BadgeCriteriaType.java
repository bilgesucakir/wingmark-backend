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
    /** Logs of the user's favorite species. Shown only to users who have one. */
    FAVORITE_SPECIES_LOGS,
    /** Log one specific species (criteriaMetadata.speciesId) criteriaValue times. */
    SPECIES_LOGS,
    /** Logs seen between 04:00 and 06:00 local time; needs the log's {@code utcOffsetMinutes}. */
    EARLY_BIRD_LOGS,
    /** One species logged as a male, a female and a baby, each in a different log (progress 0-3). */
    FAMILY_PORTRAIT,
    /** Distinct species within any single genus; subspecies count as their species. Pet logs count. */
    SAME_GENUS_ANY,
    /** Logs of species whose conservation status is Endangered or Critically Endangered; pet logs are ignored. */
    RARE_SPECIES_LOGS,
    /** Earned badges among all other badges; computed after all others, target = the number of other badges. */
    ALL_OTHER_BADGES
}
