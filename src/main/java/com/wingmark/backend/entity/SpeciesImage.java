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
}
