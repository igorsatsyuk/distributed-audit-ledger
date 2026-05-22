package lt.satsyuk.distributed.audit.eventstore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.r2dbc.postgresql.codec.Json;
import lt.satsyuk.distributed.audit.event.AuditEvent;
import lt.satsyuk.distributed.audit.event.EventType;
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
    private static final int MAX_AGGREGATE_ID_LENGTH = 128;

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
        EventType eventType = requireEventType(event);
        JsonNode payloadRoot = objectMapper.valueToTree(event);
        String payloadJson = objectMapper.writeValueAsString(payloadRoot);
        String aggregateId = resolveAggregateId(eventType, payloadRoot, event.getEventId());

        StoredAuditEvent entity = new StoredAuditEvent();
        entity.setEventId(event.getEventId());
        entity.setAggregateId(normalizeAggregateId(aggregateId, event.getEventId()));
        entity.setEventType(eventType.name());
        entity.setUserId(extractStringField(payloadRoot, "userId"));
        entity.setPayload(Json.of(payloadJson));
        entity.setEventHash(eventHashService.sha256Hex(payloadJson));
        entity.setCreatedAt(LocalDateTime.ofInstant(resolveTimestamp(event), ZoneOffset.UTC));
        return entity;
    }

    private String resolveAggregateId(EventType eventType, JsonNode payloadRoot, String fallbackEventId) {
        return switch (eventType) {
            case USER_LOGGED_IN, USER_PROFILE_CHANGED -> {
                String userId = extractStringField(payloadRoot, "userId");
                yield userId != null ? "user:" + userId : fallbackEventId;
            }
            case ENTITY_CREATED, ENTITY_UPDATED, DATA_DELETED -> entityAggregateId(
                    extractStringField(payloadRoot, "entityType"),
                    extractStringField(payloadRoot, "entityId"),
                    fallbackEventId
            );
        };
    }

    private String entityAggregateId(String entityType, String entityId, String fallbackEventId) {
        if (entityType == null || entityId == null) {
            return fallbackEventId;
        }
        return "entity:" + entityType + ":" + entityId;
    }

    private String normalizeAggregateId(String aggregateId, String fallbackEventId) {
        if (aggregateId == null || aggregateId.isBlank()) {
            return fallbackEventId;
        }
        if (aggregateId.length() <= MAX_AGGREGATE_ID_LENGTH) {
            return aggregateId;
        }
        log.warn(
                "aggregate_id exceeds {} chars (actual={}); using fallback eventId [{}]",
                MAX_AGGREGATE_ID_LENGTH,
                aggregateId.length(),
                fallbackEventId
        );
        return fallbackEventId;
    }

    private String extractStringField(JsonNode payloadRoot, String fieldName) {
        String value = payloadRoot.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private EventType requireEventType(AuditEvent event) {
        if (event.getEventType() == null) {
            throw new IllegalArgumentException("AuditEvent.eventType must not be null");
        }
        return event.getEventType();
    }

    private Instant resolveTimestamp(AuditEvent event) {
        return event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();
    }
}

