package com.wingmark.backend.service;

import java.util.List;
import java.util.UUID;

/**
 * Filters of the species guide. Every part is optional and they combine.
 *
 * @param search   text matched against the names, or null
 * @param orders   orders (Latin names, any case), or null/empty for all
 * @param families families (Latin names, any case), or null/empty for all
 * @param seen     seen or unseen by {@code userId}, or null for both; needs a user
 * @param userId   the signed-in user for {@code seen}, or null
 */
public record SpeciesFilter(String search, List<String> orders, List<String> families, SeenFilter seen, UUID userId) {

    /** A filter with only a name search. */
    public static SpeciesFilter searching(String search) {
        return new SpeciesFilter(search, null, null, null, null);
    }

    /** Returns true if anything besides the name search is set. */
    public boolean hasTaxonomyOrSeen() {
        return (orders != null && !orders.isEmpty()) || (families != null && !families.isEmpty()) || seen != null;
    }
}
