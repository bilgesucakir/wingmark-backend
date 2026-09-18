package com.wingmark.backend.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * id is always pre-populated client-side (never left null for Mongo to assign),
     * so Spring Data's default null-id "is this new?" check always says false, which
     * silently skips @CreatedDate on insert. This transient flag is the real signal
     * instead: true until MongoEntityLoadListener.onAfterConvert flips it after a
     * successful load from the database, so it reflects "not yet persisted" regardless
     * of any field's value - unlike a createdAt-null check, it isn't fooled by legacy
     * documents that predate this fix and have no createdAt of their own.
     */
    @Transient
    @Builder.Default
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void markNotNew() {
        this.newEntity = false;
    }
}
