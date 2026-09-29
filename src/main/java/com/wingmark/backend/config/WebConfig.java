package com.wingmark.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * VIA_DTO serializes Page responses (e.g. GET /api/species) as a stable PagedModel
 * ({"content": [...], "page": {size, number, totalElements, totalPages}}) instead of
 * Spring Data's raw PageImpl, whose JSON shape is explicitly not guaranteed to stay
 * the same across versions.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig implements WebMvcConfigurer {
}
