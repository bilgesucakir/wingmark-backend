package com.wingmark.backend.repository;

import java.util.UUID;

public interface BirdLogRepositoryCustom {

    long countDistinctSpeciesByUserId(UUID userId);
}
