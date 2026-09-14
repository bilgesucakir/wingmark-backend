package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {
}
