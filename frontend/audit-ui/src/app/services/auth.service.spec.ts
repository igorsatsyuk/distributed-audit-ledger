import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
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

  it('hasAnyRole returns false and clears expired session', () => {
    service.login({ username: 'auditor', password: 'auditor123!' }).subscribe();
    httpMock.expectOne(LOGIN_URL).flush({
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: '2000-01-01T00:00:00Z',
      username: 'auditor',
      roles: ['AUDITOR'],
    });

    expect(service.hasAnyRole(['AUDITOR'])).toBeFalse();
    expect(sessionStorage.getItem('dal.auth.session')).toBeNull();
  });

  it('getUsername returns null for expired session', () => {
    service.login({ username: 'auditor', password: 'auditor123!' }).subscribe();
    httpMock.expectOne(LOGIN_URL).flush({
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: '2000-01-01T00:00:00Z',
      username: 'auditor',
      roles: ['AUDITOR'],
    });

    expect(service.getUsername()).toBeNull();
    expect(sessionStorage.getItem('dal.auth.session')).toBeNull();
  });

  it('getAuthorizationHeader returns tokenType and token for valid session', () => {
    service.login({ username: 'auditor', password: 'auditor123!' }).subscribe();
    httpMock.expectOne(LOGIN_URL).flush({
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: '2099-01-01T00:00:00Z',
      username: 'auditor',
      roles: ['AUDITOR'],
    });

    expect(service.getAuthorizationHeader()).toBe('Bearer jwt-token');
  });

  it('getAuthorizationHeader returns null when not authenticated', () => {
    expect(service.getAuthorizationHeader()).toBeNull();
  });

  it('drops session with invalid roles from sessionStorage', () => {
    sessionStorage.setItem(
      'dal.auth.session',
      JSON.stringify({
        accessToken: 'old-token',
        tokenType: 'Bearer',
        expiresAt: '2099-01-01T00:00:00Z',
        username: 'user',
        roles: ['SUPERADMIN'],
      }),
    );

    const stored = (service as any).readStoredSession();

    expect(stored).toBeNull();
    expect(sessionStorage.getItem('dal.auth.session')).toBeNull();
  });

  it('auto-clears session after token expiration time passes', fakeAsync(() => {
    let latestUsername: string | null = null;
    service.session$.subscribe((session) => {
      latestUsername = session?.username ?? null;
    });

    service.login({ username: 'auditor', password: 'auditor123!' }).subscribe();
    httpMock.expectOne(LOGIN_URL).flush({
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 40).toISOString(),
      username: 'auditor',
      roles: ['AUDITOR'],
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(latestUsername === 'auditor').toBeTrue();

    tick(60);

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getAuthorizationHeader()).toBeNull();
    expect(sessionStorage.getItem('dal.auth.session')).toBeNull();
    expect(latestUsername).toBeNull();
  }));
});

