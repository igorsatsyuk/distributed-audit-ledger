import { Routes } from '@angular/router';
import { AuditDashboardComponent } from './features/audit-dashboard/audit-dashboard.component';
import { AuthLoginComponent } from './features/auth-login/auth-login.component';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: 'login', component: AuthLoginComponent },
  { path: '', component: AuditDashboardComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
