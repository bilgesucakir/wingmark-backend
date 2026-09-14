package com.wingmark.backend.controller;

import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** File uploads (currently just bird-log photos). */
@Tag(name = "Uploads", description = "File uploads (currently just bird-log photos)")
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final FileStorageService fileStorageService;

    /**
     * Stores an uploaded photo and returns its URL, for use as a bird log's photoUrl.
     * Images are re-encoded server-side to strip EXIF metadata (including GPS tags),
     * since the sighting's location is already captured explicitly on the log.
     */
    @Operation(summary = "Upload a photo", description = "Stores an uploaded photo (re-encoded to strip EXIF metadata) and returns its URL for use as a bird log's photoUrl.")
    @PostMapping("/photo")
    public ResponseEntity<Map<String, String>> uploadPhoto(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestParam("file") MultipartFile file) {
        String url = fileStorageService.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }
}
