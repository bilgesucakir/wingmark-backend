package com.wingmark.backend.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonMapConverterTest {

    private final JsonMapConverter converter = new JsonMapConverter();

    @Test
    void roundTripsAMapThroughJson() {
        Map<String, Object> original = Map.of("radiusMeters", 5000);

        String json = converter.convertToDatabaseColumn(original);
        Map<String, Object> restored = converter.convertToEntityAttribute(json);

        assertThat(restored.get("radiusMeters")).isEqualTo(5000);
    }

    @Test
    void emptyMapConvertsToNullColumn() {
        assertThat(converter.convertToDatabaseColumn(Map.of())).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void blankColumnConvertsToEmptyMap() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void malformedJsonThrowsRatherThanSilentlyDroppingData() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> converter.convertToEntityAttribute("{not valid json"));
    }
}
