import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows the message the backend sent when the credentials are rejected', () => {
    authSpy.login.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { error: 'Invalid email or password' },
          }),
      ),
    );

    component.form.setValue({ email: 'a@example.com', password: 'wrongpw' });
    component.submit();

    expect(component.error).toBe('Invalid email or password');
  });

  it('falls back to a generic message when the server sends no body', () => {
    authSpy.login.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 0, error: null })),
    );

    component.form.setValue({ email: 'a@example.com', password: 'wrongpw' });
    component.submit();

    expect(component.error).toBe('Connection failed');
  });

  it('does not call the service when the form is invalid', () => {
    component.form.setValue({ email: 'not-an-email', password: '' });
    component.submit();

    expect(authSpy.login).not.toHaveBeenCalled();
  });
});
