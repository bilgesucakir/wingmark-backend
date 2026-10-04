package com.wingmark.backend.config;

import com.wingmark.backend.entity.AccountDeletion;
import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.Consent;
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
 * On startup, fills missing {@code createdAt} (earliest timestamp the document already has, else now) and
 * missing bird-log {@code observedAt} (its {@code createdAt}). Updates only missing fields, so it is safe to rerun.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyTimestampBackfill implements ApplicationRunner {

    /** Entities whose collections are backfilled. */
    static final List<Class<?>> AUDITED_ENTITIES = List.of(
            User.class, UserSettings.class, BirdLog.class, Species.class, SpeciesImage.class,
            Badge.class, UserBadge.class, RefreshToken.class, PasswordResetToken.class,
            EmailVerificationToken.class, Consent.class, AccountDeletion.class);

    /** Fields that, when present, record a moment at or after the document's creation. */
    private static final List<String> LATER_TIMESTAMPS = List.of(
            "updatedAt", "lastLoginAt", "observedAt", "usedAt", "revokedAt", "earnedAt");

    private final MongoTemplate mongoTemplate;

    /** Backfills every audited collection, then bird-log {@code observedAt}. */
    @Override
    public void run(ApplicationArguments args) {
        for (Class<?> entity : AUDITED_ENTITIES) {
            backfillCreatedAt(mongoTemplate.getCollectionName(entity));
        }
        backfillObservedAt(mongoTemplate.getCollectionName(BirdLog.class));
    }

    /** Sets a missing {@code createdAt} to the earliest later timestamp in the document, else now. */
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

    /** Sets a missing {@code observedAt} to the bird log's {@code createdAt}. */
    private void backfillObservedAt(String collection) {
        long updated = mongoTemplate.updateMulti(
                Query.query(new Criteria().andOperator(isMissing("observedAt"), isPresent("createdAt"))),
                AggregationUpdate.update().set("observedAt").toValueOf(Fields.field("createdAt")),
                collection).getModifiedCount();
        if (updated > 0) {
            log.info("Backfilled observedAt from createdAt on {} {} document(s)", updated, collection);
        }
    }

    /** Matches documents where the field is absent or null. */
    private static Criteria isMissing(String field) {
        return new Criteria().orOperator(Criteria.where(field).exists(false), Criteria.where(field).is(null));
    }

    /** Matches documents where the field is present and not null. */
    private static Criteria isPresent(String field) {
        return Criteria.where(field).exists(true).ne(null);
    }
}
