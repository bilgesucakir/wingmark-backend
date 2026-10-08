package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.StatsProperties;
import com.wingmark.backend.dto.stats.UsageStatsResponseDto;
import com.wingmark.backend.dto.stats.UsageStatsResponseDto.DayCount;
import com.wingmark.backend.dto.stats.UsageStatsResponseDto.RegionCount;
import com.wingmark.backend.dto.stats.UsageStatsResponseDto.SightingsBucket;
import com.wingmark.backend.dto.stats.UsageStatsResponseDto.SpeciesCount;
import com.wingmark.backend.dto.stats.UsageStatsResponseDto.WeekCount;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.service.UsageStatsData;
import com.wingmark.backend.service.UsageStatsData.LogRow;
import com.wingmark.backend.service.UsageStatsData.UserRow;
import com.wingmark.backend.service.UsageStatsService;
import com.wingmark.backend.util.LocalizedTextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Anonymous aggregates only; groups smaller than the minimum group size are left out so nobody can be singled out. */
@Service
@RequiredArgsConstructor
public class UsageStatsServiceImpl implements UsageStatsService {

    private static final int DAYS_SHOWN = 30;
    private static final int WEEKS_SHOWN = 12;
    private static final int TOP_SPECIES = 10;

    private final UsageStatsData data;
    private final SpeciesRepository speciesRepository;
    private final StatsProperties properties;

    @Override
    public UsageStatsResponseDto compute(Instant now) {
        List<UserRow> users = data.users();
        List<LogRow> logs = data.logs();
        int minGroup = properties.minGroupSize();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();

        Instant sevenDays = now.minus(Duration.ofDays(7));
        Instant thirtyDays = now.minus(Duration.ofDays(30));

        return new UsageStatsResponseDto(
                now,
                minGroup,
                users.size(),
                users.stream().filter(u -> u.lastLoginAt() != null && !u.lastLoginAt().isBefore(sevenDays)).count(),
                users.stream().filter(u -> u.lastLoginAt() != null && !u.lastLoginAt().isBefore(thirtyDays)).count(),
                users.stream().filter(u -> u.createdAt() != null && !u.createdAt().isBefore(thirtyDays)).count(),
                logs.size(),
                logs.stream().filter(l -> l.createdAt() != null && !l.createdAt().isBefore(sevenDays)).count(),
                logs.stream().filter(l -> l.createdAt() != null && !l.createdAt().isBefore(thirtyDays)).count(),
                logsPerDay(logs, today),
                logsPerWeek(logs, today),
                sightingsPerUser(users.size(), logs, minGroup),
                topSpecies(logs, minGroup),
                regions(logs, minGroup));
    }

    private static List<DayCount> logsPerDay(List<LogRow> logs, LocalDate today) {
        Map<LocalDate, Long> perDay = logs.stream().filter(l -> l.createdAt() != null)
                .collect(Collectors.groupingBy(l -> l.createdAt().atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
        List<DayCount> days = new ArrayList<>();
        for (int i = DAYS_SHOWN - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            days.add(new DayCount(day, perDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    private static List<WeekCount> logsPerWeek(List<LogRow> logs, LocalDate today) {
        Map<LocalDate, Long> perWeek = logs.stream().filter(l -> l.createdAt() != null)
                .collect(Collectors.groupingBy(l -> weekStart(l.createdAt().atZone(ZoneOffset.UTC).toLocalDate()), Collectors.counting()));
        LocalDate thisWeek = weekStart(today);
        List<WeekCount> weeks = new ArrayList<>();
        for (int i = WEEKS_SHOWN - 1; i >= 0; i--) {
            LocalDate start = thisWeek.minus(i, ChronoUnit.WEEKS);
            weeks.add(new WeekCount(start, perWeek.getOrDefault(start, 0L)));
        }
        return weeks;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    private static List<SightingsBucket> sightingsPerUser(int totalUsers, List<LogRow> logs, int minGroup) {
        Map<UUID, Long> perUser = logs.stream().filter(l -> l.userId() != null)
                .collect(Collectors.groupingBy(LogRow::userId, Collectors.counting()));
        long[] counts = new long[5];
        for (long n : perUser.values()) {
            counts[bucketIndex(n)]++;
        }
        counts[0] += Math.max(0, totalUsers - perUser.size());
        String[] ranges = {"0", "1", "2-5", "6-20", "21+"};
        List<SightingsBucket> buckets = new ArrayList<>();
        for (int i = 0; i < ranges.length; i++) {
            boolean hidden = counts[i] > 0 && counts[i] < minGroup;
            buckets.add(new SightingsBucket(ranges[i], hidden ? null : counts[i]));
        }
        return buckets;
    }

    private static int bucketIndex(long logs) {
        if (logs <= 0) {
            return 0;
        }
        if (logs == 1) {
            return 1;
        }
        if (logs <= 5) {
            return 2;
        }
        return logs <= 20 ? 3 : 4;
    }

    private List<SpeciesCount> topSpecies(List<LogRow> logs, int minGroup) {
        Map<UUID, Long> logsPerSpecies = new HashMap<>();
        Map<UUID, Set<UUID>> usersPerSpecies = new HashMap<>();
        for (LogRow log : logs) {
            if (log.speciesId() == null) {
                continue;
            }
            logsPerSpecies.merge(log.speciesId(), 1L, Long::sum);
            usersPerSpecies.computeIfAbsent(log.speciesId(), id -> new HashSet<>()).add(log.userId());
        }
        List<UUID> shown = logsPerSpecies.keySet().stream()
                .filter(id -> usersPerSpecies.get(id).size() >= minGroup)
                .sorted(Comparator.comparing((UUID id) -> logsPerSpecies.get(id)).reversed().thenComparing(UUID::toString))
                .limit(TOP_SPECIES).toList();
        if (shown.isEmpty()) {
            return List.of();
        }
        Map<UUID, Species> species = speciesRepository.findAllById(shown).stream()
                .collect(Collectors.toMap(Species::getId, s -> s));
        return shown.stream().filter(species::containsKey).map(id -> new SpeciesCount(
                LocalizedTextResolver.resolve(species.get(id).getCommonName(), Locale.ENGLISH),
                logsPerSpecies.get(id), usersPerSpecies.get(id).size())).toList();
    }

    private static List<RegionCount> regions(List<LogRow> logs, int minGroup) {
        Map<String, Long> logsPerCell = new HashMap<>();
        Map<String, Set<UUID>> usersPerCell = new HashMap<>();
        for (LogRow log : logs) {
            if (log.latitude() == null || log.longitude() == null) {
                continue;
            }
            String cell = (long) Math.floor(log.latitude()) + ", " + (long) Math.floor(log.longitude());
            logsPerCell.merge(cell, 1L, Long::sum);
            usersPerCell.computeIfAbsent(cell, c -> new HashSet<>()).add(log.userId());
        }
        return logsPerCell.keySet().stream()
                .filter(cell -> usersPerCell.get(cell).size() >= minGroup)
                .sorted(Comparator.comparing((String cell) -> logsPerCell.get(cell)).reversed().thenComparing(cell -> cell))
                .map(cell -> new RegionCount(cell, logsPerCell.get(cell), usersPerCell.get(cell).size())).toList();
    }
}
