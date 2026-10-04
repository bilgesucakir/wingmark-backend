package com.wingmark.backend.controller;

import com.wingmark.backend.dto.avatar.AvatarResponseDto;
import com.wingmark.backend.service.AvatarCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Preset profile-picture catalog. */
@Tag(name = "Avatars", description = "Preset profile-picture catalog")
@RestController
@RequestMapping("/api/avatars")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarCatalog avatarCatalog;

    /** Lists the preset avatar keys a profile's profilePicture may be set to. Public. */
    @Operation(summary = "List preset avatars", description = "Public. Returns the preset avatar keys a profile's profilePicture may be set to. " +
            "The images ship inside the app; only the keys are served. profilePicture may alternatively be a /uploads/... URL from POST /api/uploads/photo, or null.")
    @GetMapping
    public ResponseEntity<List<AvatarResponseDto>> list() {
        return ResponseEntity.ok(avatarCatalog.keys().stream().map(AvatarResponseDto::new).toList());
    }
}
