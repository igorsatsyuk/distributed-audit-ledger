import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

function isProtectedRequest(url: string): boolean {
  try {
    const parsedUrl = new URL(url, globalThis.location?.origin ?? 'http://localhost');
    return parsedUrl.pathname.startsWith('/api') || parsedUrl.pathname.startsWith('/commands');
  } catch {
    return url.startsWith('/api') || url.startsWith('/commands');
  }
}

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  if (!token || !isProtectedRequest(request.url)) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    }),
  );
};

