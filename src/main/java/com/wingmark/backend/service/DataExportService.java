package com.wingmark.backend.service;

import com.wingmark.backend.dto.export.UserDataExportDto;

import java.util.Locale;
import java.util.UUID;

/** Assembles a complete copy of one user's data (right of access / portability). */
public interface DataExportService {

    UserDataExportDto export(UUID userId, Locale locale);
}
