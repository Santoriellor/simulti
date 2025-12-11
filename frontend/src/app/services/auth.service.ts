import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {BehaviorSubject, Observable, switchMap, tap} from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';
import { User } from '../models/user.model';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';
import {jwtDecode} from 'jwt-decode';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly TOKEN_KEY = 'auth_token';

  private readonly currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private readonly http: HttpClient, private readonly router: Router) {}

  // --------------------------------------------------------------
  //  INITIALIZATION (called once from app.component.ts)
  // --------------------------------------------------------------
  init(): void {
    const token = this.getToken();

    if (!token || !this.isTokenValid(token)) {
      this.clearAuth();
      return;
    }

    // Load user silently (no logout on failure)
    this.getCurrentUserFromBackend().subscribe({
      error: () => console.warn('Unable to fetch user profile, but token exists.')
    });
  }

  // --------------------------------------------------------------
  //  AUTHENTICATION
  // --------------------------------------------------------------
  login(email: string, password: string): Observable<User> {
    const payload: LoginRequest = { email, password };

    return this.http.post<AuthResponse>(`${this.API_URL}/login`, payload).pipe(
      tap(response => this.saveToken(response.token)),
      switchMap(() => this.getCurrentUserFromBackend())
    );
  }

  register(email: string, username: string, password: string): Observable<string> {
    const payload: RegisterRequest = { email, username, password };
    return this.http.post(`${this.API_URL}/register`, payload, { responseType: 'text' });
  }

  logout(): void {
    this.clearAuth();
    this.router.navigate(['/auth/login']);
  }

  // --------------------------------------------------------------
  //  USER PROFILE
  // --------------------------------------------------------------
  getCurrentUserFromBackend(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/me`).pipe(
      tap(user => this.currentUserSubject.next(user))
    );
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  // --------------------------------------------------------------
  //  TOKEN MANAGEMENT
  // --------------------------------------------------------------
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private saveToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  // --------------------------------------------------------------
  //  TOKEN VALIDATION
  // --------------------------------------------------------------
  private isTokenValid(token: string): boolean {
    try {
      const decoded: any = jwtDecode(token);
      const exp = decoded.exp * 1000;
      return Date.now() < exp;
    } catch {
      return false;
    }
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return !!token && this.isTokenValid(token);
  }

  // --------------------------------------------------------------
  //  CLEAR AUTH
  // --------------------------------------------------------------
  clearAuth(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this.currentUserSubject.next(null);
  }
}
