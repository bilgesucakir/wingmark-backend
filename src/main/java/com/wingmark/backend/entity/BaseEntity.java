package com.wingmark.backend.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
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
     * silently skips @CreatedDate on insert. createdAt is only ever set by auditing,
     * so its absence is an unambiguous signal this document hasn't been saved yet.
     */
    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
