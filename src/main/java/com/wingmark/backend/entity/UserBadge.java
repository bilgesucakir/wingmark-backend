package com.wingmark.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "user_badges", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_id"}))
public class UserBadge extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "badge_id", nullable = false)
    private UUID badgeId;

    /** Null until the badge's criteria have actually been met. */
    private Instant earnedAt;

    /** Current progress toward the badge's criteriaValue, kept even after earning for display. */
    private Integer progress;
}
