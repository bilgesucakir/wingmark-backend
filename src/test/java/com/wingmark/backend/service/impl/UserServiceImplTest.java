package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.admin.AdminUpdateUserRequestDto;
import com.wingmark.backend.dto.user.SettingsResponseDto;
import com.wingmark.backend.dto.user.UpdateProfileRequestDto;
import com.wingmark.backend.dto.user.UpdateSettingsRequestDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserSettings;
import com.wingmark.backend.enums.DeletionInitiator;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.enums.UnitPreference;
import com.wingmark.backend.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.wingmark.backend.service.AccountDeletionService;
import com.wingmark.backend.service.AvatarCatalog;
import com.wingmark.backend.service.BadgeService;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.exception.UnauthorizedActionException;
import com.wingmark.backend.exception.InvalidReferenceException;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.ConflictException;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private AccountDeletionService accountDeletionService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private BadgeService badgeService;
    @Mock
    private UploadedFileCleaner uploadedFileCleaner;

    private UserServiceImpl userService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, userSettingsRepository, speciesRepository, accountDeletionService, passwordEncoder,
                new AvatarCatalog(), fileStorageService, badgeService, uploadedFileCleaner);
    }

    @Test
    void getProfileThrowsWhenUserMissing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId, Locale.ENGLISH)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProfileReturnsMappedFields() {
        User user = User.builder()
                .id(userId).email("user@example.com").username("someuser")
                .role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponseDto response = userService.getProfile(userId, Locale.ENGLISH);

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.username()).isEqualTo("someuser");
    }

    @Test
    void markingTheWalkthroughSeenStoresTheTimeOnce() {
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.markWalkthroughSeen(userId);
        java.time.Instant first = user.getWalkthroughSeenAt();
        userService.markWalkthroughSeen(userId);

        assertThat(first).isNotNull();
        assertThat(user.getWalkthroughSeenAt()).isEqualTo(first);
        verify(userRepository, org.mockito.Mockito.times(1)).save(user);
    }

    @Test
    void markingTheWalkthroughSeenForAMissingUserIsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.markWalkthroughSeen(userId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theProfileCarriesWalkthroughSeenAtAndItIsNullForANewAccount() {
        java.time.Instant seen = java.time.Instant.parse("2026-10-10T10:00:00Z");
        User seenUser = User.builder().id(userId).email("a@example.com").role(Role.USER).walkthroughSeenAt(seen).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(seenUser));
        assertThat(userService.getProfile(userId, Locale.ENGLISH).walkthroughSeenAt()).isEqualTo(seen);

        UUID otherId = UUID.randomUUID();
        when(userRepository.findById(otherId)).thenReturn(Optional.of(User.builder().id(otherId).email("b@example.com").role(Role.USER).build()));
        assertThat(userService.getProfile(otherId, Locale.ENGLISH).walkthroughSeenAt()).isNull();
    }

    @Test
    void getProfileResolvesFavoriteSpeciesNameForLocale() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER)
                .favoriteSpeciesId(speciesId).build();
        Species species = Species.builder().id(speciesId)
                .commonName(Map.of("en", "Great Tit", "tr", "Büyük Baştankara")).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.findById(speciesId)).thenReturn(Optional.of(species));

        UserProfileResponseDto response = userService.getProfile(userId, Locale.forLanguageTag("tr"));

        assertThat(response.favoriteSpeciesName()).isEqualTo("Büyük Baştankara");
    }

    @Test
    void updateProfileRejectsUnknownFavoriteSpecies() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.existsById(speciesId)).thenReturn(false);

        UpdateProfileRequestDto request = new UpdateProfileRequestDto("First", "Last", null, speciesId);

        assertThatThrownBy(() -> userService.updateProfile(userId, request, Locale.ENGLISH))
                .isInstanceOf(InvalidReferenceException.class);
    }

    @Test
    void updateProfileSavesValidChanges() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.existsById(speciesId)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequestDto request = new UpdateProfileRequestDto("First", "Last", "avatar-1", speciesId);

        UserProfileResponseDto response = userService.updateProfile(userId, request, Locale.ENGLISH);

        assertThat(response.firstName()).isEqualTo("First");
        assertThat(response.favoriteSpeciesId()).isEqualTo(speciesId);
    }

    @Test
    void changingTheFavoriteSpeciesRecomputesBadgeProgress() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.existsById(speciesId)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(userId, new UpdateProfileRequestDto("A", "B", null, speciesId), Locale.ENGLISH);

        verify(badgeService).evaluateForUser(userId);
    }

    @Test
    void keepingTheSameFavoriteSpeciesDoesNotRecomputeBadges() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).favoriteSpeciesId(speciesId).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.existsById(speciesId)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(userId, new UpdateProfileRequestDto("A", "B", null, speciesId), Locale.ENGLISH);

        verify(badgeService, never()).evaluateForUser(any());
    }

    @Test
    void anAdminClearingTheFavoriteSpeciesRecomputesBadgeProgress() {
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER)
                .favoriteSpeciesId(UUID.randomUUID()).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.adminUpdateUser(userId, new AdminUpdateUserRequestDto("A", "B", Role.USER, true, null, null), Locale.ENGLISH);

        verify(badgeService).evaluateForUser(userId);
    }

    @Test
    void getSettingsThrowsWhenMissing() {
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getSettings(userId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSettingsPersistsNewPreference() {
        UserSettings settings = UserSettings.builder().userId(userId).unitPreference(UnitPreference.METRIC).build();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings));

        SettingsResponseDto response = userService.updateSettings(userId,
                new UpdateSettingsRequestDto(UnitPreference.IMPERIAL, "en-US"));

        assertThat(response.unitPreference()).isEqualTo(UnitPreference.IMPERIAL);
        assertThat(response.locale()).isEqualTo("en-US");
    }

    @Test
    void getAllUsersReturnsEveryUserMapped() {
        User a = User.builder().id(UUID.randomUUID()).email("a@example.com").role(Role.USER).build();
        User b = User.builder().id(UUID.randomUUID()).email("b@example.com").role(Role.ADMIN).build();
        when(userRepository.findAll()).thenReturn(List.of(a, b));

        List<UserProfileResponseDto> response = userService.getAllUsers(Locale.ENGLISH);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(UserProfileResponseDto::email)
                .containsExactlyInAnyOrder("a@example.com", "b@example.com");
    }

    @Test
    void adminUpdateUserOverwritesNameRoleAndVerifiedFlag() {
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).emailVerified(false).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUpdateUserRequestDto request = new AdminUpdateUserRequestDto("First", "Last", Role.ADMIN, true, null, null);

        UserProfileResponseDto response = userService.adminUpdateUser(userId, request, Locale.ENGLISH);

        assertThat(response.firstName()).isEqualTo("First");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.emailVerified()).isTrue();
    }

    @Test
    void adminUpdateUserSetsFavoriteSpeciesWhenValid() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.existsById(speciesId)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUpdateUserRequestDto request = new AdminUpdateUserRequestDto("First", "Last", Role.USER, true, speciesId, null);

        UserProfileResponseDto response = userService.adminUpdateUser(userId, request, Locale.ENGLISH);

        assertThat(response.favoriteSpeciesId()).isEqualTo(speciesId);
    }

    @Test
    void adminUpdateUserRejectsUnknownFavoriteSpecies() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.existsById(speciesId)).thenReturn(false);

        AdminUpdateUserRequestDto request = new AdminUpdateUserRequestDto("First", "Last", Role.USER, true, speciesId, null);

        assertThatThrownBy(() -> userService.adminUpdateUser(userId, request, Locale.ENGLISH))
                .isInstanceOf(InvalidReferenceException.class);
    }

    @Test
    void adminUpdateUserThrowsWhenUserMissing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AdminUpdateUserRequestDto request = new AdminUpdateUserRequestDto("First", "Last", Role.ADMIN, true, null, null);

        assertThatThrownBy(() -> userService.adminUpdateUser(userId, request, Locale.ENGLISH))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUserDelegatesToTheFullAccountDeletion() {
        userService.deleteUser(userId);

        verify(accountDeletionService).deleteAccount(userId, DeletionInitiator.ADMIN);
    }

    @Test
    void deleteOwnAccountRejectsWrongPassword() {
        User user = User.builder().id(userId).email("user@example.com").passwordHash("hash").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteOwnAccount(userId, "wrong"))
                .isInstanceOf(UnauthorizedActionException.class);
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void deleteOwnAccountRefusesTheLastAdmin() {
        User user = User.builder().id(userId).email("admin@example.com").passwordHash("hash").role(Role.ADMIN).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteOwnAccount(userId, "right"))
                .isInstanceOf(ConflictException.class);
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void deleteOwnAccountDeletesEverythingWhenPasswordMatches() {
        User user = User.builder().id(userId).email("user@example.com").passwordHash("hash").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);

        userService.deleteOwnAccount(userId, "right");

        verify(accountDeletionService).deleteAccount(userId, DeletionInitiator.SELF);
    }

    @Test
    void updateProfileAcceptsPresetAvatarNullAndAnExistingUpload() {
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.storedFilename("/uploads/abc.jpg")).thenReturn(Optional.of("abc.jpg"));
        when(fileStorageService.exists("abc.jpg")).thenReturn(true);

        userService.updateProfile(userId, new UpdateProfileRequestDto(null, null, "avatar-3", null), Locale.ENGLISH);
        assertThat(user.getProfilePicture()).isEqualTo("avatar-3");

        userService.updateProfile(userId, new UpdateProfileRequestDto(null, null, "/uploads/abc.jpg", null), Locale.ENGLISH);
        assertThat(user.getProfilePicture()).isEqualTo("/uploads/abc.jpg");

        userService.updateProfile(userId, new UpdateProfileRequestDto(null, null, "  ", null), Locale.ENGLISH);
        assertThat(user.getProfilePicture()).isNull();
    }

    @Test
    void updateProfileRejectsUnknownKeysExternalUrlsAndMissingUploads() {
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.storedFilename(any())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            int i = url.lastIndexOf("/uploads/");
            return i < 0 ? Optional.empty() : Optional.of(url.substring(i + "/uploads/".length()));
        });
        when(fileStorageService.exists("gone.jpg")).thenReturn(false);

        for (String bad : new String[]{"avatar-999", "https://evil.example.com/x.png", "https://evil.example.com/uploads/abc.jpg", "/uploads/gone.jpg"}) {
            assertThatThrownBy(() -> userService.updateProfile(userId, new UpdateProfileRequestDto(null, null, bad, null), Locale.ENGLISH))
                    .as(bad)
                    .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_PROFILE_PICTURE));
        }
    }

    @Test
    void updateProfileKeepsALegacyPictureThatIsResentUnchanged() {
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).profilePicture("legacy-value").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(userId, new UpdateProfileRequestDto("New", null, "legacy-value", null), Locale.ENGLISH);

        assertThat(user.getProfilePicture()).isEqualTo("legacy-value");
        assertThat(user.getFirstName()).isEqualTo("New");
    }

    private User userWithPicture(String picture) {
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).profilePicture(picture).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        return user;
    }

    @Test
    void replacingACustomProfilePictureDeletesTheOldFile() {
        userWithPicture("/uploads/old.jpg");
        when(fileStorageService.storedFilename("/uploads/new.jpg")).thenReturn(Optional.of("new.jpg"));
        when(fileStorageService.exists("new.jpg")).thenReturn(true);

        userService.updateProfile(userId, new UpdateProfileRequestDto("A", "B", "/uploads/new.jpg", null), Locale.ENGLISH);

        verify(uploadedFileCleaner).deleteIfUnreferenced("/uploads/old.jpg");
    }

    @Test
    void switchingToAPresetAvatarOrRemovingThePictureDeletesTheOldFile() {
        userWithPicture("/uploads/old.jpg");

        userService.updateProfile(userId, new UpdateProfileRequestDto("A", "B", "avatar-1", null), Locale.ENGLISH);
        verify(uploadedFileCleaner).deleteIfUnreferenced("/uploads/old.jpg");
    }

    @Test
    void keepingTheSamePictureOrHavingNoneDeletesNothing() {
        userWithPicture("/uploads/same.jpg");
        userService.updateProfile(userId, new UpdateProfileRequestDto("A", "B", "/uploads/same.jpg", null), Locale.ENGLISH);

        userWithPicture(null);
        userService.updateProfile(userId, new UpdateProfileRequestDto("A", "B", "avatar-1", null), Locale.ENGLISH);

        verify(uploadedFileCleaner, org.mockito.Mockito.never()).deleteIfUnreferenced(any());
    }

    @Test
    void anAdminChangingTheProfilePictureAlsoDeletesTheOldFile() {
        userWithPicture("/uploads/old.jpg");

        userService.adminUpdateUser(userId, new AdminUpdateUserRequestDto("A", "B", Role.USER, true, null, null), Locale.ENGLISH);

        verify(uploadedFileCleaner).deleteIfUnreferenced("/uploads/old.jpg");
    }
}
