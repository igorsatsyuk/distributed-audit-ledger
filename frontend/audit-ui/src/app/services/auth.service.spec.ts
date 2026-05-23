import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

const LOGIN_URL = `${environment.commandServiceBaseUrl}/auth/login`;

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('stores session after successful login', () => {
    service.login({ username: 'auditor', password: 'auditor123!' }).subscribe((response) => {
      expect(response.accessToken).toBe('jwt-token');
      expect(service.isAuthenticated()).toBeTrue();
      expect(service.getAccessToken()).toBe('jwt-token');
    });

    const req = httpMock.expectOne(LOGIN_URL);
    expect(req.request.method).toBe('POST');
    req.flush({
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: '2099-01-01T00:00:00Z',
      username: 'auditor',
      roles: ['AUDITOR'],
    });
  });

  it('logout clears stored session', () => {
    service.login({ username: 'admin', password: 'admin123!' }).subscribe();
    httpMock.expectOne(LOGIN_URL).flush({
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: '2099-01-01T00:00:00Z',
      username: 'admin',
      roles: ['ADMIN', 'AUDITOR', 'USER'],
    });

    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getAccessToken()).toBeNull();
    expect(service.getUsername()).toBeNull();
  });

  it('hasAnyRole returns true only when role is present in session', () => {
    service.login({ username: 'auditor', password: 'auditor123!' }).subscribe();
    httpMock.expectOne(LOGIN_URL).flush({
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: '2099-01-01T00:00:00Z',
      username: 'auditor',
      roles: ['AUDITOR'],
    });

    expect(service.hasAnyRole(['ADMIN'])).toBeFalse();
    expect(service.hasAnyRole(['AUDITOR', 'ADMIN'])).toBeTrue();
  });

  it('ignores invalid serialized session in sessionStorage', () => {
    sessionStorage.setItem('dal.auth.session', '{not-json');

    const stored = (service as any).readStoredSession();

    expect(stored).toBeNull();
    expect(sessionStorage.getItem('dal.auth.session')).toBeNull();
  });

  it('drops expired session loaded from sessionStorage', () => {
    sessionStorage.setItem(
      'dal.auth.session',
      JSON.stringify({
        accessToken: 'old-token',
        tokenType: 'Bearer',
        expiresAt: '2000-01-01T00:00:00Z',
        username: 'user',
        roles: ['USER'],
      }),
    );

    const stored = (service as any).readStoredSession();

    expect(stored).toBeNull();
    expect(sessionStorage.getItem('dal.auth.session')).toBeNull();
  });
});

