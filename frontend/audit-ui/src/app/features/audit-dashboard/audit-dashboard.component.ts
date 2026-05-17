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
import { BehaviorSubject, EMPTY, Subject, catchError, finalize, map, switchMap, takeUntil, tap } from 'rxjs';
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

  /**
   * Emitting here triggers a (possibly cancelling) list reload.
   * switchMap ensures only the latest request's result is applied.
   */
  private readonly loadTrigger$ = new Subject<void>();

  /**
   * Emitting an id here triggers an integrity check for that row.
   * switchMap cancels any in-flight check for a previously opened row.
   */
  private readonly integrityTrigger$ = new Subject<number>();

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly auditLogService: AuditLogService) {
    // ── List loading pipeline ────────────────────────────────────────────────
    this.loadTrigger$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        // switchMap cancels any previous in-flight list request
        switchMap(() => {
          const size = this.pageSize();
          const offset = this.pageIndex() * size;
          return this.auditLogService
            .getAuditLogs({
              userId: this.userIdControl.value.trim() || undefined,
              eventType: this.eventTypeControl.value.trim() || undefined,
              // Fetch one extra item to detect whether a next page exists
              limit: size + 1,
              offset,
            })
            .pipe(
              // Carry size/offset into the subscriber so stale values aren't read
              map(items => ({ items, size, offset })),
              catchError(() => {
                this.logs$.next([]);
                this.errorMessage.set('Failed to load audit logs. Please try again.');
                return EMPTY;
              }),
              finalize(() => this.loading.set(false)),
            );
        }),
        takeUntil(this.destroy$),
      )
      .subscribe(({ items, size, offset }) => {
        const hasMore = items.length > size;
        this.logs$.next(hasMore ? items.slice(0, size) : items);
        // If we got the extra item, another page exists → advance estimate by one page
        this.estimatedTotal.set(hasMore ? offset + size + size : offset + items.length);
      });

    // ── Integrity check pipeline ─────────────────────────────────────────────
    this.integrityTrigger$
      .pipe(
        tap(() => {
          this.integrityLoading.set(true);
          this.integrityCheckResult.set(null);
          this.integrityCheckError.set(null);
        }),
        // switchMap cancels any in-flight check when a different row is opened
        switchMap(id =>
          this.auditLogService.checkIntegrity(id).pipe(
            catchError(() => {
              this.integrityCheckError.set('Could not verify blockchain integrity.');
              return EMPTY;
            }),
            finalize(() => this.integrityLoading.set(false)),
          ),
        ),
        takeUntil(this.destroy$),
      )
      .subscribe(result => this.integrityCheckResult.set(result));

    // Initial load
    this.loadTrigger$.next();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.loadTrigger$.next();
  }

  clearFilters(): void {
    this.userIdControl.setValue('');
    this.eventTypeControl.setValue('');
    this.pageIndex.set(0);
    this.loadTrigger$.next();
  }

  retry(): void {
    this.loadTrigger$.next();
  }

  onPageChange(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.pageIndex.set(event.pageIndex);
    this.loadTrigger$.next();
  }

  openDetails(item: AuditLog): void {
    this.selectedAuditLog.set(item);
    void this.detailsDrawer?.open();
    // tap() inside integrityTrigger$ pipeline resets loading/result/error states
    this.integrityTrigger$.next(item.id);
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
}
