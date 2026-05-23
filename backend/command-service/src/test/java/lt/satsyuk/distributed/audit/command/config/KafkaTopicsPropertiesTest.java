package lt.satsyuk.distributed.audit.command.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicsPropertiesTest {

    @Test
    void auditEventsReturnsAuditEventsWhenSet() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setAuditEvents("audit-topic");

        assertThat(properties.getAuditEvents()).isEqualTo("audit-topic");
    }

    @Test
    void auditEventsReturnsFallbackToUserLoginEventsWhenNotSet() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setUserLoginEvents("login-topic");

        assertThat(properties.getAuditEvents()).isEqualTo("login-topic");
    }

    @Test
    void auditEventsReturnsFallbackToUserLoginEventsWhenAuditEventsIsBlank() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setAuditEvents("   ");
        properties.setUserLoginEvents("login-topic");

        assertThat(properties.getAuditEvents()).isEqualTo("login-topic");
    }

    @Test
    void userLoginEventsReturnsUserLoginEventsWhenSet() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setUserLoginEvents("login-topic");

        assertThat(properties.getUserLoginEvents()).isEqualTo("login-topic");
    }

    @Test
    void userLoginEventsReturnsFallbackToAuditEventsWhenNotSet() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setAuditEvents("audit-topic");

        assertThat(properties.getUserLoginEvents()).isEqualTo("audit-topic");
    }

    @Test
    void userLoginEventsReturnsAuditEventsWhenUserLoginEventsBlank() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setAuditEvents("audit-topic");
        properties.setUserLoginEvents("   ");

        assertThat(properties.getUserLoginEvents()).isEqualTo("audit-topic");
    }

    @Test
    void bothPropertiesSetReturnsFirstWhenBothValid() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setAuditEvents("audit-topic");
        properties.setUserLoginEvents("login-topic");

        assertThat(properties.getAuditEvents()).isEqualTo("audit-topic");
        assertThat(properties.getUserLoginEvents()).isEqualTo("login-topic");
    }

    @Test
    void neitherPropertySetReturnsNull() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();

        assertThat(properties.getAuditEvents()).isNull();
        assertThat(properties.getUserLoginEvents()).isNull();
    }

    @Test
    void bothPropertiesBlankReturnRawFallbackValues() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setAuditEvents("   ");
        properties.setUserLoginEvents("   ");

        assertThat(properties.getAuditEvents()).isEqualTo("   ");
        assertThat(properties.getUserLoginEvents()).isEqualTo("   ");
    }

    @Test
    void bothPropertiesNullReturnsNull() {
        KafkaTopicsProperties properties = new KafkaTopicsProperties();
        properties.setAuditEvents(null);
        properties.setUserLoginEvents(null);

        assertThat(properties.getAuditEvents()).isNull();
        assertThat(properties.getUserLoginEvents()).isNull();
    }
}

