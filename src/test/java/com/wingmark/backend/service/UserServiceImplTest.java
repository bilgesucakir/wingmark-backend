package com.wingmark.backend.service;

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
import com.wingmark.backend.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
