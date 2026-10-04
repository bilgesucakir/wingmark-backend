package com.wingmark.backend.config;

import com.wingmark.backend.entity.BaseEntity;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.stereotype.Component;

/** Marks every entity loaded from Mongo as not new (see {@code BaseEntity.isNew()}). */
@Component
public class MongoEntityLoadListener extends AbstractMongoEventListener<BaseEntity> {

    /** Marks the loaded entity as not new. */
    @Override
    public void onAfterConvert(AfterConvertEvent<BaseEntity> event) {
        event.getSource().markNotNew();
    }
}
