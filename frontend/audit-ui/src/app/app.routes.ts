import { Routes } from '@angular/router';
import { AuditDashboardComponent } from './features/audit-dashboard/audit-dashboard.component';

export const routes: Routes = [
  { path: '', component: AuditDashboardComponent },
  { path: '**', redirectTo: '' },
];
