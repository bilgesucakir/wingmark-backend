package com.wingmark.backend.entity;

import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.enums.SpeciesStatus;
import com.wingmark.backend.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** A bird sighting recorded by a user, indexed for map-box and diary queries. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "bird_logs")
@CompoundIndex(name = "user_location", def = "{'userId': 1, 'latitude': 1, 'longitude': 1}")
@CompoundIndex(name = "user_observed_at", def = "{'userId': 1, 'observedAt': -1}")
public class BirdLog extends BaseEntity {

    private UUID userId;

    /** Null when the user does not know/select a species. */
    private UUID speciesId;

    /** Only meaningful when speciesId is set. */
    private SpeciesStatus speciesStatus;

    @Builder.Default
    private boolean pet = false;

    /** Name the user gave this bird (e.g. a pet's name, or a nickname for a wild sighting). */
    private String customName;

    @Builder.Default
    private LifeStage lifeStage = LifeStage.UNKNOWN;

    @Builder.Default
    private Gender gender = Gender.UNKNOWN;

    private String photoUrl;

    private String note;

    private Double latitude;

    private Double longitude;

    private String locationName;

    private Instant observedAt;

    /** Offset of the user's local time from UTC in minutes at the time of the sighting; null for older logs. */
    private Integer utcOffsetMinutes;

    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    /** Reserved for future ML-based image species detection. */
    private UUID detectedSpeciesId;

    private Double detectionConfidence;
}
