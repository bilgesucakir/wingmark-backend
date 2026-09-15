package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.admin.AdminUpdateUserRequestDto;
import com.wingmark.backend.dto.user.SettingsResponseDto;
import com.wingmark.backend.dto.user.UpdateProfileRequestDto;
import com.wingmark.backend.dto.user.UpdateSettingsRequestDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserSettings;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SpeciesRepository speciesRepository;

    @Override
    public UserProfileResponseDto getProfile(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Override
    @Transactional
    public UserProfileResponseDto updateProfile(UUID userId, UpdateProfileRequestDto request) {
        User user = findUser(userId);

        if (request.favoriteSpeciesId() != null && !speciesRepository.existsById(request.favoriteSpeciesId())) {
            throw ResourceNotFoundException.of("Species", request.favoriteSpeciesId());
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setProfilePicture(request.profilePicture());
        user.setFavoriteSpeciesId(request.favoriteSpeciesId());

        return toResponse(userRepository.save(user));
    }

    @Override
    public SettingsResponseDto getSettings(UUID userId) {
        UserSettings settings = findSettings(userId);
        return new SettingsResponseDto(settings.getUnitPreference(), settings.getLocale());
    }

    @Override
    @Transactional
    public SettingsResponseDto updateSettings(UUID userId, UpdateSettingsRequestDto request) {
        UserSettings settings = findSettings(userId);
        settings.setUnitPreference(request.unitPreference());
        settings.setLocale(request.locale());
        userSettingsRepository.save(settings);
        return new SettingsResponseDto(settings.getUnitPreference(), settings.getLocale());
    }

    @Override
    public List<UserProfileResponseDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public UserProfileResponseDto adminUpdateUser(UUID userId, AdminUpdateUserRequestDto request) {
        User user = findUser(userId);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        user.setEmailVerified(request.emailVerified());
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.of("User", userId);
        }
        userRepository.deleteById(userId);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private UserSettings findSettings(UUID userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("UserSettings", userId));
    }

    private UserProfileResponseDto toResponse(User user) {
        return new UserProfileResponseDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePicture(),
                user.getFavoriteSpeciesId(),
                user.getRole(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
