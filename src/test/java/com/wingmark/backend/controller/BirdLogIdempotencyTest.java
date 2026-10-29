package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.support.TestAuth;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/bird-logs with an Idempotency-Key never creates the same sighting twice. */
@SpringBootTest
@AutoConfigureMockMvc
class BirdLogIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BirdLogRepository birdLogRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    private record Account(String token, UUID id) {
    }

    private Account account() throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000_000L);
        String email = "idem-" + id + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "birdsong2026",
                                "username", "idem" + id, "confirmedAge13", true))))
                .andExpect(status().isCreated());
        String token = TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, email, "birdsong2026");
        return new Account(token, userRepository.findByEmailIgnoreCase(email).orElseThrow().getId());
    }


    private MvcResult create(Account account, String key, String note) throws Exception {
        var request = post("/api/bird-logs").header("Authorization", "Bearer " + account.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(note));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request).andReturn();
    }

    private String body(String note) throws Exception {
        return objectMapper.writeValueAsString(Map.of("lifeStage", "ADULT", "gender", "UNKNOWN",
                "latitude", 40.9, "longitude", 29.1, "note", note));
    }

    private String idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void theSameKeyTwiceReturnsTheSameLogWithTheSameStatusAndCreatesOneDocument() throws Exception {
        Account user = account();
        String key = UUID.randomUUID().toString();

        MvcResult first = create(user, key, "first");
        MvcResult repeat = create(user, key, "second try with another body");

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(repeat.getResponse().getStatus()).isEqualTo(201);
        assertThat(repeat.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(idOf(repeat)).isEqualTo(idOf(first));
        assertThat(objectMapper.readTree(repeat.getResponse().getContentAsString()).get("note").asText()).isEqualTo("first");
        assertThat(birdLogRepository.countByUserId(user.id())).isEqualTo(1);
        assertThat(repeat.getResponse().getContentAsString()).doesNotContain(key);
    }

    @Test
    void differentUsersMayUseTheSameKey() throws Exception {
        Account alice = account();
        Account bob = account();
        String key = UUID.randomUUID().toString();

        MvcResult a = create(alice, key, "alice's");
        MvcResult b = create(bob, key, "bob's");

        assertThat(b.getResponse().getStatus()).isEqualTo(201);
        assertThat(b.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(idOf(b)).isNotEqualTo(idOf(a));
        assertThat(birdLogRepository.countByUserId(alice.id())).isEqualTo(1);
        assertThat(birdLogRepository.countByUserId(bob.id())).isEqualTo(1);
    }

    @Test
    void withoutAKeyEveryCallCreatesANewLogAsBefore() throws Exception {
        Account user = account();

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            MvcResult result = create(user, null, "same");
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            assertThat(result.getResponse().getHeader("Idempotent-Replayed")).isNull();
            ids.add(idOf(result));
        }

        assertThat(ids).hasSize(3);
        assertThat(birdLogRepository.countByUserId(user.id())).isEqualTo(3);
    }

    @Test
    void differentKeysCreateDifferentLogs() throws Exception {
        Account user = account();

        MvcResult a = create(user, "key-number-one", "x");
        MvcResult b = create(user, "key-number-two", "x");

        assertThat(idOf(a)).isNotEqualTo(idOf(b));
        assertThat(birdLogRepository.countByUserId(user.id())).isEqualTo(2);
    }

    @Test
    void parallelCallsWithTheSameKeyCreateOneLog() throws Exception {
        Account user = account();
        String key = UUID.randomUUID().toString();
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return create(user, key, "parallel");
                };
                results.add(pool.submit(call));
            }
            start.countDown();

            Set<String> ids = new HashSet<>();
            int replays = 0;
            for (Future<MvcResult> future : results) {
                MvcResult result = future.get();
                assertThat(result.getResponse().getStatus()).isEqualTo(201);
                ids.add(idOf(result));
                if ("true".equals(result.getResponse().getHeader("Idempotent-Replayed"))) {
                    replays++;
                }
            }
            assertThat(ids).hasSize(1);
            assertThat(replays).isEqualTo(callers - 1);
            assertThat(birdLogRepository.countByUserId(user.id())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aBadKeyIsRejectedWith400AndAValidationErrorEntry() throws Exception {
        Account user = account();
        for (String bad : new String[]{"short", "a".repeat(65), "has space in it", "semi;colon-key", "", "ünicode-key-1234"}) {
            mockMvc.perform(post("/api/bird-logs").header("Authorization", "Bearer " + user.token())
                            .header("Idempotency-Key", bad).contentType(MediaType.APPLICATION_JSON).content(body("x")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.validationErrors").value(hasKey("Idempotency-Key")));
        }
        assertThat(birdLogRepository.countByUserId(user.id())).isZero();
    }

    @Test
    void keysOfTheEdgeLengthsAndCharactersAreAccepted() throws Exception {
        Account user = account();
        for (String good : new String[]{"abcdefgh", "A_b-C_d-1", "x".repeat(64), UUID.randomUUID().toString()}) {
            mockMvc.perform(post("/api/bird-logs").header("Authorization", "Bearer " + user.token())
                            .header("Idempotency-Key", good).contentType(MediaType.APPLICATION_JSON).content(body("x")))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void deletingALogFreesItsKey() throws Exception {
        Account user = account();
        String key = UUID.randomUUID().toString();
        String id = idOf(create(user, key, "once"));
        birdLogRepository.deleteById(UUID.fromString(id));

        MvcResult again = create(user, key, "again");

        assertThat(again.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(idOf(again)).isNotEqualTo(id);
    }

    @Test
    void theKeyIndexIsPartialSoLogsWithoutAKeyNeverCollide() {
        Document index = mongoTemplate.getCollection("bird_logs").listIndexes().into(new ArrayList<>()).stream()
                .filter(i -> "user_idempotency_key".equals(i.getString("name"))).findFirst().orElseThrow();

        assertThat(index.getBoolean("unique")).isTrue();
        assertThat(index.get("partialFilterExpression")).isNotNull();
        assertThat(index.get("sparse")).isNull();

        // Two logs of one user, neither with a key: allowed (a plain or sparse compound index would reject the second).
        UUID userId = UUID.randomUUID();
        birdLogRepository.save(BirdLog.builder().userId(userId).build());
        birdLogRepository.save(BirdLog.builder().userId(userId).build());
        assertThat(birdLogRepository.countByUserId(userId)).isEqualTo(2);
    }
}
