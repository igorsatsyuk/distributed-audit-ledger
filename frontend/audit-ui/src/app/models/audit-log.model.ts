export type IntegrityStatus = 'ON_CHAIN' | 'MISMATCH' | 'PENDING';

export interface AuditLog {
  id: number;
  eventId: string;
  eventType: string;
  userId: string | null;
  occurredAt: string;
  eventDataJson: string;
  eventHash?: string;
  integrityStatus: IntegrityStatus;
}

export interface BlockchainRecord {
  exists: boolean;
  transactionHash?: string;
  blockNumber?: number;
  timestamp?: number;
}

export interface IntegrityCheckResponse {
  auditLogId: number;
  eventId: string;
  eventHash?: string;
  blockchainRecord: BlockchainRecord;
  status: IntegrityStatus;
}

export interface AuditLogFilters {
  userId?: string;
  eventType?: string;
  from?: string;
  to?: string;
  limit?: number;
  offset?: number;
}
