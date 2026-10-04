package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Badge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** Badge definitions. */
public interface BadgeRepository extends MongoRepository<Badge, UUID> {
}
