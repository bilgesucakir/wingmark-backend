package com.wingmark.backend.controller;

import com.wingmark.backend.dto.birdlog.BirdLogLocationResultDto;
import com.wingmark.backend.dto.birdlog.BirdLogResponseDto;
import com.wingmark.backend.dto.birdlog.CreateBirdLogRequestDto;
import com.wingmark.backend.dto.birdlog.UpdateBirdLogRequestDto;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.BirdLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bird sighting logs. Users reach only their own logs; listing everyone's logs is admin-only.
 * <p>
 * The list endpoints take optional {@code hasSpecies}, {@code gender} and {@code lifeStage} filters and a
 * {@code sortDirection} (default {@code DESC}, newest first).
 */
@Tag(name = "Bird Logs", description = "Bird sighting logs (photo, species, location, notes)")
@RestController
@RequestMapping("/api/bird-logs")
@RequiredArgsConstructor
public class BirdLogController {

    public static final String TRUNCATED_HEADER = "X-Result-Truncated";

    private final BirdLogService birdLogService;

    /** Admin-only: returns every user's logs, filtered and sorted by {@code observedAt}. */
    @Operation(summary = "Get all bird logs", description = "Admin-only. Returns every log across every user. " +
            "Optional filters: ?hasSpecies=true|false (species selected or not), ?gender=MALE|FEMALE|UNKNOWN, " +
            "?lifeStage=ADULT|BABY|UNKNOWN - each omitted means no filter on it. " +
            "?sortDirection=ASC|DESC sorts by observedAt (default DESC, i.e. most recently observed first).")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BirdLogResponseDto>> getAll(@RequestParam(required = false) Boolean hasSpecies,
                                                            @RequestParam(required = false) Gender gender,
                                                            @RequestParam(required = false) LifeStage lifeStage,
                                                            @Parameter(description = "Sort direction by observedAt; defaults to DESC (most recent first).")
                                                            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction sortDirection,
                                                            Locale locale) {
        return ResponseEntity.ok(birdLogService.getAll(hasSpecies, gender, lifeStage, sortDirection, locale));
    }

    /** Returns the user's logs, filtered and sorted by {@code observedAt}. The userId must be the caller's unless admin. */
    @Operation(summary = "Get bird logs by user", description = "Returns all of this user's own logs. userId must match the authenticated caller, " +
            "unless the caller is an admin (used by the admin panel's user detail view). " +
            "Optional filters: ?hasSpecies=true|false (species selected or not), ?gender=MALE|FEMALE|UNKNOWN, " +
            "?lifeStage=ADULT|BABY|UNKNOWN - each omitted means no filter on it. " +
            "?sortDirection=ASC|DESC sorts by observedAt (default DESC, i.e. most recently observed first).")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BirdLogResponseDto>> getByUserId(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable UUID userId,
                                                                 @RequestParam(required = false) Boolean hasSpecies,
                                                                 @RequestParam(required = false) Gender gender,
                                                                 @RequestParam(required = false) LifeStage lifeStage,
                                                                 @Parameter(description = "Sort direction by observedAt; defaults to DESC (most recent first).")
                                                                 @RequestParam(required = false, defaultValue = "DESC") Sort.Direction sortDirection,
                                                                 Locale locale) {
        if (!principal.getId().equals(userId) && !principal.isAdmin()) {
            throw ResourceNotFoundException.of("User", userId);
        }
        return ResponseEntity.ok(birdLogService.getByUserId(userId, hasSpecies, gender, lifeStage, sortDirection, locale));
    }

    /** Returns the caller's logs in the visible map region, newest first, up to {@code limit}; {@code X-Result-Truncated} says if more matched. */
    @Operation(summary = "Get bird logs by location", description = "Returns the caller's own logs inside a lat/lng box (the visible map region), most recently observed first. " +
            "minLng > maxLng means the box crosses the antimeridian (e.g. minLng=170&maxLng=-170). Latitudes must be in [-90, 90], longitudes in [-180, 180], " +
            "and minLat <= maxLat, otherwise 400 INVALID_BOUNDS. Optional filters as on /user/{userId}: ?hasSpecies, ?gender, ?lifeStage. " +
            "?limit (1-1000, default 500) caps the result; header X-Result-Truncated: true means more logs matched than were returned (zoom in).")
    @GetMapping("/location")
    public ResponseEntity<List<BirdLogResponseDto>> getByLocation(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @RequestParam double minLat,
                                                                    @RequestParam double maxLat,
                                                                    @RequestParam double minLng,
                                                                    @RequestParam double maxLng,
                                                                    @RequestParam(required = false) Boolean hasSpecies,
                                                                    @RequestParam(required = false) Gender gender,
                                                                    @RequestParam(required = false) LifeStage lifeStage,
                                                                    @RequestParam(defaultValue = "500") int limit,
                                                                    Locale locale) {
        BirdLogLocationResultDto result = birdLogService.getByLocation(principal.getId(), minLat, maxLat, minLng, maxLng,
                hasSpecies, gender, lifeStage, limit, locale);
        return ResponseEntity.ok()
                .header(TRUNCATED_HEADER, String.valueOf(result.truncated()))
                .body(result.logs());
    }

    /** Returns one of the caller's own logs by id. */
    @Operation(summary = "Get bird log by id", description = "Returns one of the caller's own logs by id. 404 if it doesn't exist or belongs to someone else.")
    @GetMapping("/{id}")
    public ResponseEntity<BirdLogResponseDto> getById(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, Locale locale) {
        return ResponseEntity.ok(birdLogService.getById(principal.getId(), id, locale));
    }

    /** Creates a new bird sighting log for the caller and re-evaluates their badge progress. */
    @Operation(summary = "Create a bird log", description = "Creates a new bird sighting log for the caller and re-evaluates their badge progress. " +
            "observedAt (ISO-8601, e.g. 2026-09-28T07:30:00Z) is optional and defaults to the upload time; it cannot be in the future.")
    @PostMapping
    public ResponseEntity<BirdLogResponseDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody CreateBirdLogRequestDto request,
                                                    Locale locale) {
        return ResponseEntity.status(HttpStatus.CREATED).body(birdLogService.create(principal.getId(), request, locale));
    }

    /** Updates one of the caller's own bird logs and re-evaluates their badge progress. */
    @Operation(summary = "Update a bird log", description = "Updates one of the caller's own bird logs and re-evaluates their badge progress. " +
            "observedAt is optional - omit it to keep the log's existing sighting time.")
    @PutMapping("/{id}")
    public ResponseEntity<BirdLogResponseDto> update(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody UpdateBirdLogRequestDto request,
                                                    Locale locale) {
        return ResponseEntity.ok(birdLogService.update(principal.getId(), id, request, locale));
    }

    /** Deletes one of the caller's own bird logs and re-evaluates their badge progress. */
    @Operation(summary = "Delete a bird log", description = "Deletes one of the caller's own bird logs and re-evaluates their badge progress.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        birdLogService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
