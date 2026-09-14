package com.wingmark.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "species")
public class Species extends BaseEntity {

    @Column(nullable = false)
    private String commonName;

    @Column(nullable = false, unique = true)
    private String scientificName;

    private String family;

    @Column(name = "taxonomic_order")
    private String order;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String lifespan;

    @Column(columnDefinition = "TEXT")
    private String diet;

    private String habitat;

    private String sizeDescription;

    private String conservationStatus;

    private String nativeRange;
}
