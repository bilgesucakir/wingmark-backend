package com.wingmark.backend.controller;

import com.wingmark.backend.service.BirdLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin-only bird log management: delete any user's log. */
@Tag(name = "Admin - Bird logs", description = "Admin-only bird log management")
@RestController
@RequestMapping("/api/admin/bird-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBirdLogController {

    private final BirdLogService birdLogService;

    /** Admin-only: deletes any user's bird log; the response has no body. */
    @Operation(summary = "Delete a bird log", description = "Admin-only. Permanently deletes any user's bird log, removes its uploaded photo " +
            "when nothing else uses it and re-evaluates the owner's badge progress. Unlike DELETE /api/bird-logs/{id}, the log need not belong to the caller.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        birdLogService.deleteAsAdmin(id);
        return ResponseEntity.noContent().build();
    }
}
