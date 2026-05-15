package lt.satsyuk.distributed.audit.eventstore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.UserLoggedInEvent;
import lt.satsyuk.distributed.audit.eventstore.model.StoredAuditEvent;
import lt.satsyuk.distributed.audit.eventstore.repository.StoredAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class EventPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(EventPersistenceService.class);

    private final ObjectMapper objectMapper;
    private final EventHashService eventHashService;
    private final StoredAuditEventRepository repository;

    public EventPersistenceService(
            ObjectMapper objectMapper,
            EventHashService eventHashService,
            StoredAuditEventRepository repository
    ) {
        this.objectMapper = objectMapper;
        this.eventHashService = eventHashService;
        this.repository = repository;
    }

    public Mono<StoredAuditEvent> persist(AuditEvent event) {
        return Mono.fromCallable(() -> toEntity(event))
                .flatMap(repository::save)
                .doOnSuccess(saved -> {
                    if (saved != null) {
                        log.debug("Saved event [{}] to audit.events", saved.getEventId());
                    }
                })
                .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                        .filter(DataAccessResourceFailureException.class::isInstance))
                .onErrorResume(DuplicateKeyException.class, ignored -> {
                    log.info("Event [{}] already persisted, skipping duplicate", event.getEventId());
                    return Mono.empty();
                });
    }

    private StoredAuditEvent toEntity(AuditEvent event) throws JsonProcessingException {
        String payloadJson = objectMapper.writeValueAsString(event);
        String aggregateId = resolveAggregateId(event);

        StoredAuditEvent entity = new StoredAuditEvent();
        entity.setEventId(event.getEventId());
        entity.setAggregateId(aggregateId);
        entity.setEventType(event.getEventType().name());
        entity.setUserId(resolveUserId(event));
        entity.setPayload(Json.of(payloadJson));
        entity.setEventHash(eventHashService.sha256Hex(payloadJson));
        entity.setCreatedAt(LocalDateTime.ofInstant(resolveTimestamp(event), ZoneOffset.UTC));
        return entity;
    }

    private String resolveAggregateId(AuditEvent event) {
        if (event instanceof UserLoggedInEvent userLoggedInEvent && userLoggedInEvent.getUserId() != null) {
            return "user:" + userLoggedInEvent.getUserId();
        }
        return event.getEventId();
    }

    private String resolveUserId(AuditEvent event) {
        if (event instanceof UserLoggedInEvent userLoggedInEvent) {
            return userLoggedInEvent.getUserId();
        }
        return null;
    }

    private Instant resolveTimestamp(AuditEvent event) {
        return event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();
    }
}

