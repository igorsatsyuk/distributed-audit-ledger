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
  private readonly loginUrl = `${environment.commandServiceBaseUrl}/auth/login`;

  private readonly sessionSubject = new BehaviorSubject<AuthSession | null>(this.readStoredSession());
  readonly session$ = this.sessionSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

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
    return Number.isFinite(expiresAt) && expiresAt > Date.now();
  }

  hasAnyRole(roles: UserRole[]): boolean {
    const session = this.sessionSubject.value;
    if (!session) {
      return false;
    }
    return session.roles.some((role) => roles.includes(role));
  }

  getAccessToken(): string | null {
    return this.sessionSubject.value?.accessToken ?? null;
  }

  getUsername(): string | null {
    return this.sessionSubject.value?.username ?? null;
  }

  private setSession(session: AuthSession): void {
    localStorage.setItem(AuthService.STORAGE_KEY, JSON.stringify(session));
    this.sessionSubject.next(session);
  }

  private clearSession(): void {
    localStorage.removeItem(AuthService.STORAGE_KEY);
    this.sessionSubject.next(null);
  }

  private readStoredSession(): AuthSession | null {
    const raw = localStorage.getItem(AuthService.STORAGE_KEY);
    if (!raw) {
      return null;
    }

    try {
      const parsed = JSON.parse(raw) as AuthSession;
      const expiresAt = Date.parse(parsed.expiresAt);
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
        localStorage.removeItem(AuthService.STORAGE_KEY);
        return null;
      }
      return parsed;
    } catch {
      localStorage.removeItem(AuthService.STORAGE_KEY);
      return null;
    }
  }
}

