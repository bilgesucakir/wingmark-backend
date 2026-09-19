package com.wingmark.backend.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "species")
public class Species extends BaseEntity {

    /** Translations keyed by locale code, e.g. {"en": "Great Tit", "tr": "Büyük Baştankara"}. */
    private Map<String, String> commonName;

    @Indexed(unique = true)
    private String scientificName;

    private String family;

    private String order;

    /** Translations keyed by locale code. */
    private Map<String, String> description;

    /** Translations keyed by locale code. */
    private Map<String, String> lifespan;

    /** Translations keyed by locale code. */
    private Map<String, String> diet;

    /** Translations keyed by locale code. */
    private Map<String, String> habitat;

    /** Translations keyed by locale code. */
    private Map<String, String> sizeDescription;

    /** Translations keyed by locale code. */
    private Map<String, String> conservationStatus;

    /** Translations keyed by locale code. */
    private Map<String, String> nativeRange;
}
