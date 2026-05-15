package lt.satsyuk.distributed.audit.command;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that wires up an embedded Kafka broker for integration tests
 * in command-service.  Apply to any {@code @SpringBootTest} test class that
 * needs Kafka without an external broker.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"user.login.events"})
@ActiveProfiles("test")
public @interface CommandServiceIntegrationTest {
}


