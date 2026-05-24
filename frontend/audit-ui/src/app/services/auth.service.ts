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
  private expiryTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly sessionSubject: BehaviorSubject<AuthSession | null>;
  readonly session$: Observable<AuthSession | null>;

  constructor(private readonly http: HttpClient) {
    this.storage = sessionStorage;
    const initialSession = this.readStoredSession();
    this.sessionSubject = new BehaviorSubject<AuthSession | null>(initialSession);
    this.session$ = this.sessionSubject.asObservable();
    this.scheduleAutoLogout(initialSession);
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

  getAuthorizationHeader(): string | null {
    if (!this.isAuthenticated()) {
      return null;
    }
    const session = this.sessionSubject.value;
    if (!session) {
      return null;
    }
    return `${session.tokenType} ${session.accessToken}`;
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
    this.scheduleAutoLogout(session);
  }

  private clearSession(): void {
    this.clearExpiryTimer();
    this.storage.removeItem(AuthService.STORAGE_KEY);
    this.sessionSubject.next(null);
  }

  private static readonly ALLOWED_ROLES: ReadonlySet<UserRole> = new Set<UserRole>(['AUDITOR', 'ADMIN', 'USER']);

  private static isAllowedRole(role: unknown): role is UserRole {
    return typeof role === 'string' && AuthService.ALLOWED_ROLES.has(role as UserRole);
  }

  private scheduleAutoLogout(session: AuthSession | null): void {
    this.clearExpiryTimer();
    if (!session) {
      return;
    }

    const expiresAt = Date.parse(session.expiresAt);
    if (!Number.isFinite(expiresAt)) {
      this.clearSession();
      return;
    }

    const delayMs = expiresAt - Date.now();
    if (delayMs <= 0) {
      this.clearSession();
      return;
    }

    this.expiryTimer = setTimeout(() => {
      this.clearSession();
    }, delayMs);
  }

  private clearExpiryTimer(): void {
    if (this.expiryTimer !== null) {
      clearTimeout(this.expiryTimer);
      this.expiryTimer = null;
    }
  }

  private readStoredSession(): AuthSession | null {
    const raw = this.storage.getItem(AuthService.STORAGE_KEY);
    if (!raw) {
      return null;
    }

    try {
      const parsed = JSON.parse(raw) as AuthSession;

      // Validate all required fields to prevent runtime errors from tampered storage
      if (
        typeof parsed.accessToken !== 'string' || !parsed.accessToken ||
        typeof parsed.tokenType !== 'string' || !parsed.tokenType ||
        typeof parsed.username !== 'string' || !parsed.username ||
        typeof parsed.expiresAt !== 'string' ||
        !Array.isArray(parsed.roles) || parsed.roles.length === 0 ||
        !parsed.roles.every(AuthService.isAllowedRole)
      ) {
        this.storage.removeItem(AuthService.STORAGE_KEY);
        return null;
      }

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

