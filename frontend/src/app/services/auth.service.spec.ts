import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('reports an absent token as not authenticated', () => {
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('reports a malformed token as not authenticated', () => {
    localStorage.setItem('auth_token', 'not-a-jwt');
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('reports an expired token as not authenticated', () => {
    const expired = makeJwt({ exp: Math.floor(Date.now() / 1000) - 60 });
    localStorage.setItem('auth_token', expired);
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('reports an unexpired token as authenticated', () => {
    const valid = makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 });
    localStorage.setItem('auth_token', valid);
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('stores the token on login and then loads the profile', () => {
    let emitted: unknown = null;
    service.currentUser$.subscribe((u) => (emitted = u));

    service.login('a@example.com', 'pw').subscribe();

    const loginReq = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(loginReq.request.method).toBe('POST');
    loginReq.flush({ token: makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 }) });

    const meReq = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
    expect(meReq.request.method).toBe('GET');
    meReq.flush({ id: 'u1', username: 'alice', email: 'a@example.com' });

    expect(localStorage.getItem('auth_token')).not.toBeNull();
    expect(emitted).toEqual({ id: 'u1', username: 'alice', email: 'a@example.com' } as never);
  });

  it('clears the token and the current user on logout', () => {
    localStorage.setItem('auth_token', makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 }));
    service.logout();
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(service.getCurrentUser()).toBeNull();
  });
});

/** Builds an unsigned JWT whose payload decodes; jwtDecode does not verify signatures. */
function makeJwt(payload: Record<string, unknown>): string {
  const encode = (o: Record<string, unknown>) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}
