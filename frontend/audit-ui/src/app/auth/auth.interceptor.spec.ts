import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

class AuthServiceStub {
  token: string | null = null;

  getAccessToken(): string | null {
    return this.token;
  }
}

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthServiceStub;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useClass: AuthServiceStub },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService) as unknown as AuthServiceStub;
  });

  afterEach(() => httpMock.verify());

  it('adds Authorization header for protected api requests', () => {
    authService.token = 'jwt-token';

    http.get('/api/audit-logs').subscribe();

    const req = httpMock.expectOne('/api/audit-logs');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush([]);
  });

  it('does not add Authorization header when token is missing', () => {
    authService.token = null;

    http.get('/api/audit-logs').subscribe();

    const req = httpMock.expectOne('/api/audit-logs');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('does not add Authorization header for non-protected requests', () => {
    authService.token = 'jwt-token';

    http.get('/assets/config.json').subscribe();

    const req = httpMock.expectOne('/assets/config.json');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not add Authorization header for absolute urls outside configured origins', () => {
    authService.token = 'jwt-token';

    http.get('http://localhost:8084/api/audit-logs').subscribe();

    const req = httpMock.expectOne('http://localhost:8084/api/audit-logs');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('does not add Authorization header for /apiary path', () => {
    authService.token = 'jwt-token';

    http.get('/apiary').subscribe();

    const req = httpMock.expectOne('/apiary');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not add Authorization header for /commandsFoo path', () => {
    authService.token = 'jwt-token';

    http.get('/commandsFoo').subscribe();

    const req = httpMock.expectOne('/commandsFoo');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });
});

