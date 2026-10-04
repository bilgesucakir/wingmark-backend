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

/** Builds bird log queries from optional filters. */
@RequiredArgsConstructor
public class BirdLogRepositoryImpl implements BirdLogRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public long countDistinctSpeciesByUserId(UUID userId) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("speciesId").ne(null));
        return mongoTemplate.findDistinct(query, "speciesId", BirdLog.class, UUID.class).size();
    }

    @Override
    public List<BirdLog> findWithinBounds(UUID userId, double minLat, double maxLat, double minLng, double maxLng,
                                          Boolean hasSpecies, Gender gender, LifeStage lifeStage, int limit) {
        Criteria criteria = filterCriteria(userId, hasSpecies, gender, lifeStage)
                .and("latitude").gte(minLat).lte(maxLat);
        if (minLng <= maxLng) {
            criteria = criteria.and("longitude").gte(minLng).lte(maxLng);
        } else {
            // Box wraps around the antimeridian: east of minLng OR west of maxLng.
            criteria = criteria.orOperator(
                    Criteria.where("longitude").gte(minLng).lte(180),
                    Criteria.where("longitude").gte(-180).lte(maxLng));
        }

        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "observedAt"))
                .limit(limit);
        return mongoTemplate.find(query, BirdLog.class);
    }

    @Override
    public List<BirdLog> findFiltered(UUID userId, Boolean hasSpecies, Gender gender, LifeStage lifeStage, Sort.Direction observedAtDirection) {
        Query query = Query.query(filterCriteria(userId, hasSpecies, gender, lifeStage))
                .with(Sort.by(observedAtDirection, "observedAt"));
        return mongoTemplate.find(query, BirdLog.class);
    }

    private static Criteria filterCriteria(UUID userId, Boolean hasSpecies, Gender gender, LifeStage lifeStage) {
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
        return criteria;
    }
}
