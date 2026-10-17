package com.wingmark.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** An alternate app icon the admin has made available. Being stored means visible to users. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "app_icons")
public class AppIcon extends BaseEntity {

    /** Matches the icon name built into the iOS app, e.g. {@code halloween}. Unique. */
    @Indexed(unique = true)
    private String iconId;

    private String nameEn;

    private String nameTr;
}
