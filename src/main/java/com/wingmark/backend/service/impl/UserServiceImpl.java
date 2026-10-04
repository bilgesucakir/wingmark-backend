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
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ConflictException;
import com.wingmark.backend.exception.InvalidReferenceException;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.exception.UnauthorizedActionException;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.service.AccountDeletionService;
import com.wingmark.backend.service.BadgeService;
import com.wingmark.backend.service.AvatarCatalog;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.UserService;
import com.wingmark.backend.util.LocalizedTextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Profiles, settings and admin account management. */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SpeciesRepository speciesRepository;
    private final AccountDeletionService accountDeletionService;
    private final PasswordEncoder passwordEncoder;
    private final AvatarCatalog avatarCatalog;
    private final FileStorageService fileStorageService;
    private final BadgeService badgeService;

    @Override
    public UserProfileResponseDto getProfile(UUID userId, Locale locale) {
        return toResponse(findUser(userId), locale);
    }

    @Override
    public UserProfileResponseDto updateProfile(UUID userId, UpdateProfileRequestDto request, Locale locale) {
        User user = findUser(userId);

        if (request.favoriteSpeciesId() != null && !speciesRepository.existsById(request.favoriteSpeciesId())) {
            throw InvalidReferenceException.of("favoriteSpeciesId", "species", request.favoriteSpeciesId());
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setProfilePicture(validateProfilePicture(request.profilePicture(), user.getProfilePicture()));
        boolean favoriteChanged = !java.util.Objects.equals(user.getFavoriteSpeciesId(), request.favoriteSpeciesId());
        user.setFavoriteSpeciesId(request.favoriteSpeciesId());

        User saved = userRepository.save(user);
        if (favoriteChanged) {
            badgeService.evaluateForUser(userId);
        }
        return toResponse(saved, locale);
    }

    @Override
    public SettingsResponseDto getSettings(UUID userId) {
        UserSettings settings = findSettings(userId);
        return new SettingsResponseDto(settings.getUnitPreference(), settings.getLocale());
    }

    @Override
    public SettingsResponseDto updateSettings(UUID userId, UpdateSettingsRequestDto request) {
        UserSettings settings = findSettings(userId);
        settings.setUnitPreference(request.unitPreference());
        settings.setLocale(request.locale());
        userSettingsRepository.save(settings);
        return new SettingsResponseDto(settings.getUnitPreference(), settings.getLocale());
    }

    @Override
    public List<UserProfileResponseDto> getAllUsers(Locale locale) {
        return userRepository.findAll().stream().map(user -> toResponse(user, locale)).toList();
    }

    @Override
    public UserProfileResponseDto adminUpdateUser(UUID userId, AdminUpdateUserRequestDto request, Locale locale) {
        User user = findUser(userId);

        if (request.favoriteSpeciesId() != null && !speciesRepository.existsById(request.favoriteSpeciesId())) {
            throw InvalidReferenceException.of("favoriteSpeciesId", "species", request.favoriteSpeciesId());
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        user.setEmailVerified(request.emailVerified());
        boolean favoriteChanged = !java.util.Objects.equals(user.getFavoriteSpeciesId(), request.favoriteSpeciesId());
        user.setFavoriteSpeciesId(request.favoriteSpeciesId());
        user.setProfilePicture(validateProfilePicture(request.profilePicture(), user.getProfilePicture()));
        User saved = userRepository.save(user);
        if (favoriteChanged) {
            badgeService.evaluateForUser(userId);
        }
        return toResponse(saved, locale);
    }

    @Override
    public void deleteUser(UUID userId) {
        accountDeletionService.deleteAccount(userId, DeletionInitiator.ADMIN);
    }

    @Override
    public void deleteOwnAccount(UUID userId, String password) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedActionException(ErrorCode.WRONG_PASSWORD, "Password is incorrect");
        }
        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new ConflictException(ErrorCode.LAST_ADMIN, "You are the only admin - make another user an admin before deleting this account");
        }

        accountDeletionService.deleteAccount(userId, DeletionInitiator.SELF);
    }

    /** Validates a profile picture: null, a preset avatar key, an existing upload URL or the value already stored. Anything else is rejected. */
    private String validateProfilePicture(String profilePicture, String current) {
        if (profilePicture == null || profilePicture.isBlank()) {
            return null;
        }
        if (profilePicture.equals(current)) {
            return profilePicture;
        }
        if (avatarCatalog.contains(profilePicture)) {
            return profilePicture;
        }
        boolean isOwnUpload = fileStorageService.storedFilename(profilePicture)
                .filter(filename -> profilePicture.equals("/uploads/" + filename))
                .filter(fileStorageService::exists)
                .isPresent();
        if (isOwnUpload) {
            return profilePicture;
        }
        throw new BadRequestException(ErrorCode.INVALID_PROFILE_PICTURE,
                "profilePicture must be a preset avatar key (see GET /api/avatars), a /uploads/... URL from POST /api/uploads/photo, or null");
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private UserSettings findSettings(UUID userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("UserSettings", userId));
    }

    private UserProfileResponseDto toResponse(User user, Locale locale) {
        String favoriteSpeciesName = null;
        if (user.getFavoriteSpeciesId() != null) {
            favoriteSpeciesName = speciesRepository.findById(user.getFavoriteSpeciesId())
                    .map(Species::getCommonName)
                    .map(commonName -> LocalizedTextResolver.resolve(commonName, locale))
                    .orElse(null);
        }

        return new UserProfileResponseDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePicture(),
                user.getFavoriteSpeciesId(),
                favoriteSpeciesName,
                user.getRole(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
