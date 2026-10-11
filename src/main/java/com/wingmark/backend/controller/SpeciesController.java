package com.wingmark.backend.controller;

import com.wingmark.backend.dto.species.CreateSpeciesImageRequestDto;
import com.wingmark.backend.dto.species.CreateSpeciesRequestDto;
import com.wingmark.backend.dto.species.SpeciesImageResponseDto;
import com.wingmark.backend.dto.species.SpeciesRecordingResponseDto;
import com.wingmark.backend.dto.species.SpeciesResponseDto;
import com.wingmark.backend.dto.species.UpdateSpeciesRequestDto;
import com.wingmark.backend.dto.species.SpeciesFacetsResponseDto;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.SeenFilter;
import com.wingmark.backend.service.SpeciesFilter;
import com.wingmark.backend.service.SpeciesService;
import com.wingmark.backend.service.XenoCantoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The species guide: public reference entries (description, images, sound) plus
 * admin-only management and curation endpoints.
 */
@Tag(name = "Species", description = "The species guide: public reference data plus admin curation tools")
@RestController
@RequestMapping("/api/species")
@RequiredArgsConstructor
public class SpeciesController {

    private final SpeciesService speciesService;
    private final XenoCantoService xenoCantoService;

    /** Returns a page of species, optionally filtered by a name substring and sortable. Public. */
    @Operation(summary = "Get all species", description = "Returns a page of species in the guide, optionally filtered by a substring (?search=) of the common name (English or Turkish) or the scientific name, case- and accent-insensitive (serce finds Serçe), " +
            "paginated via the standard ?page=/?size= params. Sortable via ?sort=<field>,<asc|desc>, e.g. " +
            "?sort=commonName.en,asc, ?sort=commonName.tr,desc, or ?sort=scientificName,asc. Also filterable by order and family (Latin names, ignoring case; several values allowed, " +
            "e.g. ?order=Passeriformes&family=Corvidae,Paridae) and by seen=seen|unseen (species the signed-in user has or has not logged, pet logs included; " +
            "401 UNAUTHENTICATED without a token). All filters combine with the search; totals and pages follow them. Public endpoint, except for the seen filter.")
    @GetMapping
    public ResponseEntity<Page<SpeciesResponseDto>> getAll(@RequestParam(required = false) String search,
                                                            @RequestParam(required = false) List<String> order,
                                                            @RequestParam(required = false) List<String> family,
                                                            @RequestParam(required = false) String seen,
                                                            @AuthenticationPrincipal UserPrincipal principal,
                                                            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(speciesService.getAll(filter(search, order, family, seen, principal), pageable));
    }

    /** Returns the orders and families of the guide with species counts for the filter menus. Public; the seen filter needs a signed-in user. */
    @Operation(summary = "Get species filter options", description = "Returns the orders and families in the guide with how many species match, " +
            "for the guide's filter menus: {orders: [{value, name, count}], families: [{value, order, name, count}]}. value is the Latin name to send back as ?order= or ?family=; " +
            "name is the real taxonomic name: the Latin name, or for a family the well-known Turkish -giller name when Accept-Language is Turkish and one is listed. A selected value with no species under the other filters is still returned with count 0. Takes the same ?search=, ?order=, ?family= and ?seen= filters as GET /api/species; " +
            "each menu is counted under all filters except its own. Public endpoint, except for the seen filter (401 UNAUTHENTICATED without a token). " +
            "Answers without a seen filter are cached for five minutes.")
    @GetMapping("/facets")
    public ResponseEntity<SpeciesFacetsResponseDto> getFacets(@RequestParam(required = false) String search,
                                                              @RequestParam(required = false) List<String> order,
                                                              @RequestParam(required = false) List<String> family,
                                                              @RequestParam(required = false) String seen,
                                                              @AuthenticationPrincipal UserPrincipal principal,
                                                              Locale locale) {
        SpeciesFacetsResponseDto facets = speciesService.getFacets(filter(search, order, family, seen, principal), locale);
        CacheControl cache = seen == null ? CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic() : CacheControl.noStore();
        return ResponseEntity.ok().cacheControl(cache).header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE).body(facets);
    }

    private static SpeciesFilter filter(String search, List<String> order, List<String> family, String seen, UserPrincipal principal) {
        SeenFilter seenFilter = null;
        if (seen != null && !seen.isBlank()) {
            seenFilter = switch (seen.trim().toLowerCase(Locale.ROOT)) {
                case "seen" -> SeenFilter.SEEN;
                case "unseen" -> SeenFilter.UNSEEN;
                default -> throw new BadRequestException(ErrorCode.INVALID_PARAMETER, "seen must be seen or unseen");
            };
        }
        return new SpeciesFilter(search, order, family, seenFilter, principal == null ? null : principal.getId());
    }

    /** Returns one species by id, including its reference images. Public. */
    @Operation(summary = "Get species by id", description = "Returns one species by id, including its reference images. Public endpoint.")
    @GetMapping("/{id}")
    public ResponseEntity<SpeciesResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(speciesService.getById(id));
    }

    /** Proxies a live Xeno-canto lookup for this species' call/song recordings. Public. */
    @Operation(summary = "Get species sound recordings", description = "Live-fetches this species' call/song recordings from Xeno-canto. Public endpoint.")
    @GetMapping("/{id}/sound")
    public ResponseEntity<List<SpeciesRecordingResponseDto>> getSound(@PathVariable UUID id) {
        SpeciesResponseDto species = speciesService.getById(id);
        return ResponseEntity.ok(xenoCantoService.findRecordings(species.scientificName()));
    }

    /** Admin-only: adds a new species to the guide. */
    @Operation(summary = "Create a species", description = "Admin-only. Adds a new species entry to the guide.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpeciesResponseDto> create(@Valid @RequestBody CreateSpeciesRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(speciesService.create(request));
    }

    /** Admin-only: updates an existing species entry. */
    @Operation(summary = "Update a species", description = "Admin-only. Updates an existing species entry.")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpeciesResponseDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateSpeciesRequestDto request) {
        return ResponseEntity.ok(speciesService.update(id, request));
    }

    /** Admin-only: removes a species (and its images) from the guide. */
    @Operation(summary = "Delete a species", description = "Admin-only. Removes a species and its reference images from the guide.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        speciesService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Admin-only: attaches a curated reference image (life stage + gender) to a species. */
    @Operation(summary = "Add a species image", description = "Admin-only. Attaches a curated reference image (life stage + gender) to a species. " +
            "For a Wikimedia Commons photo send commonsFileUrl (the file page address): " +
            "the license, credit line and source page are then read from Commons and stored, and files that are not public domain, CC0, CC BY or CC BY-SA are refused with 400.")
    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpeciesImageResponseDto> createImage(@PathVariable UUID id,
                                                           @Valid @RequestBody CreateSpeciesImageRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(speciesService.addImage(id, request));
    }

    /** Admin-only: removes one of a species' reference images. */
    @Operation(summary = "Delete a species image", description = "Admin-only. Removes one of a species' reference images.")
    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        speciesService.deleteImage(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
