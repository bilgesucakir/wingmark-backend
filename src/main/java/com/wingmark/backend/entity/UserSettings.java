package com.wingmark.backend.entity;

import com.wingmark.backend.enums.UnitPreference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "user_settings")
public class UserSettings extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UnitPreference unitPreference = UnitPreference.METRIC;

    private String locale;
}
