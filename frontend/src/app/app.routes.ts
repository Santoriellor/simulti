import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { HomeComponent } from './home/home.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: 'auth/login', component: LoginComponent },
  { path: 'auth/register', component: RegisterComponent },
  {
    path: 'home',
    component: HomeComponent,
    canActivate: [authGuard]
  },
  {
    path: 'game/space-invaders',
    canActivate: [authGuard],
    loadComponent: () => import('./game/space-invaders.component').then(m => m.SpaceInvadersComponent)
  },
  {
    path: 'leaderboard',
    canActivate: [authGuard],
    loadComponent: () => import('./leaderboard/leaderboard.component').then(m => m.LeaderboardComponent)
  },
  {
    path: 'waitingRoom',
    canActivate: [authGuard],
    loadComponent: () => import('./waitingRoom/waitingRoom.component').then(m => m.WaitingRoomComponent)
  },
  { path: '**', redirectTo: '/home' }
];
