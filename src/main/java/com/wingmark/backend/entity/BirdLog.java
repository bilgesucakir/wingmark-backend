package com.wingmark.backend.entity;

import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.enums.SpeciesStatus;
import com.wingmark.backend.enums.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Table(name = "bird_logs")
public class BirdLog extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    /** Null when the user does not know/select a species. */
    private UUID speciesId;

    /** Only meaningful when speciesId is set. */
    @Enumerated(EnumType.STRING)
    private SpeciesStatus speciesStatus;

    @Column(nullable = false)
    @Builder.Default
    private boolean pet = false;

    /** Name the user gave this bird (e.g. a pet's name, or a nickname for a wild sighting). */
    private String customName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LifeStage lifeStage = LifeStage.UNKNOWN;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Gender gender = Gender.UNKNOWN;

    private String photoUrl;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private String locationName;

    @Column(nullable = false)
    private Instant observedAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    /** Reserved for future ML-based image species detection. */
    private UUID detectedSpeciesId;

    private Double detectionConfidence;
}
