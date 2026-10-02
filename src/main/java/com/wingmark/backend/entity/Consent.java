package com.wingmark.backend.entity;

import com.wingmark.backend.enums.ConsentType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

/**
 * One acceptance of one version of a legal document by one user. createdAt is when it was
 * accepted. Append-only: accepting a new version adds a record rather than editing the old
 * one, so the history of what was accepted when is preserved.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "consents")
public class Consent extends BaseEntity {

    @Indexed
    private UUID userId;

    private ConsentType type;

    private String version;
}
