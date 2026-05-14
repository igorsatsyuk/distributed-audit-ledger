package lt.satsyuk.distributed.audit.event;

/**
 * Enum of all domain event types in the Distributed Audit Ledger.
 * Each value corresponds to a concrete {@link AuditEvent} subclass.
 */
public enum EventType {

    /** A user successfully authenticated. */
    USER_LOGGED_IN,

    /** A user's profile data was modified. */
    USER_PROFILE_CHANGED,

    /** A new domain entity was created. */
    ENTITY_CREATED,

    /** An existing domain entity was updated. */
    ENTITY_UPDATED,

    /** A domain entity or record was deleted. */
    DATA_DELETED
}


