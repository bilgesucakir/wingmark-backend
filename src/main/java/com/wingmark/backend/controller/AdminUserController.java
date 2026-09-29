package com.wingmark.backend.controller;

import com.wingmark.backend.dto.admin.AdminUpdateUserRequestDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.ConflictException;
import com.wingmark.backend.exception.UnauthorizedActionException;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Admin-only account management: list, edit and delete any user. */
@Tag(name = "Admin - Users", description = "Admin-only user account management")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    /** Admin-only: returns every registered user. */
    @Operation(summary = "Get all users", description = "Admin-only. Returns every registered user account.")
    @GetMapping
    public ResponseEntity<List<UserProfileResponseDto>> getAll(Locale locale) {
        return ResponseEntity.ok(userService.getAllUsers(locale));
    }

    /**
     * Admin-only: replaces a user's name, role, email-verified flag, favorite species and
     * profile picture (full replacement - omitted fields are cleared). Role and verification
     * changes apply immediately (the JWT filter reads both from the database on every
     * request), so an admin can't demote or un-verify themselves - that would lock the
     * current panel session out on its next request.
     */
    @Operation(summary = "Update a user", description = "Admin-only. Replaces a user's name, role, email-verified flag, favorite species and profilePicture - a full replacement, so omitted fields are cleared. " +
            "Role and verification changes take effect on the user's very next request. Un-verifying a user locks them out until they verify again. " +
            "409 CANNOT_MODIFY_SELF if an admin tries to remove their own admin role or un-verify themselves. 422 INVALID_REFERENCE if favoriteSpeciesId doesn't exist. " +
            "400 INVALID_PROFILE_PICTURE unless profilePicture is null, a preset key (GET /api/avatars) or an existing /uploads/... URL.")
    @PutMapping("/{id}")
    public ResponseEntity<UserProfileResponseDto> update(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID id,
                                                           @Valid @RequestBody AdminUpdateUserRequestDto request,
                                                           Locale locale) {
        if (principal.getId().equals(id) && (request.role() != Role.ADMIN || !request.emailVerified())) {
            throw new ConflictException(ErrorCode.CANNOT_MODIFY_SELF, "You cannot remove your own admin role or email verification.");
        }
        return ResponseEntity.ok(userService.adminUpdateUser(id, request, locale));
    }

    /**
     * Admin-only: permanently deletes a user account and all of its data. An admin can't delete their own
     * account through this endpoint, to avoid a panel session locking itself out.
     */
    @Operation(summary = "Delete a user", description = "Admin-only. Permanently deletes a user account and all of its data (bird logs, badge progress, settings, " +
            "refresh tokens, uploaded photos); the user's tokens stop working immediately. Callers cannot delete their own account this way (403).")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        if (principal.getId().equals(id)) {
            throw new UnauthorizedActionException(ErrorCode.CANNOT_MODIFY_SELF, "You cannot delete your own account.");
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
