package com.wingmark.backend.controller;

import com.wingmark.backend.dto.birdlog.BirdLogResponse;
import com.wingmark.backend.dto.birdlog.CreateBirdLogRequest;
import com.wingmark.backend.dto.birdlog.UpdateBirdLogRequest;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.BirdLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class BirdLogController {

    private final BirdLogService birdLogService;

    @GetMapping
    public ResponseEntity<List<BirdLogResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(birdLogService.listForUser(principal.getId()));
    }

    @GetMapping("/map")
    public ResponseEntity<List<BirdLogResponse>> map(@AuthenticationPrincipal UserPrincipal principal,
                                                       @RequestParam double minLat,
                                                       @RequestParam double maxLat,
                                                       @RequestParam double minLng,
                                                       @RequestParam double maxLng) {
        return ResponseEntity.ok(birdLogService.findWithinBounds(principal.getId(), minLat, maxLat, minLng, maxLng));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BirdLogResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(birdLogService.get(principal.getId(), id));
    }

    @PostMapping
    public ResponseEntity<BirdLogResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody CreateBirdLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(birdLogService.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BirdLogResponse> update(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody UpdateBirdLogRequest request) {
        return ResponseEntity.ok(birdLogService.update(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        birdLogService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
