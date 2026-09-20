package com.wingmark.backend.controller;

import com.wingmark.backend.dto.admin.AdminUpdateUserRequestDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;
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

    /** Admin-only: updates a user's name, role and email-verified flag. */
    @Operation(summary = "Update a user", description = "Admin-only. Updates a user's name, role and email-verified flag.")
    @PutMapping("/{id}")
    public ResponseEntity<UserProfileResponseDto> update(@PathVariable UUID id,
                                                           @Valid @RequestBody AdminUpdateUserRequestDto request,
                                                           Locale locale) {
        return ResponseEntity.ok(userService.adminUpdateUser(id, request, locale));
    }

    /**
     * Admin-only: permanently deletes a user account. An admin can't delete their own
     * account through this endpoint, to avoid a panel session locking itself out.
     */
    @Operation(summary = "Delete a user", description = "Admin-only. Permanently deletes a user account. Callers cannot delete their own account this way.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        if (principal.getId().equals(id)) {
            throw new UnauthorizedActionException("You cannot delete your own account.");
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
