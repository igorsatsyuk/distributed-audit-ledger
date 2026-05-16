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

    private Mono<AuditIntegrityCheckResponse> resolveIntegrityResponse(AuditEventRecord record) {
        if (!hasText(record.getEventHash())) {
            return Mono.just(toResponse(record, MISSING_BLOCKCHAIN_RECORD));
        }

        return blockchainClient.inspectEventHash(record.getEventHash())
                .map(blockchainRecord -> toResponse(record, blockchainRecord));
    }

    private AuditIntegrityCheckResponse toResponse(AuditEventRecord record,
                                                   AuditIntegrityCheckResponse.BlockchainRecord blockchainRecord) {
        String status = blockchainRecord.exists() ? "OK" : "MISMATCH";
        return new AuditIntegrityCheckResponse(record.getId(), record.getEventHash(), blockchainRecord, status);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

