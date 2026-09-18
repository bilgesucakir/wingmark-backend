package com.wingmark.backend.repository;

import com.wingmark.backend.entity.BirdLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.UUID;

@RequiredArgsConstructor
public class BirdLogRepositoryImpl implements BirdLogRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public long countDistinctSpeciesByUserId(UUID userId) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("speciesId").ne(null)
                .and("pet").is(false));
        return mongoTemplate.findDistinct(query, "speciesId", BirdLog.class, UUID.class).size();
    }
}
