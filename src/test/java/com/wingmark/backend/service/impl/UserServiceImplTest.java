package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.admin.AdminUpdateUserRequestDto;
import com.wingmark.backend.dto.user.SettingsResponseDto;
import com.wingmark.backend.dto.user.UpdateProfileRequestDto;
import com.wingmark.backend.dto.user.UpdateSettingsRequestDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserSettings;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.enums.UnitPreference;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private SpeciesRepository speciesRepository;

    private UserServiceImpl userService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, userSettingsRepository, speciesRepository);
    }

    @Test
    void getProfileThrowsWhenUserMissing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProfileReturnsMappedFields() {
        User user = User.builder()
                .id(userId).email("user@example.com").username("someuser")
                .role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponseDto response = userService.getProfile(userId);

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.username()).isEqualTo("someuser");
    }

    @Test
    void updateProfileRejectsUnknownFavoriteSpecies() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.existsById(speciesId)).thenReturn(false);

        UpdateProfileRequestDto request = new UpdateProfileRequestDto("First", "Last", null, speciesId);

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfileSavesValidChanges() {
        UUID speciesId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(speciesRepository.existsById(speciesId)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequestDto request = new UpdateProfileRequestDto("First", "Last", "avatar-1", speciesId);

        UserProfileResponseDto response = userService.updateProfile(userId, request);

        assertThat(response.firstName()).isEqualTo("First");
        assertThat(response.favoriteSpeciesId()).isEqualTo(speciesId);
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

        List<UserProfileResponseDto> response = userService.getAllUsers();

        assertThat(response).hasSize(2);
        assertThat(response).extracting(UserProfileResponseDto::email)
                .containsExactlyInAnyOrder("a@example.com", "b@example.com");
    }

    @Test
    void adminUpdateUserOverwritesNameRoleAndVerifiedFlag() {
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).emailVerified(false).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUpdateUserRequestDto request = new AdminUpdateUserRequestDto("First", "Last", Role.ADMIN, true);

        UserProfileResponseDto response = userService.adminUpdateUser(userId, request);

        assertThat(response.firstName()).isEqualTo("First");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.emailVerified()).isTrue();
    }

    @Test
    void adminUpdateUserThrowsWhenUserMissing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AdminUpdateUserRequestDto request = new AdminUpdateUserRequestDto("First", "Last", Role.ADMIN, true);

        assertThatThrownBy(() -> userService.adminUpdateUser(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUserThrowsWhenMissing() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(userId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUserRemovesExistingUser() {
        when(userRepository.existsById(userId)).thenReturn(true);

        userService.deleteUser(userId);

        org.mockito.Mockito.verify(userRepository).deleteById(userId);
    }
}
