import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('blocks an unauthenticated visitor by redirecting to /auth/login', () => {
    // The guard does not call router.navigate(); it returns a UrlTree built
    // via router.createUrlTree(['/auth/login']), which the Router treats as
    // a redirect instruction. It never returns a boolean `false`.
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toBe('/auth/login');
  });

  it('admits a visitor holding an unexpired token', () => {
    const encode = (o: Record<string, unknown>) =>
      btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const token =
      `${encode({ alg: 'HS256', typ: 'JWT' })}.` +
      `${encode({ exp: Math.floor(Date.now() / 1000) + 3600 })}.sig`;
    localStorage.setItem('auth_token', token);

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBeTrue();
  });
});
