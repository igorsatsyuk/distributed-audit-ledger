# Event Store Service

`event-store-service` реализует Issue #6: читает события из Kafka (`user.login.events`) и сохраняет их в PostgreSQL (`audit.events`).

## Что внутри

- Kafka consumer (`UserLoginEventConsumer`)
- Преобразование `AuditEvent` -> `StoredAuditEvent`
- Расчет `SHA-256` хэша payload (`event_hash`)
- Сохранение через Spring Data R2DBC
- Flyway миграции в `src/main/resources/db/migration`

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

