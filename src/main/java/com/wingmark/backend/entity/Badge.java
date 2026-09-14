package com.wingmark.backend.entity;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import com.wingmark.backend.util.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "badges")
public class Badge extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String icon;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BadgeCriteriaType criteriaType;

    @Column(nullable = false)
    private Integer criteriaValue;

    /**
     * Free-form parameters for criteria that need more than the plain count in
     * criteriaValue, e.g. SPECIES_IN_RADIUS needs a radius in meters: {"radiusMeters": 5000}.
     * Stored as a JSON string in a single TEXT column via JsonMapConverter.
     */
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> criteriaMetadata;

    @Enumerated(EnumType.STRING)
    private BadgeTier tier;
}
