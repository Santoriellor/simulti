import { Component, OnDestroy } from '@angular/core';
import {FormBuilder, Validators, FormGroup, ReactiveFormsModule} from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import {Router, RouterModule} from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent implements OnDestroy {
  error: string | null = null;
  loading = false;
  form: FormGroup;
  private destroy$ = new Subject<void>();

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error = null;
    this.loading = true;
    const { email, username, password } = this.form.value;
    this.auth.register(email!, username!, password!)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.loading = false;
          this.router.navigate(['/auth/login']);
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message || err?.message || "Échec de l'inscription";
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
