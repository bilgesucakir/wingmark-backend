package com.wingmark.backend.repository;

import com.wingmark.backend.entity.AccountDeletion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** Audit records of deleted accounts. */
public interface AccountDeletionRepository extends MongoRepository<AccountDeletion, UUID> {
}
