package lt.satsyuk.distributed.audit.eventstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import reactor.core.publisher.Mono;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
class EventStoreKafkaToPostgresIntegrationTest {

    private static final String TOPIC = "user.login.events";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("audit_ledger")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:postgresql://%s:%d/%s",
                POSTGRES.getHost(),
                POSTGRES.getFirstMappedPort(),
                POSTGRES.getDatabaseName()
        ));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void prepareInfrastructure() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS audit");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS audit.events (
                        id BIGSERIAL PRIMARY KEY,
                        event_id VARCHAR(36) NOT NULL UNIQUE,
                        aggregate_id VARCHAR(128) NOT NULL,
                        event_type VARCHAR(128) NOT NULL,
                        user_id VARCHAR(255),
                        payload JSONB NOT NULL,
                        event_hash VARCHAR(64),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }

        Map<String, Object> adminProps = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()
        );
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            adminClient.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        }
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void userLoginEventIsPersistedToPostgresFromKafkaTopic() throws Exception {
        UserLoggedInEvent event = UserLoggedInEvent.of("user-e2e-1", "203.0.113.50", "JUnit-IT");
        String payload = objectMapper.writeValueAsString(event);

        kafkaTemplate.send(TOPIC, event.getEventId(), payload).get();

        Optional<PersistedEventRow> persisted = waitForEvent(event.getEventId());

        assertTrue(persisted.isPresent(), "Expected event to be persisted in audit.events");
        PersistedEventRow stored = persisted.get();
        assertEquals(event.getEventId(), stored.eventId());
        assertEquals("user:user-e2e-1", stored.aggregateId());
        assertEquals("USER_LOGGED_IN", stored.eventType());
        assertEquals("user-e2e-1", stored.userId());
        assertNotNull(stored.payload());
        assertEquals("user-e2e-1", objectMapper.readTree(stored.payload()).path("userId").asText());
        assertNotNull(stored.eventHash());
        assertEquals(64, stored.eventHash().length());
    }

    private Optional<PersistedEventRow> waitForEvent(String eventId) {
        for (int i = 0; i < 60; i++) {
            Optional<PersistedEventRow> row = readEventRow(eventId);
            if (row.isPresent()) {
                return row;
            }
            Mono.delay(Duration.ofMillis(200)).block();
        }
        return Optional.empty();
    }

    private Optional<PersistedEventRow> readEventRow(String eventId) {
        String sql = "SELECT event_id, aggregate_id, event_type, user_id, payload::text AS payload_text, event_hash "
                + "FROM audit.events WHERE event_id = ?";

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PersistedEventRow(
                        rs.getString("event_id"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("user_id"),
                        rs.getString("payload_text"),
                        rs.getString("event_hash")
                ));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private record PersistedEventRow(
            String eventId,
            String aggregateId,
            String eventType,
            String userId,
            String payload,
            String eventHash
    ) {
    }

    @TestConfiguration
    static class KafkaProducerTestConfig {

        @Bean
        ProducerFactory<String, String> producerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }
    }
}

