package com.wingmark.backend.dto.species;

import java.util.List;

/**
 * The orders and families of the species guide with how many species match, for the guide's filter menus.
 *
 * @param orders   orders with their species count, most species first
 * @param families families, each with its order, with their species count, most species first
 */
public record SpeciesFacetsResponseDto(List<OrderFacet> orders, List<FamilyFacet> families) {

    /**
     * One order.
     *
     * @param value the Latin name; send it back as the {@code order} parameter
     * @param name  the name in the request language, or the Latin name
     * @param count how many species match
     */
    public record OrderFacet(String value, String name, long count) {
    }

    /**
     * One family.
     *
     * @param value the Latin name; send it back as the {@code family} parameter
     * @param order the Latin name of the family's order
     * @param name  the name in the request language, or the Latin name
     * @param count how many species match
     */
    public record FamilyFacet(String value, String order, String name, long count) {
    }
}
