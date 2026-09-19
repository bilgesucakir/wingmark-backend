package com.wingmark.backend.controller;

import com.wingmark.backend.dto.species.CreateSpeciesImageRequestDto;
import com.wingmark.backend.dto.species.CreateSpeciesRequestDto;
import com.wingmark.backend.dto.species.PhotoCandidateDto;
import com.wingmark.backend.dto.species.SpeciesImageResponseDto;
import com.wingmark.backend.dto.species.SpeciesRecordingResponseDto;
import com.wingmark.backend.dto.species.SpeciesResponseDto;
import com.wingmark.backend.dto.species.UpdateSpeciesRequestDto;
import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
import com.wingmark.backend.service.INaturalistService;
import com.wingmark.backend.service.SpeciesService;
import com.wingmark.backend.service.XenoCantoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    private final INaturalistService iNaturalistService;

    /** Returns a page of species in the guide, optionally filtered by a common-name substring. Public. */
    @Operation(summary = "Get all species", description = "Returns a page of species in the guide, optionally filtered by a common-name substring (?search=), " +
            "paginated via the standard ?page=/?size=/?sort= params. Public endpoint.")
    @GetMapping
    public ResponseEntity<Page<SpeciesResponseDto>> getAll(@RequestParam(required = false) String search,
                                                            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(speciesService.getAll(search, pageable));
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
            "Typically the URL comes from reviewing results of GET /{id}/photo-candidates first.")
    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpeciesImageResponseDto> createImage(@PathVariable UUID id,
                                                           @Valid @RequestBody CreateSpeciesImageRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(speciesService.addImage(id, request));
    }

    /**
     * Admin-only photo curation tool. It does NOT save anything by itself - it searches
     * iNaturalist's crowd-sourced observation database for candidate photos of this
     * species matching the requested life stage (and, optionally, sex), so an admin can
     * review them and then pick the best one to actually save into the guide via
     * POST /{id}/images. This exists because species reference photos are curated once
     * and reused forever, not re-fetched live on every guide page view (see the
     * INaturalistService javadoc for why: crowd-sourced results vary in quality, so
     * picking one deliberately beats grabbing whatever comes back first).
     */
    @Operation(
            summary = "Get species photo candidates",
            description = "Admin-only curation tool - does not save anything. Searches iNaturalist for candidate " +
                    "reference photos of this species matching the given lifeStage (and optionally gender), " +
                    "returning attribution/license info for each so one can be reviewed and saved via " +
                    "POST /{id}/images. Not called on every guide page view - species photos are curated " +
                    "once, not live-fetched per request."
    )
    @GetMapping("/{id}/photo-candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PhotoCandidateDto>> getPhotoCandidates(@PathVariable UUID id,
                                                                    @RequestParam LifeStageImage lifeStage,
                                                                    @RequestParam(defaultValue = "NOT_APPLICABLE") ImageGender gender) {
        SpeciesResponseDto species = speciesService.getById(id);
        return ResponseEntity.ok(iNaturalistService.findPhotoCandidates(species.scientificName(), lifeStage, gender));
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
