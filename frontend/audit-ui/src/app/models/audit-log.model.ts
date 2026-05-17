export type IntegrityStatus = 'ON_CHAIN' | 'MISMATCH' | 'PENDING';

export interface AuditLog {
  id: number;
  eventId: string;
  eventType: string;
  userId: string;
  createdAt: string;
  status: IntegrityStatus;
  payload: Record<string, unknown>;
}

export interface AuditLogFilters {
  userId?: string;
  eventType?: string;
}

