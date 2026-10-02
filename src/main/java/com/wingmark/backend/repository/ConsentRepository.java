package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Consent;
import com.wingmark.backend.enums.ConsentType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentRepository extends MongoRepository<Consent, UUID> {

    List<Consent> findByUserIdOrderByCreatedAtAsc(UUID userId);

    boolean existsByUserIdAndTypeAndVersion(UUID userId, ConsentType type, String version);

    void deleteByUserId(UUID userId);
}
