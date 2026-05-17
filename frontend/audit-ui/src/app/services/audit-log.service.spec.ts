import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuditLogService } from './audit-log.service';
import { environment } from '../../environments/environment';

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
        req.url === `${environment.queryServiceBaseUrl}/api/audit-logs` &&
        req.params.get('userId') === 'user1' &&
        req.params.get('eventType') === 'login',
    );

    request.flush([]);
  });

  it('propagates API error when request fails', () => {
    service.getAuditLogs({ userId: 'user1' }).subscribe({
      next: () => {
        fail('Expected observable to error');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
      },
    });

    const request = httpMock.expectOne(
      (req) =>
        req.method === 'GET' &&
        req.url === `${environment.queryServiceBaseUrl}/api/audit-logs` &&
        req.params.get('userId') === 'user1',
    );
    request.flush('error', { status: 500, statusText: 'Server Error' });
  });
});

