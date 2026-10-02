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

    /**
     * Where the image came from and under what terms - needed to credit third-party photos
     * (e.g. iNaturalist's CC licenses require attribution). licenseCode is e.g. "cc-by";
     * all three are null for images the operator uploaded themselves.
     */
    private String licenseCode;

    private String attribution;

    private String sourceUrl;
}
