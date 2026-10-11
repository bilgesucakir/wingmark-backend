package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Species;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Species queries that combine name search, order, family and seen/unseen. */
public interface SpeciesSearchRepository {

    /** One order and family with the number of species in it. */
    record FacetRow(String order, String family, long count) {
    }

    /**
     * Finds species matching every given filter. {@code nameRegex}, {@code orders}, {@code families} and {@code seen}
     * may each be null; orders and families match ignoring case; {@code seenIds} are the species the user has logged.
     */
    Page<Species> search(String nameRegex, List<String> orders, List<String> families,
                         Collection<UUID> seenIds, Boolean seen, Pageable pageable);

    /** Counts species per order and family under the same filters. */
    List<FacetRow> facetRows(String nameRegex, List<String> orders, List<String> families,
                             Collection<UUID> seenIds, Boolean seen);
}
