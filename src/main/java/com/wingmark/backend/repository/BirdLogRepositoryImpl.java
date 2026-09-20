package com.wingmark.backend.repository;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
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

    @Override
    public List<BirdLog> findFiltered(UUID userId, Boolean hasSpecies, Gender gender, LifeStage lifeStage, Sort.Direction observedAtDirection) {
        Criteria criteria = new Criteria();
        if (userId != null) {
            criteria = criteria.and("userId").is(userId);
        }
        if (hasSpecies != null) {
            criteria = hasSpecies ? criteria.and("speciesId").ne(null) : criteria.and("speciesId").is(null);
        }
        if (gender != null) {
            criteria = criteria.and("gender").is(gender);
        }
        if (lifeStage != null) {
            criteria = criteria.and("lifeStage").is(lifeStage);
        }

        Query query = Query.query(criteria).with(Sort.by(observedAtDirection, "observedAt"));
        return mongoTemplate.find(query, BirdLog.class);
    }
}
