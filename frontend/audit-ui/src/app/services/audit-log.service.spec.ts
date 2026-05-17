import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuditLogService } from './audit-log.service';

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

  afterEach(() => {
    httpMock.verify();
  });

  it('adds query params for filters', () => {
    service.getAuditLogs({ userId: 'user1', eventType: 'login' }).subscribe((result) => {
      expect(result).toEqual([]);
    });

    const request = httpMock.expectOne(
      (req) =>
        req.method === 'GET' &&
        req.url === 'http://localhost:8084/api/audit-logs' &&
        req.params.get('userId') === 'user1' &&
        req.params.get('eventType') === 'login',
    );

    request.flush([]);
  });

  it('returns local fallback data when API fails', () => {
    service.getAuditLogs({ userId: 'user1' }).subscribe((result) => {
      expect(result.length).toBe(2);
      expect(result.every((item) => item.userId === 'user1')).toBeTrue();
    });

    const request = httpMock.expectOne(
      (req) => req.method === 'GET' && req.url === 'http://localhost:8084/api/audit-logs' && req.params.get('userId') === 'user1',
    );
    request.flush('error', { status: 500, statusText: 'Server Error' });
  });
});

