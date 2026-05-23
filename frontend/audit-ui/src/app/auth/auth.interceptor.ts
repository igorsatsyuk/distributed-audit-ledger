import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';

function isApiOrCommandsPath(pathname: string): boolean {
  return (
    pathname === '/api' ||
    pathname.startsWith('/api/') ||
    pathname === '/commands' ||
    pathname.startsWith('/commands/')
  );
}

function isProtectedRequest(url: string): boolean {
  try {
    const parsedUrl = new URL(url, globalThis.location?.origin ?? 'http://localhost');
    const currentOrigin = globalThis.location?.origin ?? 'http://localhost';

    // Only attach JWT for same-origin requests or to configured service URLs
    if (parsedUrl.origin !== currentOrigin) {
      const configuredUrls = [
        environment.queryServiceBaseUrl,
        environment.commandServiceBaseUrl,
      ].filter(Boolean);

      const isTrustedUrl = configuredUrls.some(
        (serviceUrl) => new URL(serviceUrl, currentOrigin).origin === parsedUrl.origin
      );

      if (!isTrustedUrl) {
        return false; // Don't attach JWT to third-party origins
      }
    }

    return isApiOrCommandsPath(parsedUrl.pathname);
  } catch {
    return isApiOrCommandsPath(url);
  }
}

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  const authHeader = authService.getAuthorizationHeader();

  if (!authHeader || !isProtectedRequest(request.url)) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: {
        Authorization: authHeader,
      },
    }),
  );
};

