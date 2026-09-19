package com.wingmark.backend.controller;

import com.wingmark.backend.dto.birdlog.BirdLogResponseDto;
import com.wingmark.backend.dto.birdlog.CreateBirdLogRequestDto;
import com.wingmark.backend.dto.birdlog.UpdateBirdLogRequestDto;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.BirdLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * Bird sighting logs. Regular users can only ever reach their own logs - either
 * implicitly (create/update/delete/getById/getByLocation, scoped via the JWT principal)
 * or explicitly by their own id (getByUserId, ownership-checked like
 * UserController/BadgeController). The one true "all logs, everyone's" endpoint
 * (getAll) is admin-only, since there's no cross-user viewing feature for regular
 * users.
 */
@Tag(name = "Bird Logs", description = "Bird sighting logs (photo, species, location, notes)")
@RestController
@RequestMapping("/api/bird-logs")
@RequiredArgsConstructor
public class BirdLogController {

    private final BirdLogService birdLogService;

    /** Admin-only: returns every log across every user, most recently observed first. */
    @Operation(summary = "Get all bird logs", description = "Admin-only. Returns every log across every user, most recently observed first.")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BirdLogResponseDto>> getAll(Locale locale) {
        return ResponseEntity.ok(birdLogService.getAll(locale));
    }

    /** Returns all of the given user's own logs, most recently observed first. userId must match the authenticated caller, unless the caller is an admin. */
    @Operation(summary = "Get bird logs by user", description = "Returns all of this user's own logs, most recently observed first. userId must match the authenticated caller, unless the caller is an admin (used by the admin panel's user detail view).")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BirdLogResponseDto>> getByUserId(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable UUID userId,
                                                                 Locale locale) {
        if (!principal.getId().equals(userId) && !principal.isAdmin()) {
            throw ResourceNotFoundException.of("User", userId);
        }
        return ResponseEntity.ok(birdLogService.getByUserId(userId, locale));
    }

    /** Returns the caller's own logs whose coordinates fall within the given lat/lng box, for the map view. */
    @Operation(summary = "Get bird logs by location", description = "Returns the caller's own logs whose coordinates fall within the given lat/lng bounding box, for the map view.")
    @GetMapping("/location")
    public ResponseEntity<List<BirdLogResponseDto>> getByLocation(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @RequestParam double minLat,
                                                                    @RequestParam double maxLat,
                                                                    @RequestParam double minLng,
                                                                    @RequestParam double maxLng,
                                                                    Locale locale) {
        return ResponseEntity.ok(birdLogService.getByLocation(principal.getId(), minLat, maxLat, minLng, maxLng, locale));
    }

    /** Returns one of the caller's own logs by id. */
    @Operation(summary = "Get bird log by id", description = "Returns one of the caller's own logs by id. 404 if it doesn't exist or belongs to someone else.")
    @GetMapping("/{id}")
    public ResponseEntity<BirdLogResponseDto> getById(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, Locale locale) {
        return ResponseEntity.ok(birdLogService.getById(principal.getId(), id, locale));
    }

    /** Creates a new bird sighting log for the caller and re-evaluates their badge progress. */
    @Operation(summary = "Create a bird log", description = "Creates a new bird sighting log for the caller and re-evaluates their badge progress.")
    @PostMapping
    public ResponseEntity<BirdLogResponseDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody CreateBirdLogRequestDto request,
                                                    Locale locale) {
        return ResponseEntity.status(HttpStatus.CREATED).body(birdLogService.create(principal.getId(), request, locale));
    }

    /** Updates one of the caller's own bird logs and re-evaluates their badge progress. */
    @Operation(summary = "Update a bird log", description = "Updates one of the caller's own bird logs and re-evaluates their badge progress.")
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
