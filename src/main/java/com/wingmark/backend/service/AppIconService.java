package com.wingmark.backend.service;

import com.wingmark.backend.dto.appicon.AdminAppIconResponseDto;
import com.wingmark.backend.dto.appicon.AppIconResponseDto;
import com.wingmark.backend.dto.appicon.CreateAppIconRequestDto;
import com.wingmark.backend.dto.appicon.UpdateAppIconRequestDto;

import java.util.List;

/** The list of alternate app icons the admin has made available. */
public interface AppIconService {

    /** The icons users may pick, oldest first. */
    List<AppIconResponseDto> list();

    /** The same list with creation times, for the admin screen. */
    List<AdminAppIconResponseDto> listForAdmin();

    /** Adds an icon; throws a conflict if the iconId is already in the list. */
    AdminAppIconResponseDto add(CreateAppIconRequestDto request);

    /** Changes the display names of an icon; throws not-found if it is not in the list. */
    AdminAppIconResponseDto update(String iconId, UpdateAppIconRequestDto request);

    /** Removes an icon from the list, hiding it from users; throws not-found if it is not there. */
    void delete(String iconId);
}
