package lt.satsyuk.distributed.audit.query.repository;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import lt.satsyuk.distributed.audit.query.service.AuditLogFilter;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
public class AuditLogQueryRepositoryImpl implements AuditLogQueryRepository {

    private final DatabaseClient databaseClient;

    public AuditLogQueryRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<AuditEventRecord> findByFilter(AuditLogFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, event_id, aggregate_id, event_type, user_id, payload::text AS payload_text, event_hash, created_at
                FROM audit.events
                WHERE 1 = 1
                """);

        if (hasText(filter.userId())) {
            sql.append(" AND user_id = :userId");
        }
        if (filter.eventType() != null) {
            sql.append(" AND event_type = :eventType");
        }
        if (filter.from() != null) {
            sql.append(" AND created_at >= :fromTs");
        }
        if (filter.to() != null) {
            sql.append(" AND created_at <= :toTs");
        }
        if (hasText(filter.search())) {
            sql.append(" AND payload::text ILIKE :searchPattern ESCAPE '\\'");
        }

        sql.append(" ORDER BY created_at DESC, id DESC");
        sql.append(" LIMIT :limit OFFSET :offset");

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql.toString());

        if (hasText(filter.userId())) {
            executeSpec = executeSpec.bind("userId", filter.userId());
        }
        if (filter.eventType() != null) {
            executeSpec = executeSpec.bind("eventType", filter.eventType().name());
        }
        if (filter.from() != null) {
            executeSpec = executeSpec.bind("fromTs", LocalDateTime.ofInstant(filter.from(), ZoneOffset.UTC));
        }
        if (filter.to() != null) {
            executeSpec = executeSpec.bind("toTs", LocalDateTime.ofInstant(filter.to(), ZoneOffset.UTC));
        }
        if (hasText(filter.search())) {
            executeSpec = executeSpec.bind("searchPattern", "%" + escapeLikePattern(filter.search()) + "%");
        }
        executeSpec = executeSpec.bind("limit", filter.limit());
        executeSpec = executeSpec.bind("offset", filter.offset());

        RowsFetchSpec<AuditEventRecord> rows = executeSpec.map(this::mapRow);
        return rows.all();
    }

    @Override
    public Mono<AuditEventRecord> findById(Long id) {
        return databaseClient.sql("""
                        SELECT id, event_id, aggregate_id, event_type, user_id, payload::text AS payload_text, event_hash, created_at
                        FROM audit.events
                        WHERE id = :id
                        """)
                .bind("id", id)
                .map(this::mapRow)
                .one();
    }

    private AuditEventRecord mapRow(Row row, RowMetadata metadata) {
        AuditEventRecord eventRecord = new AuditEventRecord();
        eventRecord.setId(row.get("id", Long.class));
        eventRecord.setEventId(row.get("event_id", String.class));
        eventRecord.setAggregateId(row.get("aggregate_id", String.class));
        eventRecord.setEventType(row.get("event_type", String.class));
        eventRecord.setUserId(row.get("user_id", String.class));
        eventRecord.setPayload(row.get("payload_text", String.class));
        eventRecord.setEventHash(row.get("event_hash", String.class));
        eventRecord.setCreatedAt(row.get("created_at", LocalDateTime.class));
        return eventRecord;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
