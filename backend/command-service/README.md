# Command Service (Issue #5)

MVP command-side service that accepts user login commands and publishes `UserLoggedInEvent` to Kafka.

## Implemented

- WebFlux endpoint: `POST /commands/user/login`
- Kafka producer to topic `user.login.events`
- Temporary in-memory event storage for accepted events
- Validation + error handling responses in `CommandResponse` format

## Run

From `backend/`:

```pwsh
mvn spring-boot:run -pl command-service -am
```

## Quick check

```pwsh
curl -X POST http://localhost:8081/commands/user/login -H "Content-Type: application/json" -d '{"userId":"user1"}'
```

## Test

From `backend/`:

```pwsh
mvn -pl command-service test
```

