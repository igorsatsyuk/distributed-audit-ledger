package lt.satsyuk.distributed.audit.query.api;

public record AuditIntegrityCheckResponse(
        Long eventId,
        String eventHash,
        BlockchainRecord blockchainRecord,
        String status
) {

    public record BlockchainRecord(
            boolean exists,
            String transactionHash,
            Long blockNumber,
            Long timestamp
    ) {
    }
}

