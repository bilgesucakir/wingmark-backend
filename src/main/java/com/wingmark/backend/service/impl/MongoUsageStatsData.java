package com.wingmark.backend.service.impl;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.service.UsageStatsData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/** Loads only the statistics fields from MongoDB. */
@Component
@RequiredArgsConstructor
public class MongoUsageStatsData implements UsageStatsData {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<UserRow> users() {
        Query query = new Query();
        query.fields().include("createdAt", "lastLoginAt");
        return mongoTemplate.find(query, User.class).stream()
                .map(user -> new UserRow(user.getCreatedAt(), user.getLastLoginAt())).toList();
    }

    @Override
    public List<LogRow> logs() {
        Query query = new Query();
        query.fields().include("userId", "speciesId", "createdAt", "latitude", "longitude");
        return mongoTemplate.find(query, BirdLog.class).stream()
                .map(log -> new LogRow(log.getUserId(), log.getSpeciesId(), log.getCreatedAt(), log.getLatitude(), log.getLongitude()))
                .toList();
    }
}
