package com.wingmark.backend.controller;

import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.FileStorageService.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Serves uploaded images from GridFS at {@code /uploads/{filename}}. Public. */
@Tag(name = "Uploads", description = "File uploads (currently just bird-log photos)")
@RestController
@RequiredArgsConstructor
public class UploadsController {

    private final FileStorageService fileStorageService;

/** Returns the stored image, or 404 if it does not exist. */
    @Operation(summary = "Get an uploaded image", description = "Public. Returns an image previously stored via POST /api/uploads/photo. 404 if it doesn't exist.")
    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<byte[]> get(@PathVariable String filename) {
        StoredFile file = fileStorageService.storedFilename("/uploads/" + filename)
                .flatMap(fileStorageService::load)
                .orElseThrow(() -> new ResourceNotFoundException("Uploaded file not found: " + filename));

        // Filenames are random UUIDs and content never changes under one, so caches may keep it forever.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
