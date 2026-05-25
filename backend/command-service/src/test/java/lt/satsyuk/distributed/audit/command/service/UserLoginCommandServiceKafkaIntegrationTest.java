package lt.satsyuk.distributed.audit.command.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.distributed.audit.command.CommandServiceIntegrationTest;
import lt.satsyuk.distributed.audit.contracts.command.UserLoginCommand;
import lt.satsyuk.distributed.audit.contracts.config.CanonicalObjectMapperFactory;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@CommandServiceIntegrationTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@Import(UserLoginCommandServiceKafkaIntegrationTest.KafkaProducerTestConfig.class)
class UserLoginCommandServiceKafkaIntegrationTest {

    private static final String TOPIC = "user.login.events";

    private final UserLoginCommandService userLoginCommandService;
    private final org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafkaBroker;

    UserLoginCommandServiceKafkaIntegrationTest(UserLoginCommandService userLoginCommandService,
                                                org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafkaBroker) {
        this.userLoginCommandService = userLoginCommandService;
        this.embeddedKafkaBroker = embeddedKafkaBroker;
    }

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUpConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "command-service-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(props);
        consumer = consumerFactory.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
    }

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void handleUserLoginPublishesSerializableEventToKafkaTopic() {
        UserLoginCommand command = UserLoginCommand.builder()
                .userId("user-integration")
                .build();

        var response = userLoginCommandService.handleUserLogin(command, "10.10.10.10", "JUnit-IT").block();
        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.isSuccess());
        Assertions.assertNotNull(response.getEventId());

        var kafkaRecord = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
        Assertions.assertEquals(response.getEventId(), kafkaRecord.key());
        Assertions.assertNotNull(kafkaRecord.value());
        Assertions.assertTrue(kafkaRecord.value().contains("\"eventType\":\"USER_LOGGED_IN\""));
        Assertions.assertTrue(kafkaRecord.value().contains("\"userId\":\"user-integration\""));
    }

    @TestConfiguration
    static class KafkaProducerTestConfig {

        @Bean
        ProducerFactory<String, AuditEvent> producerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
        ) {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AuditEventJsonSerializer.class);
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        KafkaTemplate<String, AuditEvent> kafkaTemplate(ProducerFactory<String, AuditEvent> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        public static class AuditEventJsonSerializer implements Serializer<AuditEvent> {
            private final ObjectMapper objectMapper = CanonicalObjectMapperFactory.create();

            @Override
            public byte[] serialize(String topic, AuditEvent data) {
                if (data == null) {
                    return null;
                }
                try {
                    return objectMapper.writeValueAsBytes(data);
                } catch (JsonProcessingException ex) {
                    throw new SerializationException("Failed to serialize AuditEvent for topic " + topic, ex);
                }
            }
        }
    }
}

