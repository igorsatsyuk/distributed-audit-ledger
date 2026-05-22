import { AsyncPipe, DatePipe, JsonPipe, NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, ViewChild, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BehaviorSubject, EMPTY, Subject, catchError, finalize, map, switchMap, takeUntil } from 'rxjs';
import { AuditLog, IntegrityCheckResponse, IntegrityStatus } from '../../models/audit-log.model';
import { AuditLogService } from '../../services/audit-log.service';
import { ApproximatePaginatorIntl } from './approximate-paginator-intl';

type DisplayIntegrityStatus = IntegrityStatus | 'UNKNOWN';


@Component({
  selector: 'app-audit-dashboard',
  standalone: true,
  providers: [{ provide: MatPaginatorIntl, useClass: ApproximatePaginatorIntl }],
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
  static readonly PAGE_SIZE_OPTIONS: number[] = [10, 20, 50];
  static readonly DEFAULT_PAGE_SIZE = 20;

  readonly PAGE_SIZE_OPTIONS = AuditDashboardComponent.PAGE_SIZE_OPTIONS;
  readonly DEFAULT_PAGE_SIZE = AuditDashboardComponent.DEFAULT_PAGE_SIZE;

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

  @ViewChild('detailsDrawer') private readonly detailsDrawer?: MatSidenav;

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
  private readonly rowIntegrityById = signal<Record<number, DisplayIntegrityStatus>>({});

  private readonly destroy$ = new Subject<void>();

  private listRequestSeq = 0;
  private currentListRequestId = 0;
  private integrityRequestSeq = 0;
  private currentIntegrityRequestId = 0;
  private readonly paginatorIntl: ApproximatePaginatorIntl;

  constructor(
    private readonly auditLogService: AuditLogService,
    paginatorIntl: MatPaginatorIntl,
  ) {
    this.paginatorIntl = paginatorIntl as ApproximatePaginatorIntl;
    this.initLoadPipeline();
    this.initIntegrityPipeline();

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
    this.detailsDrawer?.open()?.catch((error: unknown) => {
      console.debug('Failed to open details drawer (non-critical):', error);
    });
    this.integrityTrigger$.next(item.id);
  }

  closeDetails(): void {
    this.selectedAuditLog.set(null);
    this.integrityCheckResult.set(null);
    this.integrityCheckError.set(null);
    this.detailsDrawer?.close()?.catch((error: unknown) => {
      console.debug('Failed to close details drawer (non-critical):', error);
    });
  }

  integrityClass(status: string): string {
    const normalized = status.toUpperCase() as DisplayIntegrityStatus;
    if (normalized === 'ON_CHAIN' || normalized === 'MISMATCH' || normalized === 'PENDING') {
      return `status-chip--${normalized.toLowerCase()}`;
    }
    return 'status-chip--unknown';
  }

  effectiveIntegrityStatus(item: AuditLog): DisplayIntegrityStatus {
    return this.rowIntegrityById()[item.id] ?? item.integrityStatus;
  }

  parseEventData(item: AuditLog): unknown {
    try {
      return JSON.parse(item.eventDataJson);
    } catch (error: unknown) {
      // eventDataJson is not valid JSON — return the raw string as-is
      console.debug('Failed to parse event data:', error);
      return item.eventDataJson;
    }
  }

  private initLoadPipeline(): void {
    this.loadTrigger$
      .pipe(
        map(() => {
          const requestId = ++this.listRequestSeq;
          this.currentListRequestId = requestId;
          this.loading.set(true);
          this.errorMessage.set(null);
          return requestId;
        }),
        switchMap(requestId => {
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
                if (requestId === this.currentListRequestId) {
                  this.logs$.next([]);
                  this.estimatedTotal.set(0);
                  this.paginatorIntl.setHasMore(false);
                  this.rowIntegrityById.set({});
                  this.errorMessage.set('Failed to load audit logs. Please try again.');
                }
                return EMPTY;
              }),
              finalize(() => {
                // Ignore cancellation of previous requests; only latest controls loading
                if (requestId === this.currentListRequestId) {
                  this.loading.set(false);
                }
              }),
            );
        }),
        takeUntil(this.destroy$),
      )
      .subscribe(({ items, size, offset }) => {
        const hasMore = items.length > size;
        const visibleRows = hasMore ? items.slice(0, size) : items;

        this.logs$.next(visibleRows);
        this.estimatedTotal.set(hasMore ? offset + size + 1 : offset + visibleRows.length);
        this.paginatorIntl.setHasMore(hasMore);
      });
  }

  private initIntegrityPipeline(): void {
    this.integrityTrigger$
      .pipe(
        map(id => {
          const requestId = ++this.integrityRequestSeq;
          this.currentIntegrityRequestId = requestId;
          this.integrityLoading.set(true);
          this.integrityCheckResult.set(null);
          this.integrityCheckError.set(null);
          return { id, requestId };
        }),
        switchMap(({ id, requestId }) =>
          this.auditLogService.checkIntegrity(id).pipe(
            map(result => ({ result, requestId })),
            catchError(() => {
              if (requestId === this.currentIntegrityRequestId) {
                this.integrityCheckError.set('Could not verify blockchain integrity.');
                // Do NOT write UNKNOWN into the row cache: a transport/RPC error is not
                // an integrity result. The previous list status (e.g. PENDING / ON_CHAIN)
                // stays visible in the table; only the drawer shows the error message.
              }
              return EMPTY;
            }),
            finalize(() => {
              // Ignore finalize from cancelled checks of previously selected rows
              if (requestId === this.currentIntegrityRequestId) {
                this.integrityLoading.set(false);
              }
            }),
          ),
        ),
        takeUntil(this.destroy$),
      )
      .subscribe(({ result, requestId }) => {
        if (requestId === this.currentIntegrityRequestId) {
          this.integrityCheckResult.set(result);
          this.rowIntegrityById.update(current => ({ ...current, [result.auditLogId]: result.status }));
        }
      });
  }

}
