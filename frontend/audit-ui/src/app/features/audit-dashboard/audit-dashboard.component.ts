import { AsyncPipe, DatePipe, JsonPipe, NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, ViewChild, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BehaviorSubject, EMPTY, Subject, catchError, finalize, map, switchMap, takeUntil } from 'rxjs';
import { AuditLog, AuditLogFilters, IntegrityCheckResponse, IntegrityStatus } from '../../models/audit-log.model';
import { AuditLogService } from '../../services/audit-log.service';
import { ApproximatePaginatorIntl } from './approximate-paginator-intl';
import { AuditTimelineComponent } from '../audit-timeline/audit-timeline.component';

type DisplayIntegrityStatus = IntegrityStatus | 'UNKNOWN';
type DashboardViewMode = 'table' | 'timeline';

interface DashboardQueryState {
  userId: string;
  eventType: string;
  search: string;
  from: string | null;
  to: string | null;
  page: number;
  pageSize: number;
}

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
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSidenavModule,
    MatTableModule,
    MatTooltipModule,
    AuditTimelineComponent,
  ],
  templateUrl: './audit-dashboard.component.html',
  styleUrl: './audit-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditDashboardComponent implements OnDestroy {
  static readonly PAGE_SIZE_OPTIONS: number[] = [10, 20, 50];
  static readonly DEFAULT_PAGE_SIZE = 20;
  private static readonly STORAGE_KEY = 'audit-dashboard.filters.v1';

  readonly PAGE_SIZE_OPTIONS = AuditDashboardComponent.PAGE_SIZE_OPTIONS;
  readonly DEFAULT_PAGE_SIZE = AuditDashboardComponent.DEFAULT_PAGE_SIZE;

  readonly displayedColumns = ['id', 'eventType', 'userId', 'occurredAt', 'integrityStatus', 'details'];
  readonly logs$ = new BehaviorSubject<AuditLog[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedAuditLog = signal<AuditLog | null>(null);

  readonly pageSize = signal(this.DEFAULT_PAGE_SIZE);
  readonly pageIndex = signal(0);
  readonly estimatedTotal = signal(0);

  readonly integrityLoading = signal(false);
  readonly integrityCheckResult = signal<IntegrityCheckResponse | null>(null);
  readonly integrityCheckError = signal<string | null>(null);
  readonly viewMode = signal<DashboardViewMode>('table');

  readonly userIdControl = new FormControl('', { nonNullable: true });
  readonly eventTypeControl = new FormControl('', { nonNullable: true });
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly dateRangeForm = new FormGroup({
    from: new FormControl<Date | null>(null),
    to: new FormControl<Date | null>(null),
  });

  @ViewChild('detailsDrawer') private readonly detailsDrawer?: MatSidenav;

  private readonly loadTrigger$ = new Subject<void>();
  private readonly integrityTrigger$ = new Subject<number>();
  private readonly rowIntegrityById = signal<Record<number, DisplayIntegrityStatus>>({});
  private readonly destroy$ = new Subject<void>();

  private listRequestSeq = 0;
  private currentListRequestId = 0;
  private integrityRequestSeq = 0;
  private currentIntegrityRequestId = 0;
  private readonly paginatorIntl: ApproximatePaginatorIntl;
  private storageRestoreHandled = false;

  constructor(
    private readonly auditLogService: AuditLogService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    paginatorIntl: MatPaginatorIntl,
  ) {
    this.paginatorIntl = paginatorIntl as ApproximatePaginatorIntl;
    this.initLoadPipeline();
    this.initIntegrityPipeline();
    this.initRouteStatePipeline();
  }

  get fromDateControl(): FormControl<Date | null> {
    return this.dateRangeForm.controls.from;
  }

  get toDateControl(): FormControl<Date | null> {
    return this.dateRangeForm.controls.to;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.syncUrlWithCurrentState();
  }

  setViewMode(mode: DashboardViewMode): void {
    this.viewMode.set(mode);
  }

  clearFilters(): void {
    this.userIdControl.setValue('');
    this.eventTypeControl.setValue('');
    this.searchControl.setValue('');
    this.fromDateControl.setValue(null);
    this.toDateControl.setValue(null);
    this.pageIndex.set(0);
    this.syncUrlWithCurrentState();
  }

  retry(): void {
    this.loadTrigger$.next();
  }

  onPageChange(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.pageIndex.set(event.pageIndex);
    this.syncUrlWithCurrentState();
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

  exportCsv(): void {
    const rows = this.logs$.value;
    if (rows.length === 0) {
      return;
    }

    const csv = this.buildCsv(rows);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = this.buildCsvFilename();
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    // Defer revocation so the browser has time to start the download before
    // the blob URL is invalidated (immediate revoke can cause empty downloads).
    setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
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
              ...this.buildFiltersForApi(),
              limit: size + 1,
              offset,
            })
            .pipe(
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
        this.pruneIntegrityCache(visibleRows);
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
              }
              return EMPTY;
            }),
            finalize(() => {
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

  private initRouteStatePipeline(): void {
    this.route.queryParamMap
      .pipe(takeUntil(this.destroy$))
      .subscribe(paramMap => {
        const hasUrlState = this.hasRelevantQueryParams(paramMap);

        if (!hasUrlState && !this.storageRestoreHandled) {
          this.storageRestoreHandled = true;
          const storedState = this.readStoredState();
          if (storedState) {
            const restoreStoredState = () => {
              this.applyState(storedState);
              this.persistState(storedState);
              this.loadTrigger$.next();
            };

            this.router.navigate([], {
              relativeTo: this.route,
              queryParams: this.toQueryParams(storedState),
            }).then((navigated: boolean) => {
              if (!navigated) {
                restoreStoredState();
              }
            }).catch(() => {
              restoreStoredState();
            });
            return;
          }
        }

        this.storageRestoreHandled = true;
        const state = this.stateFromQueryParams(paramMap);
        this.applyState(state);
        this.persistState(state);
        this.loadTrigger$.next();
      });
  }

  private buildFiltersForApi(): AuditLogFilters {
    return {
      userId: this.normalizeText(this.userIdControl.value) || undefined,
      eventType: this.normalizeText(this.eventTypeControl.value) || undefined,
      search: this.normalizeText(this.searchControl.value) || undefined,
      from: this.toStartOfDayIso(this.fromDateControl.value) ?? undefined,
      to: this.toEndOfDayIso(this.toDateControl.value) ?? undefined,
    };
  }

  private currentState(): DashboardQueryState {
    return {
      userId: this.normalizeText(this.userIdControl.value),
      eventType: this.normalizeText(this.eventTypeControl.value),
      search: this.normalizeText(this.searchControl.value),
      from: this.toStartOfDayIso(this.fromDateControl.value),
      to: this.toEndOfDayIso(this.toDateControl.value),
      page: this.pageIndex(),
      pageSize: this.pageSize(),
    };
  }

  private syncUrlWithCurrentState(): void {
    const state = this.currentState();
    this.persistState(state);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.toQueryParams(state),
    }).then((navigated: boolean) => {
      // Angular may ignore same-URL navigation; force reload in that case.
      if (!navigated) {
        this.loadTrigger$.next();
      }
    }).catch(() => {
      // Best-effort fallback: if navigation fails, still refresh the table.
      this.loadTrigger$.next();
    });
  }

  private stateFromQueryParams(paramMap: ParamMap): DashboardQueryState {
    return {
      userId: this.normalizeText(paramMap.get('userId')),
      eventType: this.normalizeText(paramMap.get('eventType')),
      search: this.normalizeText(paramMap.get('search')),
      from: this.parseDateParam(paramMap.get('from')),
      to: this.parseDateParam(paramMap.get('to')),
      page: this.parseNonNegativeInt(paramMap.get('page'), 0),
      pageSize: this.resolvePageSize(paramMap.get('pageSize')),
    };
  }

  private applyState(state: DashboardQueryState): void {
    this.userIdControl.setValue(state.userId, { emitEvent: false });
    this.eventTypeControl.setValue(state.eventType, { emitEvent: false });
    this.searchControl.setValue(state.search, { emitEvent: false });
    this.fromDateControl.setValue(this.toCalendarDate(state.from), { emitEvent: false });
    this.toDateControl.setValue(this.toCalendarDate(state.to), { emitEvent: false });
    this.pageIndex.set(state.page);
    this.pageSize.set(state.pageSize);
  }

  private toQueryParams(state: DashboardQueryState): Record<string, string> {
    const queryParams: Record<string, string> = {};

    if (state.userId) {
      queryParams['userId'] = state.userId;
    }
    if (state.eventType) {
      queryParams['eventType'] = state.eventType;
    }
    if (state.search) {
      queryParams['search'] = state.search;
    }
    if (state.from) {
      queryParams['from'] = state.from;
    }
    if (state.to) {
      queryParams['to'] = state.to;
    }
    if (state.page > 0) {
      queryParams['page'] = String(state.page);
    }
    if (state.pageSize !== this.DEFAULT_PAGE_SIZE) {
      queryParams['pageSize'] = String(state.pageSize);
    }

    return queryParams;
  }

  private hasRelevantQueryParams(paramMap: ParamMap): boolean {
    return ['userId', 'eventType', 'search', 'from', 'to', 'page', 'pageSize'].some(key => paramMap.has(key));
  }

  private readStoredState(): DashboardQueryState | null {
    try {
      const rawState = globalThis.localStorage?.getItem(AuditDashboardComponent.STORAGE_KEY);
      if (!rawState) {
        return null;
      }

      const parsedState = JSON.parse(rawState) as Partial<DashboardQueryState>;
      return {
        userId: this.normalizeText(parsedState.userId),
        eventType: this.normalizeText(parsedState.eventType),
        search: this.normalizeText(parsedState.search),
        from: this.parseDateParam(parsedState.from),
        to: this.parseDateParam(parsedState.to),
        page: this.parseStoredPage(parsedState.page),
        pageSize: this.parseStoredPageSize(parsedState.pageSize),
      };
    } catch (error: unknown) {
      console.debug('Failed to restore dashboard filters from localStorage:', error);
      return null;
    }
  }

  private persistState(state: DashboardQueryState): void {
    try {
      if (this.isDefaultState(state)) {
        globalThis.localStorage?.removeItem(AuditDashboardComponent.STORAGE_KEY);
        return;
      }
      globalThis.localStorage?.setItem(AuditDashboardComponent.STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Quota exceeded, private mode, or storage disabled — fail silently.
    }
  }

  private isDefaultState(state: DashboardQueryState): boolean {
    return !state.userId && !state.eventType && !state.search && !state.from && !state.to && state.page === 0 && state.pageSize === this.DEFAULT_PAGE_SIZE;
  }

  private normalizeText(value: string | null | undefined): string {
    return value?.trim() ?? '';
  }

  private parseDateParam(value: string | null | undefined): string | null {
    if (!value) {
      return null;
    }

    const parsedDate = new Date(value);
    return Number.isNaN(parsedDate.getTime()) ? null : parsedDate.toISOString();
  }

  private toCalendarDate(value: string | null): Date | null {
    if (!value) {
      return null;
    }

    const parsedDate = new Date(value);
    if (Number.isNaN(parsedDate.getTime())) {
      return null;
    }

    return new Date(parsedDate.getFullYear(), parsedDate.getMonth(), parsedDate.getDate());
  }

  private parseNonNegativeInt(value: string | null, fallback: number): number {
    if (value == null) {
      return fallback;
    }

    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
  }

  private parseStoredPage(value: number | undefined): number {
    return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : 0;
  }

  private parseStoredPageSize(value: number | undefined): number {
    return typeof value === 'number' ? this.resolvePageSize(String(value)) : this.DEFAULT_PAGE_SIZE;
  }

  private resolvePageSize(value: string | null): number {
    const parsed = value == null ? Number.NaN : Number.parseInt(value, 10);
    return this.PAGE_SIZE_OPTIONS.includes(parsed) ? parsed : this.DEFAULT_PAGE_SIZE;
  }

  private toStartOfDayIso(date: Date | null): string | null {
    if (!date) {
      return null;
    }

    return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0, 0).toISOString();
  }

  private toEndOfDayIso(date: Date | null): string | null {
    if (!date) {
      return null;
    }

    return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999).toISOString();
  }

  private pruneIntegrityCache(rows: AuditLog[]): void {
    const rowIds = new Set(rows.map(row => row.id));
    const nextCache = Object.entries(this.rowIntegrityById())
      .filter(([id]) => rowIds.has(Number(id)))
      .reduce<Record<number, DisplayIntegrityStatus>>((accumulator, [id, status]) => {
        accumulator[Number(id)] = status;
        return accumulator;
      }, {});

    this.rowIntegrityById.set(nextCache);
  }

  private buildCsv(rows: AuditLog[]): string {
    const header = ['id', 'eventId', 'eventType', 'userId', 'occurredAt', 'integrityStatus', 'eventHash', 'eventDataJson'];
    const csvRows = rows.map(row => [
      row.id,
      row.eventId,
      row.eventType,
      row.userId ?? '',
      row.occurredAt,
      this.effectiveIntegrityStatus(row),
      row.eventHash ?? '',
      row.eventDataJson,
    ]);

    return [header, ...csvRows]
      .map(columns => columns.map(value => this.escapeCsvValue(value)).join(','))
      .join('\r\n');
  }

  private escapeCsvValue(value: unknown): string {
    const normalized = this.normalizeCsvValue(value);
    // Guard against CSV/spreadsheet formula injection (Excel, Google Sheets).
    // Strip leading whitespace/control chars before checking for formula prefixes so
    // values like "\t=CMD" or " =1+1" are also caught (not just bare leading chars).
    const trimmedForCheck = normalized.replace(/^[\s\x00-\x08\x0E-\x1F]+/, '');
    const safe = /^[=+\-@]/.test(trimmedForCheck) ? `'${normalized}` : normalized;
    return /[",\r\n]/.test(safe)
      ? `"${safe.replace(/"/g, '""')}"`
      : safe;
  }

  private buildCsvFilename(): string {
    return `audit-logs-${new Date().toISOString().replaceAll(':', '-')}.csv`;
  }

  private normalizeCsvValue(value: unknown): string {
    if (typeof value === 'string') {
      return value;
    }

    if (value == null) {
      return '';
    }

    if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') {
      return String(value);
    }

    try {
      return JSON.stringify(value) ?? '';
    } catch (error: unknown) {
      console.debug('Failed to stringify CSV value:', error);
      return String(value);
    }
  }
}


