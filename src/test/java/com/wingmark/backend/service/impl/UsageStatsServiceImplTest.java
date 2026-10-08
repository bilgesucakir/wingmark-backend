package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.StatsProperties;
import com.wingmark.backend.dto.stats.UsageStatsResponseDto;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.service.UsageStatsData;
import com.wingmark.backend.service.UsageStatsData.LogRow;
import com.wingmark.backend.service.UsageStatsData.UserRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageStatsServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z"); // a Wednesday

    @Mock
    private UsageStatsData data;
    @Mock
    private SpeciesRepository speciesRepository;

    private UsageStatsServiceImpl service(int minGroupSize) {
        return new UsageStatsServiceImpl(data, speciesRepository, new StatsProperties(minGroupSize));
    }

    private static Instant daysAgo(long days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private static LogRow log(UUID user, UUID species, Instant at, double lat, double lng) {
        return new LogRow(user, species, at, lat, lng);
    }

    @Test
    void countsUsersActivityAndLogsInTheirWindows() {
        when(data.users()).thenReturn(List.of(
                new UserRow(daysAgo(2), daysAgo(1)),      // new and active
                new UserRow(daysAgo(20), daysAgo(15)),    // active in 30 days
                new UserRow(daysAgo(200), null)));        // never active
        UUID user = UUID.randomUUID();
        when(data.logs()).thenReturn(List.of(
                log(user, null, daysAgo(1), 40.5, 29.5), log(user, null, daysAgo(10), 40.5, 29.5), log(user, null, daysAgo(100), 40.5, 29.5)));

        UsageStatsResponseDto stats = service(5).compute(NOW);

        assertThat(stats.totalUsers()).isEqualTo(3);
        assertThat(stats.activeUsersLast7Days()).isEqualTo(1);
        assertThat(stats.activeUsersLast30Days()).isEqualTo(2);
        assertThat(stats.newUsersLast30Days()).isEqualTo(2);
        assertThat(stats.totalLogs()).isEqualTo(3);
        assertThat(stats.logsLast7Days()).isEqualTo(1);
        assertThat(stats.logsLast30Days()).isEqualTo(2);
    }

    @Test
    void logsPerDayCoversTheLast30DaysIncludingEmptyDays() {
        when(data.users()).thenReturn(List.of());
        UUID user = UUID.randomUUID();
        when(data.logs()).thenReturn(List.of(log(user, null, NOW, 0.0, 0.0), log(user, null, NOW.minusSeconds(60), 0.0, 0.0),
                log(user, null, daysAgo(2), 0.0, 0.0)));

        List<UsageStatsResponseDto.DayCount> days = service(5).compute(NOW).logsPerDay();

        assertThat(days).hasSize(30);
        assertThat(days.get(29).date()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(days.get(29).logs()).isEqualTo(2);
        assertThat(days.get(27).logs()).isEqualTo(1);
        assertThat(days.get(28).logs()).isZero();
    }

    @Test
    void logsPerWeekGroupsByMondayOverTwelveWeeks() {
        when(data.users()).thenReturn(List.of());
        UUID user = UUID.randomUUID();
        // Wed 7 Oct (week of 5 Oct); Sun 4 Oct and Fri 2 Oct (week of 28 Sep); Sat 26 Sep... daysAgo(11) is Sat 26 Sep (week of 21 Sep).
        when(data.logs()).thenReturn(List.of(log(user, null, NOW, 0.0, 0.0), log(user, null, daysAgo(3), 0.0, 0.0),
                log(user, null, daysAgo(5), 0.0, 0.0), log(user, null, daysAgo(11), 0.0, 0.0)));

        List<UsageStatsResponseDto.WeekCount> weeks = service(5).compute(NOW).logsPerWeek();

        assertThat(weeks).hasSize(12);
        assertThat(weeks.get(11).weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(weeks.get(11).logs()).isEqualTo(1);
        assertThat(weeks.get(10).weekStart()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(weeks.get(10).logs()).isEqualTo(2);
        assertThat(weeks.get(9).weekStart()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(weeks.get(9).logs()).isEqualTo(1);
    }

    @Test
    void sightingsPerUserBucketsHideGroupsSmallerThanTheMinimum() {
        List<LogRow> logs = new ArrayList<>();
        List<UserRow> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) {          // six users with exactly one log
            UUID user = UUID.randomUUID();
            users.add(new UserRow(daysAgo(5), null));
            logs.add(log(user, null, daysAgo(1), 0.0, 0.0));
        }
        UUID heavy = UUID.randomUUID();         // a single user with 30 logs: must not be visible
        users.add(new UserRow(daysAgo(5), null));
        for (int i = 0; i < 30; i++) {
            logs.add(log(heavy, null, daysAgo(1), 0.0, 0.0));
        }
        users.add(new UserRow(daysAgo(5), null)); // and one user with no logs
        when(data.users()).thenReturn(users);
        when(data.logs()).thenReturn(logs);

        Map<String, Long> buckets = new java.util.LinkedHashMap<>();
        service(5).compute(NOW).sightingsPerUser().forEach(b -> buckets.put(b.range(), b.users()));

        assertThat(buckets).containsEntry("1", 6L);
        assertThat(buckets).containsEntry("21+", null);
        assertThat(buckets).containsEntry("0", null);
        assertThat(buckets).containsEntry("2-5", 0L);
    }

    @Test
    void topSpeciesListsOnlySpeciesLoggedByEnoughDifferentUsers() {
        UUID popular = UUID.randomUUID();
        UUID rare = UUID.randomUUID();
        List<LogRow> logs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            logs.add(log(UUID.randomUUID(), popular, daysAgo(1), 0.0, 0.0));
        }
        UUID single = UUID.randomUUID();       // one person logged the rare species many times
        for (int i = 0; i < 20; i++) {
            logs.add(log(single, rare, daysAgo(1), 0.0, 0.0));
        }
        when(data.users()).thenReturn(List.of());
        when(data.logs()).thenReturn(logs);
        when(speciesRepository.findAllById(List.of(popular))).thenReturn(List.of(
                Species.builder().id(popular).commonName(Map.of("en", "House Sparrow", "tr", "Serçe")).scientificName("Passer domesticus").build()));

        List<UsageStatsResponseDto.SpeciesCount> top = service(5).compute(NOW).topSpecies();

        assertThat(top).singleElement().satisfies(s -> {
            assertThat(s.speciesName()).isEqualTo("House Sparrow");
            assertThat(s.logs()).isEqualTo(5);
            assertThat(s.users()).isEqualTo(5);
        });
    }

    @Test
    void regionsAreWholeDegreeCellsListedOnlyWithEnoughUsers() {
        List<LogRow> logs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            logs.add(log(UUID.randomUUID(), null, daysAgo(1), 40.93977 + i * 0.01, 29.11957)); // all in cell 40, 29
        }
        logs.add(log(UUID.randomUUID(), null, daysAgo(1), 51.5, -0.12));                       // one user in London
        logs.add(new LogRow(UUID.randomUUID(), null, daysAgo(1), null, null));                 // no location
        when(data.users()).thenReturn(List.of());
        when(data.logs()).thenReturn(logs);

        List<UsageStatsResponseDto.RegionCount> regions = service(5).compute(NOW).regions();

        assertThat(regions).singleElement().satisfies(r -> {
            assertThat(r.cell()).isEqualTo("40, 29");
            assertThat(r.logs()).isEqualTo(5);
            assertThat(r.users()).isEqualTo(5);
        });
    }

    @Test
    void theResultCarriesNoPersonalFields() {
        // The DTOs expose only counts, dates, names of species and coarse cells: no id, email or exact coordinate field exists.
        List<String> fields = new ArrayList<>();
        for (Class<?> type : new Class<?>[]{UsageStatsResponseDto.class, UsageStatsResponseDto.DayCount.class,
                UsageStatsResponseDto.WeekCount.class, UsageStatsResponseDto.SightingsBucket.class,
                UsageStatsResponseDto.SpeciesCount.class, UsageStatsResponseDto.RegionCount.class}) {
            for (var component : type.getRecordComponents()) {
                fields.add(component.getName().toLowerCase());
            }
        }

        assertThat(fields).noneMatch(f -> f.contains("email") || f.contains("userid") || f.contains("latitude")
                || f.contains("longitude") || f.contains("device") || f.contains("name") && !f.equals("speciesname"));
        verify(speciesRepository, never()).findAllById(any());
    }
}
