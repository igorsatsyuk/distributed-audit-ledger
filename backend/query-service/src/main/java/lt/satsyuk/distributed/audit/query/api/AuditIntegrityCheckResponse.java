package lt.satsyuk.distributed.audit.query.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for the integrity-check endpoint.
 *
 * <p>Nullable fields ({@code eventHash} when hash is absent, nullable {@code BlockchainRecord}
 * sub-fields when the record is not found on-chain) are excluded from JSON output to keep
 * the payload clean and consistent with other query-service DTOs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditIntegrityCheckResponse(
        Long auditLogId,
        String eventId,
        String eventHash,
        BlockchainRecord blockchainRecord,
        String status
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BlockchainRecord(
            boolean exists,
            String transactionHash,
            Long blockNumber,
            Long timestamp
    ) {
    }
}

