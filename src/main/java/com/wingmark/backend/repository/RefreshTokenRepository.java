package com.wingmark.backend.repository;

import com.wingmark.backend.entity.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, UUID>, RefreshTokenRepositoryCustom {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
