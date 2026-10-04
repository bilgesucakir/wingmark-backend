package com.wingmark.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Enables Mongo auditing of created/updated timestamps. */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
