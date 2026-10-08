package com.wingmark.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

/**
 * Index of photos kept in object storage (R2): who owns each one and how big it is. R2 cannot be queried by owner,
 * so the per-user limit and the cleanup read this instead of listing the bucket. Holds no photo content.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "uploaded_files")
public class UploadedFile extends BaseEntity {

    /** The photo's file name, e.g. {@code <uuid>.jpg} (the thumbnail is not indexed separately). */
    @Indexed(unique = true)
    private String filename;

    /** The user who uploaded it, or null for photos that had no recorded owner. */
    @Indexed
    private UUID ownerId;

    private long sizeBytes;
}
