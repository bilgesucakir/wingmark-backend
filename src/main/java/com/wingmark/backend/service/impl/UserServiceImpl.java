package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.user.SettingsResponse;
import com.wingmark.backend.dto.user.UpdateProfileRequest;
import com.wingmark.backend.dto.user.UpdateSettingsRequest;
import com.wingmark.backend.dto.user.UserProfileResponse;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SpeciesRepository speciesRepository;

    @Override
    public UserProfileResponse getProfile(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
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
    public SettingsResponse getSettings(UUID userId) {
        UserSettings settings = findSettings(userId);
        return new SettingsResponse(settings.getUnitPreference(), settings.getLocale());
    }

    @Override
    @Transactional
    public SettingsResponse updateSettings(UUID userId, UpdateSettingsRequest request) {
        UserSettings settings = findSettings(userId);
        settings.setUnitPreference(request.unitPreference());
        settings.setLocale(request.locale());
        userSettingsRepository.save(settings);
        return new SettingsResponse(settings.getUnitPreference(), settings.getLocale());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private UserSettings findSettings(UUID userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("UserSettings", userId));
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
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
