package com.wingmark.backend.repository;

import com.wingmark.backend.entity.UserSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSettingsRepository extends MongoRepository<UserSettings, UUID> {

    Optional<UserSettings> findByUserId(UUID userId);
}
