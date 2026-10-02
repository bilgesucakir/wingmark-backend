package com.wingmark.backend.entity;

import com.wingmark.backend.enums.DeletionInitiator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record that an account was deleted, and when. Holds no personal data: userId is an
 * opaque id whose user no longer exists.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "account_deletions")
public class AccountDeletion extends BaseEntity {

    private UUID userId;

    private DeletionInitiator initiatedBy;

    private Instant requestedAt;

    private Instant completedAt;
}
