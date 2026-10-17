package com.wingmark.backend.controller;

import com.wingmark.backend.dto.appicon.AdminAppIconResponseDto;
import com.wingmark.backend.dto.appicon.CreateAppIconRequestDto;
import com.wingmark.backend.dto.appicon.UpdateAppIconRequestDto;
import com.wingmark.backend.service.AppIconService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-only management of the alternate app icon list. */
@Tag(name = "Admin - App icons", description = "Admin-only management of the alternate app icon list")
@RestController
@RequestMapping("/api/admin/app-icons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppIconController {

    private final AppIconService appIconService;

    /** Admin-only: the icon list with creation times. */
    @Operation(summary = "List app icons", description = "Admin-only. Every icon currently available to users, oldest first.")
    @GetMapping
    public ResponseEntity<List<AdminAppIconResponseDto>> list() {
        return ResponseEntity.ok(appIconService.listForAdmin());
    }

    /** Admin-only: makes an icon available to users. */
    @Operation(summary = "Add an app icon", description = "Admin-only. Makes the icon visible to users. iconId must match the icon name built into the iOS app " +
            "(lowercase letters, digits, - or _, up to 40 characters). 409 CONFLICT if the iconId is already in the list.")
    @PostMapping
    public ResponseEntity<AdminAppIconResponseDto> add(@Valid @RequestBody CreateAppIconRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appIconService.add(request));
    }

    /** Admin-only: changes an icon's display names. */
    @Operation(summary = "Edit an app icon", description = "Admin-only. Changes the English and Turkish display names. The iconId cannot be changed " +
            "(delete and add again for that). 404 if the icon is not in the list.")
    @PutMapping("/{iconId}")
    public ResponseEntity<AdminAppIconResponseDto> update(@PathVariable String iconId, @Valid @RequestBody UpdateAppIconRequestDto request) {
        return ResponseEntity.ok(appIconService.update(iconId, request));
    }

    /** Admin-only: hides an icon from users. */
    @Operation(summary = "Delete an app icon", description = "Admin-only. Removes the icon from the list so users no longer see it. 404 if it is not in the list.")
    @DeleteMapping("/{iconId}")
    public ResponseEntity<Void> delete(@PathVariable String iconId) {
        appIconService.delete(iconId);
        return ResponseEntity.noContent().build();
    }
}
