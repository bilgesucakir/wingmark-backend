package com.wingmark.backend.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "species")
public class Species extends BaseEntity {

    private String commonName;

    @Indexed(unique = true)
    private String scientificName;

    private String family;

    private String order;

    private String description;

    private String lifespan;

    private String diet;

    private String habitat;

    private String sizeDescription;

    private String conservationStatus;

    private String nativeRange;
}
