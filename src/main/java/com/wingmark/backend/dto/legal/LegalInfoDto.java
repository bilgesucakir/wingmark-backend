package com.wingmark.backend.dto.legal;

/** Current legal document versions and links. A null version means that document isn't published yet. */
public record LegalInfoDto(
        String termsVersion,
        String termsUrl,
        String privacyVersion,
        String privacyUrl,
        int minimumAge
) {
}
