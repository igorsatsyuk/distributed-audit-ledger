import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditLog, AuditLogFilters, IntegrityCheckResponse } from '../models/audit-log.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuditLogService {
  private readonly apiUrl = `${environment.queryServiceBaseUrl}/api/audit-logs`;

  constructor(private readonly http: HttpClient) {}

  getAuditLogs(filters: AuditLogFilters = {}): Observable<AuditLog[]> {
    const params = this.toHttpParams(filters);
    return this.http.get<AuditLog[]>(this.apiUrl, { params });
  }

  getAuditLogById(id: number): Observable<AuditLog> {
    return this.http.get<AuditLog>(`${this.apiUrl}/${id}`);
  }

  checkIntegrity(id: number): Observable<IntegrityCheckResponse> {
    return this.http.get<IntegrityCheckResponse>(`${this.apiUrl}/${id}/integrity-check`);
  }

  private toHttpParams(filters: AuditLogFilters): HttpParams {
    let params = new HttpParams();

    if (filters.userId) {
      params = params.set('userId', filters.userId);
    }
    if (filters.eventType) {
      params = params.set('eventType', filters.eventType);
    }
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    if (filters.limit != null) {
      params = params.set('limit', filters.limit);
    }
    if (filters.offset != null) {
      params = params.set('offset', filters.offset);
    }

    return params;
  }
}
