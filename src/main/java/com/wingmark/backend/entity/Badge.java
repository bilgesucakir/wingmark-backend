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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "badges")
public class Badge extends BaseEntity {

    private String name;

    private String description;

    private String icon;

    private BadgeCriteriaType criteriaType;

    private Integer criteriaValue;

    /**
     * Free-form parameters for criteria that need more than the plain count in
     * criteriaValue, e.g. SPECIES_IN_RADIUS needs a radius in meters: {"radiusMeters": 5000}.
     * Stored as a native embedded document.
     */
    private Map<String, Object> criteriaMetadata;

    private BadgeTier tier;
}
