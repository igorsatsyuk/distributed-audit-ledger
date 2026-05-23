import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AuthLoginComponent } from './auth-login.component';
import { AuthService } from '../../services/auth.service';

class AuthServiceStub {
  shouldFail = false;

  login() {
    if (this.shouldFail) {
      return throwError(() => new Error('login failed'));
    }
    return of({
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: '2099-01-01T00:00:00Z',
      username: 'auditor',
      roles: ['AUDITOR'],
    });
  }
}

class RouterStub {
  navigateByUrl = jasmine.createSpy('navigateByUrl');
}

describe('AuthLoginComponent', () => {
  let authService: AuthServiceStub;
  let router: RouterStub;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthLoginComponent],
      providers: [
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: Router, useClass: RouterStub },
      ],
    }).compileComponents();

    authService = TestBed.inject(AuthService) as unknown as AuthServiceStub;
    router = TestBed.inject(Router) as unknown as RouterStub;
  });

  it('marks fields touched and exits when form is invalid', () => {
    const fixture = TestBed.createComponent(AuthLoginComponent);
    const component = fixture.componentInstance;

    spyOn(component.form, 'markAllAsTouched');
    component.submit();

    expect(component.form.markAllAsTouched).toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('navigates to dashboard on successful login', () => {
    const fixture = TestBed.createComponent(AuthLoginComponent);
    const component = fixture.componentInstance;

    component.form.setValue({ username: 'auditor', password: 'auditor123!' });
    component.submit();

    expect(component.errorMessage()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    expect(component.loading()).toBeFalse();
  });

  it('sets error message when login fails', () => {
    const fixture = TestBed.createComponent(AuthLoginComponent);
    const component = fixture.componentInstance;
    authService.shouldFail = true;

    component.form.setValue({ username: 'auditor', password: 'bad' });
    component.submit();

    expect(component.errorMessage()).toBe('Login failed. Check username/password and try again.');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
  });
});

