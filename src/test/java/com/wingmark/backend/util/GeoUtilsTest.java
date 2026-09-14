package com.wingmark.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilsTest {

    @Test
    void distanceBetweenIdenticalPointsIsZero() {
        assertThat(GeoUtils.distanceMeters(40.0, 29.0, 40.0, 29.0)).isEqualTo(0.0);
    }

    @Test
    void distanceMatchesKnownReferenceValue() {
        // Istanbul (Sultanahmet) to Kadikoy, roughly 6.4km apart.
        double distance = GeoUtils.distanceMeters(41.0055, 28.9769, 40.9906, 29.0328);
        assertThat(distance).isBetween(4500.0, 5500.0);
    }

    @Test
    void distanceIsSymmetric() {
        double forward = GeoUtils.distanceMeters(10.0, 20.0, 30.0, 40.0);
        double backward = GeoUtils.distanceMeters(30.0, 40.0, 10.0, 20.0);
        assertThat(forward).isEqualTo(backward);
    }
}
