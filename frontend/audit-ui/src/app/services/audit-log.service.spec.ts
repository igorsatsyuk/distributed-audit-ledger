import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuditLogService } from './audit-log.service';
import { environment } from '../../environments/environment';
import { IntegrityCheckResponse } from '../models/audit-log.model';

const BASE = `${environment.queryServiceBaseUrl}/api/audit-logs`;

describe('AuditLogService', () => {
  let service: AuditLogService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AuditLogService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── getAuditLogs ───────────────────────────────────────────────────────────

  it('adds userId and eventType query params', () => {
    service.getAuditLogs({ userId: 'user1', eventType: 'login' }).subscribe((result) => {
      expect(result).toEqual([]);
    });

    const req = httpMock.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === BASE &&
        r.params.get('userId') === 'user1' &&
        r.params.get('eventType') === 'login',
    );
    req.flush([]);
  });

  it('adds limit and offset query params', () => {
    service.getAuditLogs({ limit: 20, offset: 40 }).subscribe((result) => {
      expect(result).toBeDefined();
    });

    const req = httpMock.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === BASE &&
        r.params.get('limit') === '20' &&
        r.params.get('offset') === '40',
    );
    req.flush([]);
  });

  it('adds from and to query params', () => {
    service.getAuditLogs({ from: '2026-01-01T00:00:00Z', to: '2026-12-31T23:59:59Z' }).subscribe((result) => {
      expect(result).toBeDefined();
    });

    const req = httpMock.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url === BASE &&
        r.params.get('from') === '2026-01-01T00:00:00Z' &&
        r.params.get('to') === '2026-12-31T23:59:59Z',
    );
    req.flush([]);
  });

  it('does not add params for undefined filter fields', () => {
    service.getAuditLogs({}).subscribe();

    const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === BASE);
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('propagates API error when getAuditLogs fails', () => {
    service.getAuditLogs({ userId: 'user1' }).subscribe({
      next: () => fail('Expected observable to error'),
      error: (error: unknown) => expect(error).toBeTruthy(),
    });

    const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === BASE);
    req.flush('error', { status: 500, statusText: 'Server Error' });
  });

  // ── getAuditLogById ────────────────────────────────────────────────────────

  it('getAuditLogById sends GET to /api/audit-logs/42', () => {
    service.getAuditLogById(42).subscribe((result) => {
      expect(result).toBeTruthy();
    });

    const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${BASE}/42`);
    req.flush({ id: 42, eventId: 'e-42', eventType: 'USER_LOGGED_IN', userId: 'u1', occurredAt: '2026-05-17T10:00:00Z', eventDataJson: '{}', integrityStatus: 'PENDING' });
  });

  it('getAuditLogById propagates 404 error', () => {
    service.getAuditLogById(999).subscribe({
      next: () => fail('Expected error'),
      error: (err: unknown) => expect(err).toBeTruthy(),
    });

    httpMock.expectOne(`${BASE}/999`).flush('Not found', { status: 404, statusText: 'Not Found' });
  });

  // ── checkIntegrity ─────────────────────────────────────────────────────────

  it('checkIntegrity sends GET to /api/audit-logs/1/integrity-check', () => {
    const mockResponse: IntegrityCheckResponse = {
      auditLogId: 1,
      eventId: 'event-1',
      eventHash: 'abc123',
      blockchainRecord: { exists: true, transactionHash: '0xdeadbeef', blockNumber: 100, timestamp: 1715774400 },
      status: 'ON_CHAIN',
    };

    service.checkIntegrity(1).subscribe((result) => {
      expect(result.status).toBe('ON_CHAIN');
      expect(result.blockchainRecord.exists).toBeTrue();
      expect(result.blockchainRecord.transactionHash).toBe('0xdeadbeef');
    });

    const req = httpMock.expectOne(`${BASE}/1/integrity-check`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('checkIntegrity returns PENDING when hash absent', () => {
    const pending: IntegrityCheckResponse = {
      auditLogId: 2,
      eventId: 'event-2',
      blockchainRecord: { exists: false },
      status: 'PENDING',
    };

    service.checkIntegrity(2).subscribe((result) => {
      expect(result.status).toBe('PENDING');
      expect(result.eventHash).toBeUndefined();
    });

    httpMock.expectOne(`${BASE}/2/integrity-check`).flush(pending);
  });

  it('checkIntegrity propagates 503 error', () => {
    service.checkIntegrity(3).subscribe({
      next: () => fail('Expected error'),
      error: (err: unknown) => expect(err).toBeTruthy(),
    });

    httpMock.expectOne(`${BASE}/3/integrity-check`).flush('Blockchain unavailable', {
      status: 503,
      statusText: 'Service Unavailable',
    });
  });
});
