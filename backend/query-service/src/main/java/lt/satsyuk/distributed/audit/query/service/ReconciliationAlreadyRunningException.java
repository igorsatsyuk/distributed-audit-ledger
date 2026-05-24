package lt.satsyuk.distributed.audit.query.service;

public class ReconciliationAlreadyRunningException extends RuntimeException {

    public ReconciliationAlreadyRunningException() {
        super("Reconciliation run is already in progress");
    }
}

