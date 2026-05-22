# Event Store Service

`event-store-service` реализует Issue #6: читает события из Kafka (`user.login.events`) и сохраняет их в PostgreSQL (`audit.events`).

## Что внутри

- Kafka consumer (`AuditEventConsumer`)
- Преобразование `AuditEvent` -> `StoredAuditEvent`
- Расчет `SHA-256` хэша payload (`event_hash`)
- Сохранение через Spring Data R2DBC
- Обработка poison records: 3 ретрая и skip (без публикации в DLT)
- Flyway миграции в `src/main/resources/db/migration`

## Совместимость `event_hash`

- Для новых записей сериализация payload выполняется canonical `ObjectMapper`, общий с `audit-writer-service`.
- Исторические записи, созданные до canonical-схемы, могут иметь другой `event_hash` при том же payload.
- Перед включением строгой сверки `audit.events.event_hash` с on-chain хэшами выполните runbook:
  `../../docs/EVENT_HASH_CANONICAL_MIGRATION.md`.

## Быстрый запуск

Из каталога `backend/`:

```bash
mvn spring-boot:run -pl event-store-service -am
```

## Тесты

Из каталога `backend/`:

```bash
mvn -pl event-store-service test
```

Интеграционный сценарий Kafka -> PostgreSQL покрыт тестом
`EventStoreKafkaToPostgresIntegrationTest` на Testcontainers.
Если Docker недоступен, этот тест будет автоматически пропущен.

