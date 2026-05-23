export type UserRole = 'AUDITOR' | 'ADMIN' | 'USER';

export interface AuthLoginRequest {
  username: string;
  password: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  username: string;
  roles: UserRole[];
}

export interface AuthSession {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  username: string;
  roles: UserRole[];
}

