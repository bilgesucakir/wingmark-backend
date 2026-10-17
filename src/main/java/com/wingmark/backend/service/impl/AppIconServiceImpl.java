package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.appicon.AdminAppIconResponseDto;
import com.wingmark.backend.dto.appicon.AppIconResponseDto;
import com.wingmark.backend.dto.appicon.CreateAppIconRequestDto;
import com.wingmark.backend.entity.AppIcon;
import com.wingmark.backend.exception.DuplicateResourceException;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.AppIconRepository;
import com.wingmark.backend.service.AppIconService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppIconServiceImpl implements AppIconService {

    private final AppIconRepository appIconRepository;

    @Override
    public List<AppIconResponseDto> list() {
        return ordered().stream().map(i -> new AppIconResponseDto(i.getIconId(), i.getNameEn(), i.getNameTr())).toList();
    }

    @Override
    public List<AdminAppIconResponseDto> listForAdmin() {
        return ordered().stream().map(AppIconServiceImpl::toAdminDto).toList();
    }

    @Override
    public AdminAppIconResponseDto add(CreateAppIconRequestDto request) {
        String iconId = request.iconId().trim();
        if (appIconRepository.existsByIconId(iconId)) {
            throw taken(iconId);
        }
        AppIcon icon = AppIcon.builder()
                .iconId(iconId)
                .nameEn(request.nameEn().trim())
                .nameTr(request.nameTr().trim())
                .build();
        try {
            return toAdminDto(appIconRepository.save(icon));
        } catch (DuplicateKeyException e) {
            // Two admins adding the same id at once: the unique index decides.
            throw taken(iconId);
        }
    }

    @Override
    public void delete(String iconId) {
        AppIcon icon = appIconRepository.findByIconId(iconId)
                .orElseThrow(() -> new ResourceNotFoundException("App icon not found: " + iconId));
        appIconRepository.delete(icon);
    }

    private List<AppIcon> ordered() {
        return appIconRepository.findAll().stream()
                .sorted(Comparator.comparing(AppIcon::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static DuplicateResourceException taken(String iconId) {
        return new DuplicateResourceException("An app icon with iconId '" + iconId + "' already exists");
    }

    private static AdminAppIconResponseDto toAdminDto(AppIcon i) {
        return new AdminAppIconResponseDto(i.getIconId(), i.getNameEn(), i.getNameTr(), i.getCreatedAt());
    }
}
