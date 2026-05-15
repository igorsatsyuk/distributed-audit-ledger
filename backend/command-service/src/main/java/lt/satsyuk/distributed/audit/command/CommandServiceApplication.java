package lt.satsyuk.distributed.audit.command;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Command Service — entry point.
 *
 * <p>Responsible for accepting user action commands via REST and publishing
 * corresponding domain events to Kafka. Runs on port 8081.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CommandServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommandServiceApplication.class, args);
    }
}

