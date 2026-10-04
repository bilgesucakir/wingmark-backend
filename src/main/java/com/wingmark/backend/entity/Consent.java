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

/** One user's acceptance of one version of a legal document. Append-only, so history is kept. */
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
