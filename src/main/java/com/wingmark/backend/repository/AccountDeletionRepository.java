package com.wingmark.backend.repository;

import com.wingmark.backend.entity.AccountDeletion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface AccountDeletionRepository extends MongoRepository<AccountDeletion, UUID> {
}
