# CQRS Flow

## Write Side

- Client sends command to command API.
- Command handler creates domain event.
- Event published to bus and persisted.
- Event hash is anchored in blockchain contract.

## Read Side

- Query projector subscribes to events.
- Read model gets updated in query storage.
- UI consumes query endpoints for timeline and integrity status.

