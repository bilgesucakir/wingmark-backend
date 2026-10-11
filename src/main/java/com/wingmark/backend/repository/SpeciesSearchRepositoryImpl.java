package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Species;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Builds the combined species queries with {@link MongoTemplate}. */
@RequiredArgsConstructor
public class SpeciesSearchRepositoryImpl implements SpeciesSearchRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Species> search(String nameRegex, List<String> orders, List<String> families,
                                Collection<UUID> seenIds, Boolean seen, Pageable pageable) {
        Criteria criteria = criteria(nameRegex, orders, families, seenIds, seen);
        long total = mongoTemplate.count(Query.query(criteria), Species.class);
        List<Species> content = mongoTemplate.find(Query.query(criteria).with(pageable), Species.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<FacetRow> facetRows(String nameRegex, List<String> orders, List<String> families,
                                    Collection<UUID> seenIds, Boolean seen) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria(nameRegex, orders, families, seenIds, seen)),
                Aggregation.group("order", "family").count().as("count"));
        List<FacetRow> rows = new ArrayList<>();
        for (Document row : mongoTemplate.aggregate(aggregation, Species.class, Document.class).getMappedResults()) {
            Object id = row.get("_id");
            Document keys = id instanceof Document document ? document : row;
            Number count = row.get("count", Number.class);
            rows.add(new FacetRow(keys.getString("order"), keys.getString("family"), count == null ? 0 : count.longValue()));
        }
        return rows;
    }

    private static Criteria criteria(String nameRegex, List<String> orders, List<String> families,
                                     Collection<UUID> seenIds, Boolean seen) {
        List<Criteria> all = new ArrayList<>();
        if (nameRegex != null) {
            all.add(new Criteria().orOperator(
                    Criteria.where("commonName.en").regex(nameRegex, "i"),
                    Criteria.where("commonName.tr").regex(nameRegex, "i"),
                    Criteria.where("scientificName").regex(nameRegex, "i")));
        }
        if (orders != null && !orders.isEmpty()) {
            all.add(Criteria.where("order").in(exactIgnoringCase(orders)));
        }
        if (families != null && !families.isEmpty()) {
            all.add(Criteria.where("family").in(exactIgnoringCase(families)));
        }
        if (seen != null) {
            all.add(seen ? Criteria.where("id").in(seenIds) : Criteria.where("id").nin(seenIds));
        }
        return all.isEmpty() ? new Criteria() : new Criteria().andOperator(all.toArray(new Criteria[0]));
    }

    private static List<Pattern> exactIgnoringCase(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> Pattern.compile("^\\s*" + Pattern.quote(value.trim()) + "\\s*$", Pattern.CASE_INSENSITIVE))
                .toList();
    }
}
