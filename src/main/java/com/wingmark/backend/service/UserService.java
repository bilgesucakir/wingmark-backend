package com.wingmark.backend.service;

import com.wingmark.backend.dto.user.SettingsResponseDto;
import com.wingmark.backend.dto.user.UpdateProfileRequestDto;
import com.wingmark.backend.dto.user.UpdateSettingsRequestDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;

import java.util.UUID;

/** A user's own profile and app settings. */
public interface UserService {

    /** Returns a user's profile. */
    UserProfileResponseDto getProfile(UUID userId);

    /** Updates a user's profile (name, profile picture, favorite species). */
    UserProfileResponseDto updateProfile(UUID userId, UpdateProfileRequestDto request);

    /** Returns a user's app settings (unit preference, locale). */
    SettingsResponseDto getSettings(UUID userId);

    /** Updates a user's app settings. */
    SettingsResponseDto updateSettings(UUID userId, UpdateSettingsRequestDto request);
}
