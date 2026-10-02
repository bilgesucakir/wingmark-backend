package com.wingmark.backend.service;

import com.wingmark.backend.dto.legal.ConsentResponseDto;
import com.wingmark.backend.dto.legal.LegalInfoDto;
import com.wingmark.backend.enums.ConsentType;

import java.util.List;
import java.util.UUID;

/** Terms of Service / Privacy Policy acceptance, recorded per user and per version. */
public interface ConsentService {

    /** Current published versions and links (nulls for unpublished documents). */
    LegalInfoDto legalInfo();

    /** Rejects a signup that didn't accept every currently published document's exact version. */
    void requireAcceptedAtSignup(String acceptedTermsVersion, String acceptedPrivacyVersion);

    /** Records acceptance of every currently published document for a just-registered user. */
    void recordSignupConsents(UUID userId);

    /** Published documents whose current version this user hasn't accepted yet. */
    List<ConsentType> pendingConsents(UUID userId);

    /** Records acceptance of the current version of one document; returns what's still pending. */
    List<ConsentType> accept(UUID userId, ConsentType type, String version);

    /** Every acceptance this user has recorded, oldest first. */
    List<ConsentResponseDto> history(UUID userId);
}
