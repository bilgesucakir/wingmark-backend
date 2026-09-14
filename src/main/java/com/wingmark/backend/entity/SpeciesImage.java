package com.wingmark.backend.entity;

import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
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

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "species_images")
public class SpeciesImage extends BaseEntity {

    @Column(nullable = false)
    private UUID speciesId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LifeStageImage lifeStage;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ImageGender gender;

    @Column(nullable = false)
    private String imageUrl;

    private String caption;
}
