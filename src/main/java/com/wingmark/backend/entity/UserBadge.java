package com.wingmark.backend.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "user_badges")
@CompoundIndex(name = "user_badge_unique", def = "{'userId': 1, 'badgeId': 1}", unique = true)
public class UserBadge extends BaseEntity {

    private UUID userId;

    private UUID badgeId;

    /** Null until the badge's criteria have actually been met. */
    private Instant earnedAt;

    /** Current progress toward the badge's criteriaValue, kept even after earning for display. */
    private Integer progress;
}
