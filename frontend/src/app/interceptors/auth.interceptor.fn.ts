import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

export const authInterceptorFn: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService); // lazy
  const router = inject(Router);

  const token = auth.getToken();

  const cloned = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(cloned).pipe(
    catchError((error: any) => {
      // keep behavior: if 401 clear auth and navigate to login
      if (error?.status === 401) {
        auth.clearAuth();
        router.navigate(['/auth/login']);
      }
      return throwError(() => error);
    })
  );
};
