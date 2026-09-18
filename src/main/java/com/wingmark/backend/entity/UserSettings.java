package com.wingmark.backend.entity;

import com.wingmark.backend.enums.UnitPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "user_settings")
public class UserSettings extends BaseEntity {

    @Indexed(unique = true)
    private UUID userId;

    @Builder.Default
    private UnitPreference unitPreference = UnitPreference.METRIC;

    private String locale;
}
