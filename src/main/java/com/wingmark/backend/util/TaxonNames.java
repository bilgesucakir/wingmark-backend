package com.wingmark.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/**
 * Display names for orders and families, from {@code taxonomy/taxon-names.json}: the Latin name maps to English and
 * Turkish. A name that is not in the file is shown as its Latin name.
 */
@Component
public class TaxonNames {

    private final Map<String, Map<String, String>> orders;
    private final Map<String, Map<String, String>> families;

    public TaxonNames(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("taxonomy/taxon-names.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            this.orders = read(root.path("orders"));
            this.families = read(root.path("families"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read taxonomy/taxon-names.json", e);
        }
    }

    private static Map<String, Map<String, String>> read(JsonNode node) {
        Map<String, Map<String, String>> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            Map<String, String> names = new HashMap<>();
            entry.getValue().fields().forEachRemaining(name -> names.put(name.getKey(), name.getValue().asText()));
            result.put(entry.getKey().toLowerCase(Locale.ROOT), names);
        });
        return result;
    }

    /** Returns the order's name in the locale (English fallback), or its Latin name if unknown. */
    public String order(String latin, Locale locale) {
        return resolve(orders, latin, locale);
    }

    /** Returns the family's name in the locale (English fallback), or its Latin name if unknown. */
    public String family(String latin, Locale locale) {
        return resolve(families, latin, locale);
    }

    private static String resolve(Map<String, Map<String, String>> names, String latin, Locale locale) {
        String resolved = LocalizedTextResolver.resolve(names.get(latin.trim().toLowerCase(Locale.ROOT)), locale);
        return resolved != null ? resolved : latin.trim();
    }
}
