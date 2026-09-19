package com.wingmark.backend.repository;

import com.wingmark.backend.entity.EmailVerificationToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends MongoRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
}
