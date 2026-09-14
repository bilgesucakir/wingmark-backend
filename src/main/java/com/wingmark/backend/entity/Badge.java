package com.wingmark.backend.entity;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
     * Free-form JSON for criteria that need extra parameters, e.g. SPECIES_IN_RADIUS
     * needs a radius in meters in addition to the species-count criteriaValue:
     * {"radiusMeters": 5000}
     */
    @Column(columnDefinition = "TEXT")
    private String criteriaMetadata;

    @Enumerated(EnumType.STRING)
    private BadgeTier tier;
}
