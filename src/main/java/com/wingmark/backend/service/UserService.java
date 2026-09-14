package com.wingmark.backend.service;

import com.wingmark.backend.dto.user.SettingsResponse;
import com.wingmark.backend.dto.user.UpdateProfileRequest;
import com.wingmark.backend.dto.user.UpdateSettingsRequest;
import com.wingmark.backend.dto.user.UserProfileResponse;

import java.util.UUID;

public interface UserService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    SettingsResponse getSettings(UUID userId);

    SettingsResponse updateSettings(UUID userId, UpdateSettingsRequest request);
}
