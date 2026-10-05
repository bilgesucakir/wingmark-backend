package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.LegalProperties;
import com.wingmark.backend.dto.legal.ConsentResponseDto;
import com.wingmark.backend.dto.legal.LegalInfoDto;
import com.wingmark.backend.entity.Consent;
import com.wingmark.backend.enums.ConsentType;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.repository.ConsentRepository;
import com.wingmark.backend.service.ConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Records and checks acceptance of legal documents. */
@Service
@RequiredArgsConstructor
public class ConsentServiceImpl implements ConsentService {

    private final LegalProperties legalProperties;
    private final ConsentRepository consentRepository;

    @Override
    public LegalInfoDto legalInfo() {
        return new LegalInfoDto(
                blankToNull(legalProperties.termsVersion()),
                blankToNull(legalProperties.termsUrl()),
                blankToNull(legalProperties.privacyVersion()),
                blankToNull(legalProperties.privacyUrl()),
                legalProperties.minimumAge());
    }

    @Override
    public void requireAcceptedAtSignup(String acceptedTermsVersion, String acceptedPrivacyVersion, boolean ageConfirmed) {
        if (!ageConfirmed) {
            throw new BadRequestException(ErrorCode.AGE_NOT_CONFIRMED,
                    "You must confirm that you are at least " + legalProperties.minimumAge() + " years old");
        }
        // Never inferred from a missing field: a published document must be accepted explicitly, by exact version.
        if (legalProperties.termsPublished() && !legalProperties.termsVersion().equals(acceptedTermsVersion)) {
            throw new BadRequestException(ErrorCode.TERMS_NOT_ACCEPTED,
                    "You must accept the current Terms of Service (version " + legalProperties.termsVersion() + ")");
        }
        if (legalProperties.privacyPublished() && !legalProperties.privacyVersion().equals(acceptedPrivacyVersion)) {
            throw new BadRequestException(ErrorCode.PRIVACY_NOT_ACCEPTED,
                    "You must accept the current Privacy Policy (version " + legalProperties.privacyVersion() + ")");
        }
    }

    @Override
    public void recordSignupConsents(UUID userId) {
        for (ConsentType type : ConsentType.values()) {
            String version = currentVersion(type);
            if (version != null) {
                save(userId, type, version);
            }
        }
    }

    @Override
    public List<ConsentType> pendingConsents(UUID userId) {
        List<ConsentType> pending = new ArrayList<>();
        for (ConsentType type : ConsentType.values()) {
            String version = currentVersion(type);
            if (version != null && !consentRepository.existsByUserIdAndTypeAndVersion(userId, type, version)) {
                pending.add(type);
            }
        }
        return pending;
    }

    @Override
    public List<ConsentType> accept(UUID userId, ConsentType type, String version) {
        String current = currentVersion(type);
        if (current == null || !current.equals(version)) {
            throw new BadRequestException(ErrorCode.CONSENT_VERSION_MISMATCH, current == null
                    ? type + " has no published version to accept"
                    : "The current " + type + " version is " + current + ", not " + version);
        }
        if (!consentRepository.existsByUserIdAndTypeAndVersion(userId, type, version)) {
            save(userId, type, version);
        }
        return pendingConsents(userId);
    }

    @Override
    public List<ConsentResponseDto> history(UUID userId) {
        return consentRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(c -> new ConsentResponseDto(c.getType(), c.getVersion(), c.getCreatedAt()))
                .toList();
    }

    private void save(UUID userId, ConsentType type, String version) {
        consentRepository.save(Consent.builder().userId(userId).type(type).version(version).build());
    }

    private String currentVersion(ConsentType type) {
        return switch (type) {
            case TERMS -> blankToNull(legalProperties.termsVersion());
            case PRIVACY -> blankToNull(legalProperties.privacyVersion());
            case AGE -> String.valueOf(legalProperties.minimumAge());
        };
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
