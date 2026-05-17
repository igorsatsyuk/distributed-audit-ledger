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

export interface AuditLogFilters {
  userId?: string;
  eventType?: string;
}

