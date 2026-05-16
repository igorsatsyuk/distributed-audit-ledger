# Event Hash Canonical Migration

Этот runbook нужен для окружений, где в `audit.events` уже есть исторические строки,
созданные до перехода на canonical JSON-сериализацию.

## Почему это важно

`event_hash` вычисляется как `SHA-256(payload_json)`. При смене правил сериализации
(порядок полей, формат `Instant`) один и тот же логический event может дать другой hash.
Без миграции historical rows проверка `DB event_hash == on-chain hash` будет расходиться.

## Кого это касается

- **Новые окружения без данных**: миграция не требуется.
- **Окружения с существующими данными**: выполните шаги ниже до включения строгой сверки.

## План миграции

1. **Остановить writers** (`event-store-service`, `audit-writer-service`), чтобы заморозить записи.
2. **Сделать backup** таблицы `audit.events`.
3. **Пересчитать canonical hash** для каждой строки по фактическому `payload` и обновить `event_hash`.
4. **Синхронизировать с blockchain (`AuditLedger`)**:
   - для строк, которые еще не были заякорены on-chain, выполнить backfill через `appendAuditRecord(canonicalHash, timestamp, eventType, sourceAddress)`:
     * `canonicalHash` — SHA-256 из пересчитанного payload, переданный как `bytes32` (`0x` + 64 hex chars, либо те же 32 raw bytes при вызове через Web3j);
     * `timestamp` — Unix epoch seconds из `audit.events.created_at`;
     * `eventType` — имя типа события (e.g., `USER_LOGGED_IN`) из `audit.events.event_type`;
     * `sourceAddress` — адрес writer'а (owner контракта AuditLedger);
   - для строк, уже заякоренных legacy hash, зафиксировать стратегию: либо отдельная историческая зона без строгого `DB == chain`, либо пересоздание окружения/ledger с canonical hash;
   - убедиться, что для целевого набора данных нет "только DB" записей без on-chain отражения.
5. **Проверить консистентность**:
   - нет `NULL` в `event_hash`;
   - hash в lowercase hex, длина 64;
   - выборочно проверить несколько событий end-to-end (DB -> blockchain).
6. **Запустить сервисы обратно** и включать строгую сверку только после успешной проверки.

## Минимальные SQL-проверки

```sql
-- 1) Пустые hash (должно быть 0)
SELECT count(*) AS null_hashes
FROM audit.events
WHERE event_hash IS NULL;

-- 2) Невалидный формат (должно быть 0)
SELECT count(*) AS invalid_format
FROM audit.events
WHERE event_hash IS NULL
   OR length(event_hash) <> 64
   OR event_hash !~ '^[0-9a-f]{64}$';
```

## Примечание по backfill

Backfill должен использовать тот же canonical mapper, что и сервисы (`CanonicalObjectMapperFactory`).
Это гарантирует одинаковый digest в `event-store-service` и `audit-writer-service`.

