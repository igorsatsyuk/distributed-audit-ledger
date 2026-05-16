package lt.satsyuk.distributed.audit.query.service;

public class AuditLogNotFoundException extends RuntimeException {

    public AuditLogNotFoundException(Long id) {
        super("Audit log with id=" + id + " was not found");
    }
}

