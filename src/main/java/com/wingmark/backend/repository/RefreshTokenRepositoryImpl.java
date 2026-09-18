package com.wingmark.backend.repository;

import com.wingmark.backend.entity.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public void revokeAllForUser(UUID userId, Instant revokedAt) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("revokedAt").isNull());
        mongoTemplate.updateMulti(query, Update.update("revokedAt", revokedAt), RefreshToken.class);
    }
}
