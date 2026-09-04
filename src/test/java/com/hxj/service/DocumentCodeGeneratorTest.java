package com.hxj.service;
import com.hxj.entity.BusinessType;
import com.hxj.repository.JdbcDocumentSequenceAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:document-code;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcDocumentSequenceAllocator.class)
class DocumentCodeGeneratorTest {

    @Autowired
    private JdbcDocumentSequenceAllocator sequenceAllocator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DocumentCodeGenerator generator;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS doc_seq");
        jdbcTemplate.execute("""
                CREATE TABLE doc_seq (
                    seq_date VARCHAR(8) NOT NULL PRIMARY KEY,
                    seq_value BIGINT NOT NULL DEFAULT 0
                )
                """);
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-08-29T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        generator = new DocumentCodeGenerator(sequenceAllocator, fixedClock);
    }

    @Test
    void shouldGeneratePrefixDateAndFourDigitSequence() {
        assertThat(generator.generate(BusinessType.DAILY_PAYMENT)).isEqualTo("BX202608290001");
        assertThat(generator.generate(BusinessType.BUSINESS_PAYMENT)).isEqualTo("FK202608290002");
        assertThat(generator.generate(BusinessType.SEAL_APPLICATION)).isEqualTo("YY202608290003");
    }

    @Test
    void shouldRemainUniqueWhenGeneratedConcurrently() throws Exception {
        int count = 80;
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            BusinessType[] types = BusinessType.values();
            for (int index = 0; index < count; index++) {
                BusinessType type = types[index % types.length];
                tasks.add(() -> generator.generate(type));
            }

            List<Future<String>> futures = executor.invokeAll(tasks);
            Set<String> codes = new HashSet<>();
            for (Future<String> future : futures) {
                codes.add(future.get());
            }

            assertThat(codes).hasSize(count);
            assertThat(codes).allMatch(code -> code.matches("(BX|FK|YY)20260829\\d{4}"));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT seq_value FROM doc_seq WHERE seq_date = '20260829'", Long.class))
                    .isEqualTo((long) count);
        } finally {
            executor.shutdownNow();
        }
    }
}