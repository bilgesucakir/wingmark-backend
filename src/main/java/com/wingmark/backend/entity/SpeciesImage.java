package com.wingmark.backend.entity;

import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

/** A reference image of a species with license details. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "species_images")
public class SpeciesImage extends BaseEntity {

    @Indexed
    private UUID speciesId;

    private LifeStageImage lifeStage;

    private ImageGender gender;

    private String imageUrl;

    private String caption;

    /** Source license, attribution and URL of the image; all null for the operator's own uploads. */
    private String licenseCode;

    private String attribution;

    private String sourceUrl;
}
