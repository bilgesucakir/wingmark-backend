package com.wingmark.backend.config;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.RefreshToken;
import com.wingmark.backend.entity.User;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LegacyTimestampBackfillTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private LegacyTimestampBackfill backfill;

    private static Date at(String iso) {
        return Date.from(Instant.parse(iso));
    }

    /** Inserts a raw document the way pre-auditing-fix code left it: no createdAt at all. */
    private String insertLegacy(Class<?> entity, Document fields) {
        String id = "legacy-" + UUID.randomUUID();
        if (entity == User.class) {
            fields.append("username", id); // users has a unique username index
        }
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(entity)).insertOne(fields.append("_id", id));
        return id;
    }

    private Document reload(Class<?> entity, String id) {
        return mongoTemplate.findOne(Query.query(Criteria.where("_id").is(id)), Document.class, mongoTemplate.getCollectionName(entity));
    }

    @Test
    void createdAtIsTheEarliestTimestampTheDocumentAlreadyHas() {
        String userId = insertLegacy(User.class, new Document("email", "legacy@example.com")
                .append("updatedAt", at("2026-03-10T12:00:00Z"))
                .append("lastLoginAt", at("2026-02-01T08:00:00Z")));
        String logId = insertLegacy(BirdLog.class, new Document("pet", false)
                .append("observedAt", at("2026-01-15T07:30:00Z"))
                .append("updatedAt", at("2026-04-01T09:00:00Z")));

        backfill.run(null);

        assertThat(reload(User.class, userId).getDate("createdAt")).isEqualTo(at("2026-02-01T08:00:00Z"));
        assertThat(reload(BirdLog.class, logId).getDate("createdAt")).isEqualTo(at("2026-01-15T07:30:00Z"));
    }

    @Test
    void documentWithNoTimestampsAtAllGetsNow() {
        Date before = new Date();
        String tokenId = insertLegacy(RefreshToken.class, new Document("tokenHash", "h-" + UUID.randomUUID()));

        backfill.run(null);

        Date createdAt = reload(RefreshToken.class, tokenId).getDate("createdAt");
        assertThat(createdAt).isNotNull();
        assertThat(createdAt).isAfterOrEqualTo(new Date(before.getTime() - 1000));
    }

    @Test
    void birdLogWithoutObservedAtGetsItsCreatedAt() {
        String logId = insertLegacy(BirdLog.class, new Document("pet", false)
                .append("createdAt", at("2025-11-20T10:00:00Z")));

        backfill.run(null);

        assertThat(reload(BirdLog.class, logId).getDate("observedAt")).isEqualTo(at("2025-11-20T10:00:00Z"));
    }

    @Test
    void birdLogMissingBothGetsCreatedAtFromUpdatedAtThenObservedAtFromThat() {
        String logId = insertLegacy(BirdLog.class, new Document("pet", false)
                .append("updatedAt", at("2025-12-01T10:00:00Z")));

        backfill.run(null);

        Document log = reload(BirdLog.class, logId);
        assertThat(log.getDate("createdAt")).isEqualTo(at("2025-12-01T10:00:00Z"));
        assertThat(log.getDate("observedAt")).isEqualTo(at("2025-12-01T10:00:00Z"));
    }

    @Test
    void existingValuesAreNeverTouchedAndRerunsChangeNothing() {
        String untouchedId = insertLegacy(User.class, new Document("email", "fine@example.com")
                .append("createdAt", at("2024-06-01T00:00:00Z"))
                .append("updatedAt", at("2024-01-01T00:00:00Z"))); // earlier on purpose: must still not be used
        String legacyId = insertLegacy(User.class, new Document("email", "old@example.com")
                .append("updatedAt", at("2025-05-05T05:05:05Z")));

        backfill.run(null);
        Date firstPass = reload(User.class, legacyId).getDate("createdAt");
        backfill.run(null);

        assertThat(reload(User.class, untouchedId).getDate("createdAt")).isEqualTo(at("2024-06-01T00:00:00Z"));
        assertThat(reload(User.class, legacyId).getDate("createdAt")).isEqualTo(firstPass).isEqualTo(at("2025-05-05T05:05:05Z"));
    }

    @Test
    void coversEveryBaseEntityCollection() {
        assertThat(LegacyTimestampBackfill.AUDITED_ENTITIES).hasSize(12);
    }
}
