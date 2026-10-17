package com.wingmark.backend.repository;

import com.wingmark.backend.entity.AppIcon;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

/** Alternate app icons made available by the admin. */
public interface AppIconRepository extends MongoRepository<AppIcon, UUID> {

    boolean existsByIconId(String iconId);

    Optional<AppIcon> findByIconId(String iconId);
}
