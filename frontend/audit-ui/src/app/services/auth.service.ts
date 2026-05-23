import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthLoginRequest, AuthSession, AuthTokenResponse, UserRole } from '../models/auth.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private static readonly STORAGE_KEY = 'dal.auth.session';
  private readonly storage: Storage;
  private readonly loginUrl = `${environment.commandServiceBaseUrl}/auth/login`;

  private readonly sessionSubject: BehaviorSubject<AuthSession | null>;
  readonly session$: Observable<AuthSession | null>;

  constructor(private readonly http: HttpClient) {
    this.storage = sessionStorage;
    this.sessionSubject = new BehaviorSubject<AuthSession | null>(this.readStoredSession());
    this.session$ = this.sessionSubject.asObservable();
  }

  login(request: AuthLoginRequest): Observable<AuthTokenResponse> {
    return this.http.post<AuthTokenResponse>(this.loginUrl, request).pipe(
      tap((response) => {
        const session: AuthSession = {
          accessToken: response.accessToken,
          tokenType: response.tokenType,
          expiresAt: response.expiresAt,
          username: response.username,
          roles: response.roles,
        };
        this.setSession(session);
      }),
    );
  }

  logout(): void {
    this.clearSession();
  }

  isAuthenticated(): boolean {
    const session = this.sessionSubject.value;
    if (!session) {
      return false;
    }
    const expiresAt = Date.parse(session.expiresAt);
    const isValid = Number.isFinite(expiresAt) && expiresAt > Date.now();
    if (!isValid) {
      this.clearSession();
    }
    return isValid;
  }

  hasAnyRole(roles: UserRole[]): boolean {
    if (!this.isAuthenticated()) {
      return false;
    }

    const session = this.sessionSubject.value;
    if (!session) {
      return false;
    }
    return session.roles.some((role) => roles.includes(role));
  }

  getAccessToken(): string | null {
    if (!this.isAuthenticated()) {
      return null;
    }
    return this.sessionSubject.value?.accessToken ?? null;
  }

  getUsername(): string | null {
    if (!this.isAuthenticated()) {
      return null;
    }
    return this.sessionSubject.value?.username ?? null;
  }

  private setSession(session: AuthSession): void {
    this.storage.setItem(AuthService.STORAGE_KEY, JSON.stringify(session));
    this.sessionSubject.next(session);
  }

  private clearSession(): void {
    this.storage.removeItem(AuthService.STORAGE_KEY);
    this.sessionSubject.next(null);
  }

  private readStoredSession(): AuthSession | null {
    const raw = this.storage.getItem(AuthService.STORAGE_KEY);
    if (!raw) {
      return null;
    }

    try {
      const parsed = JSON.parse(raw) as AuthSession;
      const expiresAt = Date.parse(parsed.expiresAt);
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
        this.storage.removeItem(AuthService.STORAGE_KEY);
        return null;
      }
      return parsed;
    } catch {
      this.storage.removeItem(AuthService.STORAGE_KEY);
      return null;
    }
  }
}

