package com.wingmark.backend.service.impl;

import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards against tests reaching R2: in a normal test context no R2 client exists, whatever the environment or a local .env says. */
@SpringBootTest
class NoRealR2Test {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    void thereIsNoObjectStoreAndPhotosGoToTheDatabase() {
        assertThat(context.getBeansOfType(ObjectStore.class)).isEmpty();
        assertThat(fileStorageService).isInstanceOf(GridFsFileStorageServiceImpl.class);
    }
}
