import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptorFn } from './auth.interceptor.fn';

describe('authInterceptorFn', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptorFn])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('sends no Authorization header when no token is stored', () => {
    http.get('/api/anything').subscribe();
    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('attaches the stored token as a Bearer credential', () => {
    localStorage.setItem('auth_token', 'stored-token');
    http.get('/api/anything').subscribe();
    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.get('Authorization')).toBe('Bearer stored-token');
    req.flush({});
  });
});
