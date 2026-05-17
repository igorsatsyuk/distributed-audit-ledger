import { AsyncPipe, DatePipe, JsonPipe, NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, ViewChild, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BehaviorSubject, Subject, finalize, takeUntil } from 'rxjs';
import { AuditLog, IntegrityCheckResponse, IntegrityStatus } from '../../models/audit-log.model';
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
    MatPaginatorModule,
    MatProgressBarModule,
    MatSidenavModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './audit-dashboard.component.html',
  styleUrl: './audit-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditDashboardComponent implements OnDestroy {
  readonly PAGE_SIZE_OPTIONS = [10, 20, 50];
  readonly DEFAULT_PAGE_SIZE = 20;

  readonly displayedColumns = ['id', 'eventType', 'userId', 'occurredAt', 'integrityStatus', 'details'];
  readonly logs$ = new BehaviorSubject<AuditLog[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedAuditLog = signal<AuditLog | null>(null);

  readonly pageSize = signal(this.DEFAULT_PAGE_SIZE);
  readonly pageIndex = signal(0);
  /** Estimated total used by the paginator; updated after each load. */
  readonly estimatedTotal = signal(0);

  readonly integrityLoading = signal(false);
  readonly integrityCheckResult = signal<IntegrityCheckResponse | null>(null);
  readonly integrityCheckError = signal<string | null>(null);

  readonly userIdControl = new FormControl('', { nonNullable: true });
  readonly eventTypeControl = new FormControl('', { nonNullable: true });

  @ViewChild('detailsDrawer') private detailsDrawer?: MatSidenav;

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly auditLogService: AuditLogService) {
    this.loadAuditLogs();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.loadAuditLogs();
  }

  clearFilters(): void {
    this.userIdControl.setValue('');
    this.eventTypeControl.setValue('');
    this.pageIndex.set(0);
    this.loadAuditLogs();
  }

  retry(): void {
    this.loadAuditLogs();
  }

  onPageChange(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.pageIndex.set(event.pageIndex);
    this.loadAuditLogs();
  }

  openDetails(item: AuditLog): void {
    this.selectedAuditLog.set(item);
    this.integrityCheckResult.set(null);
    this.integrityCheckError.set(null);
    void this.detailsDrawer?.open();
    this.loadIntegrityCheck(item.id);
  }

  closeDetails(): void {
    this.selectedAuditLog.set(null);
    this.integrityCheckResult.set(null);
    this.integrityCheckError.set(null);
    void this.detailsDrawer?.close();
  }

  integrityClass(status: string): string {
    const normalized = status.toUpperCase() as IntegrityStatus;
    if (normalized === 'ON_CHAIN' || normalized === 'MISMATCH' || normalized === 'PENDING') {
      return `status-chip--${normalized.toLowerCase()}`;
    }
    return 'status-chip--pending';
  }

  parseEventData(item: AuditLog): unknown {
    try {
      return JSON.parse(item.eventDataJson);
    } catch {
      return item.eventDataJson;
    }
  }

  private loadAuditLogs(): void {
    this.errorMessage.set(null);
    this.loading.set(true);

    const size = this.pageSize();
    const offset = this.pageIndex() * size;

    this.auditLogService
      .getAuditLogs({
        userId: this.userIdControl.value.trim() || undefined,
        eventType: this.eventTypeControl.value.trim() || undefined,
        limit: size,
        offset,
      })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (items: AuditLog[]) => {
          this.logs$.next(items);
          // Estimate total: if we received a full page, assume there's at least one more
          const hasMore = items.length >= size;
          this.estimatedTotal.set(hasMore ? offset + size + size : offset + items.length);
        },
        error: () => {
          this.logs$.next([]);
          this.errorMessage.set('Failed to load audit logs. Please try again.');
        },
      });
  }

  private loadIntegrityCheck(id: number): void {
    this.integrityLoading.set(true);

    this.auditLogService
      .checkIntegrity(id)
      .pipe(
        finalize(() => this.integrityLoading.set(false)),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (result) => this.integrityCheckResult.set(result),
        error: () => this.integrityCheckError.set('Could not verify blockchain integrity.'),
      });
  }
}
