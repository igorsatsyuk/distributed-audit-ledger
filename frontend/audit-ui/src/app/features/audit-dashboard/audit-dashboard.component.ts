import { AsyncPipe, DatePipe, JsonPipe, NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, ViewChild, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatTableModule } from '@angular/material/table';
import { BehaviorSubject, finalize } from 'rxjs';
import { AuditLog, IntegrityStatus } from '../../models/audit-log.model';
import { AuditLogService } from '../../services/audit-log.service';

@Component({
  selector: 'app-audit-dashboard',
  standalone: true,
  imports: [
    AsyncPipe,
    DatePipe,
    JsonPipe,
    NgClass,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSidenavModule,
    MatTableModule,
  ],
  templateUrl: './audit-dashboard.component.html',
  styleUrl: './audit-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditDashboardComponent {
  readonly displayedColumns = ['id', 'eventType', 'userId', 'createdAt', 'status', 'details'];
  readonly logs$ = new BehaviorSubject<AuditLog[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedAuditLog = signal<AuditLog | null>(null);

  readonly userIdControl = new FormControl('', { nonNullable: true });
  readonly eventTypeControl = new FormControl('', { nonNullable: true });

  @ViewChild('detailsDrawer') private detailsDrawer?: MatSidenav;

  constructor(private readonly auditLogService: AuditLogService) {
    this.loadAuditLogs();
  }

  applyFilters(): void {
    this.loadAuditLogs();
  }

  clearFilters(): void {
    this.userIdControl.setValue('');
    this.eventTypeControl.setValue('');
    this.loadAuditLogs();
  }

  openDetails(item: AuditLog): void {
    this.selectedAuditLog.set(item);
    void this.detailsDrawer?.open();
  }

  closeDetails(): void {
    this.selectedAuditLog.set(null);
    void this.detailsDrawer?.close();
  }

  integrityClass(status: IntegrityStatus): string {
    return `status-chip--${status.toLowerCase()}`;
  }

  private loadAuditLogs(): void {
    this.errorMessage.set(null);
    this.loading.set(true);

    this.auditLogService
      .getAuditLogs({
        userId: this.userIdControl.value.trim() || undefined,
        eventType: this.eventTypeControl.value.trim() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (items: AuditLog[]) => this.logs$.next(items),
        error: () => {
          this.logs$.next([]);
          this.errorMessage.set('Failed to load audit logs. Please try again.');
        },
      });
  }
}

