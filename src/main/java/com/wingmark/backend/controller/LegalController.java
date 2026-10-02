package com.wingmark.backend.controller;

import com.wingmark.backend.dto.legal.LegalInfoDto;
import com.wingmark.backend.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Legal", description = "Terms of Service / Privacy Policy versions")
@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
public class LegalController {

    private final ConsentService consentService;

    @Operation(summary = "Current legal documents", description = "Public. Current Terms of Service and Privacy Policy versions and URLs. " +
            "A null version means that document isn't published yet (no acceptance needed). Send the versions shown here as " +
            "acceptedTermsVersion / acceptedPrivacyVersion at signup.")
    @GetMapping
    public ResponseEntity<LegalInfoDto> get() {
        return ResponseEntity.ok(consentService.legalInfo());
    }
}
