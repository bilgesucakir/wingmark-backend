package com.wingmark.backend.config;

import com.wingmark.backend.entity.EmailVerificationToken;
import com.wingmark.backend.entity.PasswordResetToken;
import com.wingmark.backend.entity.RefreshToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Expired tokens are deleted by Mongo's TTL monitor instead of piling up forever (C-07 retention). */
@SpringBootTest
class TokenRetentionIndexTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void everyTokenCollectionHasAnExpiresAtTtlIndex() {
        for (Class<?> token : new Class<?>[]{RefreshToken.class, PasswordResetToken.class, EmailVerificationToken.class}) {
            assertThat(mongoTemplate.indexOps(token).getIndexInfo())
                    .as(token.getSimpleName())
                    .anySatisfy((IndexInfo index) -> {
                        assertThat(index.getIndexFields()).anyMatch(f -> f.getKey().equals("expiresAt"));
                        assertThat(index.getExpireAfter()).contains(Duration.ZERO);
                    });
        }
    }
}
