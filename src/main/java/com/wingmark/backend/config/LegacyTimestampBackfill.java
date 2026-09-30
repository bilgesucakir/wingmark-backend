package com.wingmark.backend.config;

import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.EmailVerificationToken;
import com.wingmark.backend.entity.PasswordResetToken;
import com.wingmark.backend.entity.RefreshToken;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.SpeciesImage;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserBadge;
import com.wingmark.backend.entity.UserSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Documents saved before BaseEntity's auditing fix have no createdAt, and very old bird logs
 * may lack observedAt, so the API returned null for them. On startup this fills them in:
 *
 * - createdAt: the earliest timestamp the document already carries (updatedAt,
 *   lastLoginAt, observedAt, usedAt, revokedAt, earnedAt) - each of those happened at or
 *   after creation, so the earliest is the closest estimate - or now if it has none.
 * - bird_logs.observedAt: its createdAt.
 *
 * Idempotent and race-safe: every update is conditional on the field still being missing,
 * so repeated startups (or several instances) never overwrite a real value.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyTimestampBackfill implements ApplicationRunner {

    static final List<Class<?>> AUDITED_ENTITIES = List.of(
            User.class, UserSettings.class, BirdLog.class, Species.class, SpeciesImage.class,
            Badge.class, UserBadge.class, RefreshToken.class, PasswordResetToken.class,
            EmailVerificationToken.class);

    /** Fields that, when present, record a moment at or after the document's creation. */
    private static final List<String> LATER_TIMESTAMPS = List.of(
            "updatedAt", "lastLoginAt", "observedAt", "usedAt", "revokedAt", "earnedAt");

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (Class<?> entity : AUDITED_ENTITIES) {
            backfillCreatedAt(mongoTemplate.getCollectionName(entity));
        }
        backfillObservedAt(mongoTemplate.getCollectionName(BirdLog.class));
    }

    private void backfillCreatedAt(String collection) {
        List<Document> missing = mongoTemplate.find(Query.query(isMissing("createdAt")), Document.class, collection);
        if (missing.isEmpty()) {
            return;
        }
        int estimated = 0;
        for (Document doc : missing) {
            Date earliest = LATER_TIMESTAMPS.stream()
                    .map(field -> doc.get(field) instanceof Date date ? date : null)
                    .filter(Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(null);
            if (earliest != null) {
                estimated++;
            }
            Date createdAt = earliest != null ? earliest : new Date();
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(doc.get("_id")).andOperator(isMissing("createdAt"))),
                    Update.update("createdAt", createdAt),
                    collection);
        }
        log.info("Backfilled createdAt on {} {} document(s) ({} from their own timestamps, {} set to now)",
                missing.size(), collection, estimated, missing.size() - estimated);
    }

    private void backfillObservedAt(String collection) {
        long updated = mongoTemplate.updateMulti(
                Query.query(new Criteria().andOperator(isMissing("observedAt"), isPresent("createdAt"))),
                AggregationUpdate.update().set("observedAt").toValueOf(Fields.field("createdAt")),
                collection).getModifiedCount();
        if (updated > 0) {
            log.info("Backfilled observedAt from createdAt on {} {} document(s)", updated, collection);
        }
    }

    private static Criteria isMissing(String field) {
        return new Criteria().orOperator(Criteria.where(field).exists(false), Criteria.where(field).is(null));
    }

    private static Criteria isPresent(String field) {
        return Criteria.where(field).exists(true).ne(null);
    }
}
