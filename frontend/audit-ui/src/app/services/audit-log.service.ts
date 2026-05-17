import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AuditLog, AuditLogFilters } from '../models/audit-log.model';

const QUERY_SERVICE_BASE_URL = 'http://localhost:8084';

const MOCK_AUDIT_LOGS: AuditLog[] = [
  {
    id: 1,
    eventId: '3f4f44f4-a93f-41d5-8f97-51d4c88d44ba',
    eventType: 'USER_LOGIN',
    userId: 'user1',
    createdAt: '2026-05-17T10:15:00Z',
    status: 'ON_CHAIN',
    payload: { ip: '127.0.0.1', userAgent: 'Mozilla/5.0' },
  },
  {
    id: 2,
    eventId: '31d07e73-7367-4152-a7d4-7cc7cbef6369',
    eventType: 'USER_LOGIN',
    userId: 'user2',
    createdAt: '2026-05-17T10:17:12Z',
    status: 'PENDING',
    payload: { ip: '127.0.0.2', userAgent: 'Mozilla/5.0' },
  },
  {
    id: 3,
    eventId: 'f7ef2116-e6a0-4227-b8b1-a53a5e5f1945',
    eventType: 'PASSWORD_CHANGED',
    userId: 'user1',
    createdAt: '2026-05-17T10:31:50Z',
    status: 'MISMATCH',
    payload: { reason: 'self-service reset' },
  },
];

@Injectable({
  providedIn: 'root',
})
export class AuditLogService {
  private readonly apiUrl = `${QUERY_SERVICE_BASE_URL}/api/audit-logs`;

  constructor(private readonly http: HttpClient) {}

  getAuditLogs(filters: AuditLogFilters = {}): Observable<AuditLog[]> {
    const params = this.toHttpParams(filters);

    return this.http.get<AuditLog[]>(this.apiUrl, { params }).pipe(
      catchError(() => of(MOCK_AUDIT_LOGS)),
      map((items: AuditLog[]) => this.applyClientFallbackFilters(items, filters)),
    );
  }

  private toHttpParams(filters: AuditLogFilters): HttpParams {
    let params = new HttpParams();

    if (filters.userId) {
      params = params.set('userId', filters.userId);
    }

    if (filters.eventType) {
      params = params.set('eventType', filters.eventType);
    }

    return params;
  }

  private applyClientFallbackFilters(items: AuditLog[], filters: AuditLogFilters): AuditLog[] {
    return items.filter((item) => {
      const userMatch = !filters.userId || item.userId.toLowerCase().includes(filters.userId.toLowerCase());
      const eventTypeMatch =
        !filters.eventType || item.eventType.toLowerCase().includes(filters.eventType.toLowerCase());

      return userMatch && eventTypeMatch;
    });
  }
}

