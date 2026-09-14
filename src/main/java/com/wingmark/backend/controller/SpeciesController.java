package com.wingmark.backend.controller;

import com.wingmark.backend.dto.species.CreateSpeciesImageRequest;
import com.wingmark.backend.dto.species.CreateSpeciesRequest;
import com.wingmark.backend.dto.species.SpeciesImageResponse;
import com.wingmark.backend.dto.species.SpeciesRecordingResponse;
import com.wingmark.backend.dto.species.SpeciesResponse;
import com.wingmark.backend.dto.species.UpdateSpeciesRequest;
import com.wingmark.backend.service.SpeciesService;
import com.wingmark.backend.service.XenoCantoService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/species")
@RequiredArgsConstructor
public class SpeciesController {

    private final SpeciesService speciesService;
    private final XenoCantoService xenoCantoService;

    @GetMapping
    public ResponseEntity<List<SpeciesResponse>> list(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(speciesService.list(search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpeciesResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(speciesService.get(id));
    }

    @GetMapping("/{id}/sound")
    public ResponseEntity<List<SpeciesRecordingResponse>> sound(@PathVariable UUID id) {
        SpeciesResponse species = speciesService.get(id);
        return ResponseEntity.ok(xenoCantoService.findRecordings(species.scientificName()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpeciesResponse> create(@Valid @RequestBody CreateSpeciesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(speciesService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpeciesResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateSpeciesRequest request) {
        return ResponseEntity.ok(speciesService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        speciesService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpeciesImageResponse> addImage(@PathVariable UUID id,
                                                           @Valid @RequestBody CreateSpeciesImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(speciesService.addImage(id, request));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        speciesService.deleteImage(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
