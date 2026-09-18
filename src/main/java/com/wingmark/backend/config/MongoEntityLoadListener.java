package com.wingmark.backend.config;

import com.wingmark.backend.entity.BaseEntity;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.stereotype.Component;

/**
 * Pairs with BaseEntity.isNew(): every entity read back from Mongo (find, findById, ...)
 * comes through here and gets marked as not-new, since id is always pre-populated
 * client-side and can't be used as the "is this new?" signal on its own.
 */
@Component
public class MongoEntityLoadListener extends AbstractMongoEventListener<BaseEntity> {

    @Override
    public void onAfterConvert(AfterConvertEvent<BaseEntity> event) {
        event.getSource().markNotNew();
    }
}
