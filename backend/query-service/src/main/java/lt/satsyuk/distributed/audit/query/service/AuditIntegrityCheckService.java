package lt.satsyuk.distributed.audit.query.service;

import lt.satsyuk.distributed.audit.query.api.AuditIntegrityCheckResponse;
import lt.satsyuk.distributed.audit.query.blockchain.AuditLedgerBlockchainClient;
import lt.satsyuk.distributed.audit.query.model.AuditEventRecord;
import lt.satsyuk.distributed.audit.query.repository.AuditLogQueryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuditIntegrityCheckService {

    private static final AuditIntegrityCheckResponse.BlockchainRecord MISSING_BLOCKCHAIN_RECORD =
            new AuditIntegrityCheckResponse.BlockchainRecord(false, null, null, null);

    private final AuditLogQueryRepository auditLogQueryRepository;
    private final AuditLedgerBlockchainClient blockchainClient;

    public AuditIntegrityCheckService(AuditLogQueryRepository auditLogQueryRepository,
                                      AuditLedgerBlockchainClient blockchainClient) {
        this.auditLogQueryRepository = auditLogQueryRepository;
        this.blockchainClient = blockchainClient;
    }

    public Mono<AuditIntegrityCheckResponse> checkIntegrity(Long id) {
        return auditLogQueryRepository.findById(id)
                .switchIfEmpty(Mono.error(new AuditLogNotFoundException(id)))
                .flatMap(this::resolveIntegrityResponse);
    }

    private Mono<AuditIntegrityCheckResponse> resolveIntegrityResponse(AuditEventRecord auditEventRecord) {
        if (!hasText(auditEventRecord.getEventHash())) {
            return Mono.just(toResponse(auditEventRecord, MISSING_BLOCKCHAIN_RECORD, false));
        }

        return blockchainClient.inspectEventHash(auditEventRecord.getEventHash())
                .map(blockchainRecord -> toResponse(auditEventRecord, blockchainRecord, true));
    }

    private AuditIntegrityCheckResponse toResponse(AuditEventRecord auditEventRecord,
                                                   AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord,
                                                   boolean hasDbHash) {
        String status;
        if (!hasDbHash) {
            status = "PENDING";
        } else {
            status = blockchainRecord.exists() ? "ON_CHAIN" : "MISMATCH";
        }

        String normalizedEventHash = hasText(auditEventRecord.getEventHash()) ? auditEventRecord.getEventHash().trim() : null;

        return new AuditIntegrityCheckResponse(
                auditEventRecord.getId(),
                auditEventRecord.getEventId(),
                normalizedEventHash,
                blockchainRecord,
                status
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

