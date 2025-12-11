import { ApplicationConfig, provideZoneChangeDetection, provideAppInitializer, inject } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';

import { authInterceptorFn } from './interceptors/auth.interceptor.fn';
import { SpaceThemeService } from './theme/space-theme.service';
import { AuthService } from './services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),

    provideRouter(routes),

    // Register the functional interceptor
    provideHttpClient(
      withInterceptors([authInterceptorFn])
    ),

    // 1) Theme initializer
    provideAppInitializer(() => {
      const theme = inject(SpaceThemeService);
      theme.applyTheme();
      return undefined;
    }),

    // 2) Auth initializer
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      auth.init();
      return undefined;
    })
  ]
};
