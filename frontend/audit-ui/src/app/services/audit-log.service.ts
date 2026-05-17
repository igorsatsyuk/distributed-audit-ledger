import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { AuditLog, AuditLogFilters } from '../models/audit-log.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuditLogService {
  private readonly apiUrl = `${environment.queryServiceBaseUrl}/api/audit-logs`;

  constructor(private readonly http: HttpClient) {}

  getAuditLogs(filters: AuditLogFilters = {}): Observable<AuditLog[]> {
    const params = this.toHttpParams(filters);

    return this.http
      .get<AuditLog[]>(this.apiUrl, { params })
      .pipe(map((items: AuditLog[]) => this.applyClientFallbackFilters(items, filters)));
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

