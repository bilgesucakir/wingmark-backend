package com.wingmark.backend.entity;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/** A badge definition: criteria, tier and localized texts. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "badges")
public class Badge extends BaseEntity {

    /** Translations keyed by locale code, e.g. {"en": "First Flight", "tr": "İlk Uçuş"}. */
    private Map<String, String> name;

    /** Translations keyed by locale code. */
    private Map<String, String> description;

    private String icon;

    private BadgeCriteriaType criteriaType;

    private Integer criteriaValue;

    /** Extra criteria parameters, e.g. {@code {"radiusMeters": 5000}} for {@code SPECIES_IN_RADIUS}. */
    private Map<String, Object> criteriaMetadata;

    private BadgeTier tier;
}
