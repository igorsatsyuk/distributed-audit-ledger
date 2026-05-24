import { TestBed } from '@angular/core/testing';
import { UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

class AuthServiceStub {
  authenticated = false;
  allowed = false;
  logoutCalled = false;

  isAuthenticated(): boolean {
    return this.authenticated;
  }

  hasAnyRole(): boolean {
    return this.allowed;
  }

  logout(): void {
    this.logoutCalled = true;
  }
}

describe('authGuard', () => {
  let authService: AuthServiceStub;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useClass: AuthServiceStub },
      ],
    });
    authService = TestBed.inject(AuthService) as unknown as AuthServiceStub;
  });

  it('redirects anonymous users to login', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toContain('/login');
  });

  it('redirects authenticated users without required role', () => {
    authService.authenticated = true;
    authService.allowed = false;

    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result instanceof UrlTree).toBeTrue();
    expect(authService.logoutCalled).toBeTrue();
    expect((result as UrlTree).toString()).toContain('/login');
  });

  it('allows authenticated auditor/admin users', () => {
    authService.authenticated = true;
    authService.allowed = true;

    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result).toBeTrue();
  });
});


