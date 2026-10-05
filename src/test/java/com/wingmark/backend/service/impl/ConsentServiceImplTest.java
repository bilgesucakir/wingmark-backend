package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.LegalProperties;
import com.wingmark.backend.entity.Consent;
import com.wingmark.backend.enums.ConsentType;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.repository.ConsentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentServiceImplTest {

    @Mock
    private ConsentRepository consentRepository;

    private final UUID userId = UUID.randomUUID();

    private ConsentServiceImpl service(String terms, String privacy) {
        return new ConsentServiceImpl(new LegalProperties(terms, "https://x/terms", privacy, "https://x/privacy", 13), consentRepository);
    }

    @Test
    void onlyTheAgeConfirmationIsEnforcedAndPendingWhileNoDocumentIsPublished() {
        ConsentServiceImpl service = service("", null);

        assertThatCode(() -> service.requireAcceptedAtSignup(null, null, true)).doesNotThrowAnyException();
        assertThat(service.pendingConsents(userId)).containsExactly(ConsentType.AGE);
        service.recordSignupConsents(userId);
        ArgumentCaptor<Consent> saved = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ConsentType.AGE);
        assertThat(saved.getValue().getVersion()).isEqualTo("13");
        assertThat(service.legalInfo().termsVersion()).isNull();
        assertThat(service.legalInfo().minimumAge()).isEqualTo(13);
    }

    @Test
    void publishedDocumentsMustBeAcceptedByExactVersionAtSignup() {
        ConsentServiceImpl service = service("2026-10-01", "2026-09-15");

        assertThatThrownBy(() -> service.requireAcceptedAtSignup(null, "2026-09-15", true))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TERMS_NOT_ACCEPTED));
        assertThatThrownBy(() -> service.requireAcceptedAtSignup("2025-01-01", "2026-09-15", true))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TERMS_NOT_ACCEPTED));
        assertThatThrownBy(() -> service.requireAcceptedAtSignup("2026-10-01", null, true))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PRIVACY_NOT_ACCEPTED));
        assertThatCode(() -> service.requireAcceptedAtSignup("2026-10-01", "2026-09-15", true)).doesNotThrowAnyException();
    }

    @Test
    void signupRecordsOneConsentPerPublishedDocumentAndTheAgeConfirmation() {
        service("2026-10-01", "2026-09-15").recordSignupConsents(userId);

        ArgumentCaptor<Consent> saved = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Consent::getType).containsExactly(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.AGE);
        assertThat(saved.getAllValues()).extracting(Consent::getVersion).containsExactly("2026-10-01", "2026-09-15", "13");
        assertThat(saved.getAllValues()).allMatch(c -> userId.equals(c.getUserId()));
    }

    @Test
    void anOldAcceptanceLeavesTheNewVersionPending() {
        when(consentRepository.existsByUserIdAndTypeAndVersion(userId, ConsentType.TERMS, "2026-10-01")).thenReturn(false);
        when(consentRepository.existsByUserIdAndTypeAndVersion(userId, ConsentType.PRIVACY, "2026-09-15")).thenReturn(true);
        when(consentRepository.existsByUserIdAndTypeAndVersion(userId, ConsentType.AGE, "13")).thenReturn(true);

        assertThat(service("2026-10-01", "2026-09-15").pendingConsents(userId)).containsExactly(ConsentType.TERMS);
    }

    @Test
    void acceptingRequiresTheCurrentVersion() {
        ConsentServiceImpl service = service("2026-10-01", null);

        assertThatThrownBy(() -> service.accept(userId, ConsentType.TERMS, "2025-01-01"))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONSENT_VERSION_MISMATCH));
        assertThatThrownBy(() -> service.accept(userId, ConsentType.PRIVACY, "2026-10-01"))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONSENT_VERSION_MISMATCH));
        verify(consentRepository, never()).save(any());
    }

    @Test
    void acceptingTwiceRecordsOnlyOnce() {
        when(consentRepository.existsByUserIdAndTypeAndVersion(userId, ConsentType.TERMS, "2026-10-01")).thenReturn(false, true);
        when(consentRepository.existsByUserIdAndTypeAndVersion(userId, ConsentType.AGE, "13")).thenReturn(true);
        ConsentServiceImpl service = service("2026-10-01", null);

        assertThat(service.accept(userId, ConsentType.TERMS, "2026-10-01")).isEmpty();
        verify(consentRepository, times(1)).save(any());
    }

    @Test
    void signupWithoutConfirmingTheMinimumAgeIsRejected() {
        ConsentServiceImpl service = service(null, null);

        assertThatThrownBy(() -> service.requireAcceptedAtSignup(null, null, false))
                .isInstanceOfSatisfying(BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AGE_NOT_CONFIRMED))
                .hasMessageContaining("13");
        assertThatCode(() -> service.requireAcceptedAtSignup(null, null, true)).doesNotThrowAnyException();
    }
}
