import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, Params, Router } from '@angular/router';
import { AuditDashboardComponent } from './audit-dashboard.component';
import { AuditLogService } from '../../services/audit-log.service';
import { AuditLog, IntegrityCheckResponse } from '../../models/audit-log.model';

const MOCK_LOG: AuditLog = {
  id: 1,
  eventId: 'event-1',
  eventType: 'USER_LOGGED_IN',
  userId: 'user1',
  occurredAt: '2026-05-17T10:15:00Z',
  eventDataJson: '{"key":"value"}',
  eventHash: 'abc123',
  integrityStatus: 'ON_CHAIN',
};

const MOCK_INTEGRITY: IntegrityCheckResponse = {
  auditLogId: 1,
  eventId: 'event-1',
  eventHash: 'abc123',
  blockchainRecord: { exists: true, transactionHash: '0xdeadbeef', blockNumber: 42, timestamp: 1715774400 },
  status: 'ON_CHAIN',
};

class ActivatedRouteStub {
  private readonly queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));
  readonly queryParamMap = this.queryParamMapSubject.asObservable();

  setQueryParams(params: Params): void {
    this.queryParamMapSubject.next(convertToParamMap(params));
  }
}

function makeServiceSpy(overrides: Partial<{
  getAuditLogs: jasmine.Spy;
  checkIntegrity: jasmine.Spy;
}> = {}) {
  return {
    getAuditLogs: overrides.getAuditLogs ?? jasmine.createSpy('getAuditLogs').and.returnValue(of([MOCK_LOG])),
    getAuditLogById: jasmine.createSpy('getAuditLogById').and.returnValue(of(MOCK_LOG)),
    checkIntegrity: overrides.checkIntegrity ?? jasmine.createSpy('checkIntegrity').and.returnValue(of(MOCK_INTEGRITY)),
  };
}

function startOfLocalDayIso(year: number, monthIndex: number, day: number): string {
  return new Date(year, monthIndex, day, 0, 0, 0, 0).toISOString();
}

function endOfLocalDayIso(year: number, monthIndex: number, day: number): string {
  return new Date(year, monthIndex, day, 23, 59, 59, 999).toISOString();
}

function roundTripStartIso(value: string): string {
  const date = new Date(value);
  return startOfLocalDayIso(date.getFullYear(), date.getMonth(), date.getDate());
}

function roundTripEndIso(value: string): string {
  const date = new Date(value);
  return endOfLocalDayIso(date.getFullYear(), date.getMonth(), date.getDate());
}

describe('AuditDashboardComponent', () => {
  let component: AuditDashboardComponent;
  let fixture: ComponentFixture<AuditDashboardComponent>;
  let serviceSpy: ReturnType<typeof makeServiceSpy>;
  let routeStub: ActivatedRouteStub;
  let routerSpy: jasmine.SpyObj<Router>;

  async function init(
    spy = makeServiceSpy(),
    options: Partial<{
      initialQueryParams: Params;
      localStorageState: unknown;
      navigateResult: boolean | 'reject';
    }> = {},
  ) {
    localStorage.clear();
    if (options.localStorageState !== undefined) {
      localStorage.setItem((AuditDashboardComponent as any).STORAGE_KEY, JSON.stringify(options.localStorageState));
    }

    routeStub = new ActivatedRouteStub();
    if (options.initialQueryParams) {
      routeStub.setQueryParams(options.initialQueryParams);
    }

    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    routerSpy.navigate.and.callFake(async (_commands: readonly unknown[], extras?: { queryParams?: Params }) => {
      routeStub.setQueryParams(extras?.queryParams ?? {});
      if (options.navigateResult === 'reject') {
        throw new Error('navigation failed');
      }
      return options.navigateResult ?? true;
    });

    serviceSpy = spy;
    await TestBed.configureTestingModule({
      imports: [AuditDashboardComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AuditLogService, useValue: spy },
        { provide: ActivatedRoute, useValue: routeStub },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => {
    localStorage.clear();
  });

  it('creates the component', async () => {
    await init();
    expect(component).toBeTruthy();
  });

  it('renders audit table title', async () => {
    await init();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Audit log');
    expect(el.textContent).toContain('Export CSV');
  });

  it('shows the table view by default and can switch to timeline view', async () => {
    await init();

    let el = fixture.nativeElement as HTMLElement;
    const tableButton = el.querySelector('[aria-pressed]') as HTMLButtonElement | null;
    const timelineButton = el.querySelectorAll('[aria-pressed]')[1] as HTMLButtonElement | undefined;
    expect(el.querySelector('table')).toBeTruthy();
    expect(el.querySelector('app-audit-timeline')).toBeFalsy();
    expect(tableButton?.getAttribute('aria-pressed')).toBe('true');
    expect(timelineButton?.getAttribute('aria-pressed')).toBe('false');

    component.setViewMode('timeline');
    fixture.detectChanges();

    el = fixture.nativeElement as HTMLElement;
    expect((el.querySelectorAll('[aria-pressed]')[0] as HTMLButtonElement | undefined)?.getAttribute('aria-pressed')).toBe('false');
    expect((el.querySelectorAll('[aria-pressed]')[1] as HTMLButtonElement | undefined)?.getAttribute('aria-pressed')).toBe('true');
    expect(el.querySelector('app-audit-timeline')).toBeTruthy();
    expect(el.querySelector('table')).toBeFalsy();
  });

  it('opens details when a timeline event is selected', async () => {
    await init();

    component.setViewMode('timeline');
    fixture.detectChanges();

    const timelineButton = fixture.nativeElement.querySelector('[data-testid="timeline-event-1"]') as HTMLButtonElement;
    expect(timelineButton).toBeTruthy();

    timelineButton.click();
    await fixture.whenStable();

    expect(component.selectedAuditLog()).toEqual(MOCK_LOG);
    expect(serviceSpy.checkIntegrity).toHaveBeenCalledWith(MOCK_LOG.id);
  });

  it('calls getAuditLogs on init with default page size and offset 0', async () => {
    await init();
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({ limit: 21, offset: 0 }),
    );
  });

  it('hydrates filters from URL query params and requests matching data', async () => {
    const from = '2026-05-01T00:00:00.000Z';
    const to = '2026-05-31T23:59:59.999Z';

    await init(makeServiceSpy(), {
      initialQueryParams: {
        userId: 'user-from-url',
        eventType: 'USER_LOGGED_IN',
        search: '10.0.0.5',
        from,
        to,
        page: '1',
        pageSize: '50',
      },
    });

    expect(component.userIdControl.value).toBe('user-from-url');
    expect(component.eventTypeControl.value).toBe('USER_LOGGED_IN');
    expect(component.searchControl.value).toBe('10.0.0.5');
    expect(component.pageIndex()).toBe(1);
    expect(component.pageSize()).toBe(50);
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({
        userId: 'user-from-url',
        eventType: 'USER_LOGGED_IN',
        search: '10.0.0.5',
        from: roundTripStartIso(from),
        to: roundTripEndIso(to),
        limit: 51,
        offset: 50,
      }),
    );
  });

  it('restores filter state from localStorage when URL params are absent', async () => {
    await init(makeServiceSpy(), {
      localStorageState: {
        userId: 'stored-user',
        eventType: 'USER_LOGGED_IN',
        search: 'stored-search',
        from: '2026-05-05T00:00:00.000Z',
        to: '2026-05-08T23:59:59.999Z',
        page: 2,
        pageSize: 50,
      },
    });

    expect(routerSpy.navigate).toHaveBeenCalled();
    expect(component.userIdControl.value).toBe('stored-user');
    expect(component.searchControl.value).toBe('stored-search');
    expect(component.pageIndex()).toBe(2);
    expect(component.pageSize()).toBe(50);
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({
        userId: 'stored-user',
        eventType: 'USER_LOGGED_IN',
        search: 'stored-search',
        limit: 51,
        offset: 100,
      }),
    );
  });

  it('restores filter state from localStorage when initial navigation is ignored', async () => {
    await init(makeServiceSpy(), {
      localStorageState: {
        userId: 'stored-user',
        eventType: 'USER_LOGGED_IN',
        search: 'stored-search',
        from: '2026-05-05T00:00:00.000Z',
        to: '2026-05-08T23:59:59.999Z',
        page: 2,
        pageSize: 50,
      },
      navigateResult: false,
    });

    expect(routerSpy.navigate).toHaveBeenCalled();
    expect(component.userIdControl.value).toBe('stored-user');
    expect(component.pageIndex()).toBe(2);
    expect(component.pageSize()).toBe(50);
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({
        userId: 'stored-user',
        eventType: 'USER_LOGGED_IN',
        search: 'stored-search',
        limit: 51,
        offset: 100,
      }),
    );
  });

  it('restores filter state from localStorage when initial navigation fails', async () => {
    await init(makeServiceSpy(), {
      localStorageState: {
        userId: 'stored-user',
        page: 1,
        pageSize: 20,
      },
      navigateResult: 'reject',
    });

    expect(component.userIdControl.value).toBe('stored-user');
    expect(component.pageIndex()).toBe(1);
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({
        userId: 'stored-user',
        limit: 21,
        offset: 20,
      }),
    );
  });

  it('shows error message and hides loading when API fails', async () => {
    await init(makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(throwError(() => new Error('Network error'))),
    }));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Failed to load audit logs');
    expect(component.loading()).toBeFalse();
  });

  it('shows retry button on error', async () => {
    await init(makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(throwError(() => new Error('Error'))),
    }));
    fixture.detectChanges();

    const retryBtn = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="retry-button"]');
    expect(retryBtn).toBeTruthy();
    expect(retryBtn?.textContent).toContain('Retry');
  });

  it('clears error and reloads on retry()', async () => {
    const spy = jasmine.createSpy('getAuditLogs')
      .and.returnValues(throwError(() => new Error('fail')), of([MOCK_LOG]));
    await init(makeServiceSpy({ getAuditLogs: spy }));
    fixture.detectChanges();
    expect(component.errorMessage()).toBeTruthy();

    component.retry();
    fixture.detectChanges();
    expect(component.errorMessage()).toBeNull();
  });

  it('resets estimatedTotal to 0 on load error', async () => {
    const successThenFail = jasmine.createSpy('getAuditLogs')
      .and.returnValues(of(Array.from({ length: 21 }, (_, i) => ({ ...MOCK_LOG, id: i + 1 }))), throwError(() => new Error('fail')));

    await init(makeServiceSpy({ getAuditLogs: successThenFail }));
    expect(component.estimatedTotal()).toBe(21);

    component.retry();
    fixture.detectChanges();
    expect(component.estimatedTotal()).toBe(0);
  });

  it('applyFilters resets pageIndex, updates URL params and persists state', async () => {
    await init();
    component.pageIndex.set(3);
    component.userIdControl.setValue(' user-42 ');
    component.eventTypeControl.setValue('USER_LOGGED_IN');
    component.searchControl.setValue('10.0.0.9');
    component.fromDateControl.setValue(new Date(2026, 4, 1));
    component.toDateControl.setValue(new Date(2026, 4, 9));

    component.applyFilters();
    await fixture.whenStable();

    expect(component.pageIndex()).toBe(0);
    expect(routerSpy.navigate).toHaveBeenCalled();
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({
        userId: 'user-42',
        eventType: 'USER_LOGGED_IN',
        search: '10.0.0.9',
        from: startOfLocalDayIso(2026, 4, 1),
        to: endOfLocalDayIso(2026, 4, 9),
        limit: 21,
        offset: 0,
      }),
    );

    const storedState = JSON.parse(localStorage.getItem((AuditDashboardComponent as any).STORAGE_KEY) ?? '{}');
    expect(storedState.search).toBe('10.0.0.9');
    expect(storedState.page).toBe(0);
  });

  it('clearFilters resets state, removes localStorage and reloads without filter params', async () => {
    await init(makeServiceSpy(), {
      localStorageState: {
        userId: 'stored-user',
        search: 'payload',
        page: 1,
        pageSize: 20,
      },
    });

    (serviceSpy.getAuditLogs as jasmine.Spy).calls.reset();
    component.clearFilters();
    await fixture.whenStable();

    expect(component.pageIndex()).toBe(0);
    expect(component.userIdControl.value).toBe('');
    expect(component.searchControl.value).toBe('');
    expect(component.fromDateControl.value).toBeNull();
    expect(localStorage.getItem((AuditDashboardComponent as any).STORAGE_KEY)).toBeNull();
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({ limit: 21, offset: 0, userId: undefined, eventType: undefined, search: undefined, from: undefined, to: undefined }),
    );
  });

  it('onPageChange updates pageSize and pageIndex then reloads through URL state', async () => {
    await init();
    (serviceSpy.getAuditLogs as jasmine.Spy).calls.reset();
    component.onPageChange({ pageSize: 50, pageIndex: 1, length: 100, previousPageIndex: 0 });
    await fixture.whenStable();

    expect(component.pageSize()).toBe(50);
    expect(component.pageIndex()).toBe(1);
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({ limit: 51, offset: 50 }),
    );
  });

  it('estimatedTotal is set correctly when page is not full', async () => {
    const partlyFull = makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(of([MOCK_LOG])),
    });
    await init(partlyFull);
    expect(component.estimatedTotal()).toBe(1);
  });

  it('estimatedTotal assumes next page when response fills limit+1', async () => {
    const fullPageItems = Array.from({ length: 21 }, (_, i) => ({ ...MOCK_LOG, id: i + 1 }));
    const fullSpy = makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(of(fullPageItems)),
    });
    await init(fullSpy);
    expect(component.estimatedTotal()).toBe(21);
    const displayed = component.logs$.value;
    expect(displayed.length).toBe(20);
  });

  it('effectiveIntegrityStatus keeps list status until the drawer updates the row', async () => {
    const pendingRow: AuditLog = { ...MOCK_LOG, integrityStatus: 'PENDING' };
    await init(makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(of([pendingRow])),
      checkIntegrity: jasmine.createSpy().and.returnValue(of({ ...MOCK_INTEGRITY, status: 'MISMATCH' })),
    }));

    expect(component.effectiveIntegrityStatus(pendingRow)).toBe('PENDING');

    component.openDetails(pendingRow);
    await Promise.resolve();

    expect(component.effectiveIntegrityStatus(pendingRow)).toBe('MISMATCH');
  });

  it('effectiveIntegrityStatus preserves original list status when the drawer verification fails', async () => {
    const pendingRow: AuditLog = { ...MOCK_LOG, integrityStatus: 'PENDING' };
    await init(makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(of([pendingRow])),
      checkIntegrity: jasmine.createSpy().and.returnValue(throwError(() => new Error('rpc down'))),
    }));

    expect(component.effectiveIntegrityStatus(pendingRow)).toBe('PENDING');

    component.openDetails(pendingRow);
    await Promise.resolve();

    expect(component.effectiveIntegrityStatus(pendingRow)).toBe('PENDING');
  });

  it('integrityClass returns correct CSS class for each status', async () => {
    await init();
    expect(component.integrityClass('ON_CHAIN')).toBe('status-chip--on_chain');
    expect(component.integrityClass('MISMATCH')).toBe('status-chip--mismatch');
    expect(component.integrityClass('PENDING')).toBe('status-chip--pending');
    expect(component.integrityClass('unknown')).toBe('status-chip--unknown');
  });

  it('openDetails sets selectedAuditLog and triggers integrity check', async () => {
    await init();
    component.openDetails(MOCK_LOG);
    expect(component.selectedAuditLog()).toEqual(MOCK_LOG);
    expect(serviceSpy.checkIntegrity).toHaveBeenCalledWith(MOCK_LOG.id);
    expect(component.integrityCheckResult()).toEqual(MOCK_INTEGRITY);
  });

  it('openDetails sets integrityCheckError when integrity check fails and preserves original row status', async () => {
    await init(makeServiceSpy({
      checkIntegrity: jasmine.createSpy().and.returnValue(throwError(() => new Error('BC error'))),
    }));
    component.openDetails(MOCK_LOG);
    expect(component.integrityCheckError()).toBeTruthy();
    expect(component.integrityCheckResult()).toBeNull();
    expect(component.effectiveIntegrityStatus(MOCK_LOG)).toBe('ON_CHAIN');
  });

  it('openDetails swallows drawer open rejection (non-critical)', async () => {
    await init();
    const drawerMock = {
      open: jasmine.createSpy('open').and.returnValue(Promise.reject(new Error('drawer open failed'))),
      close: jasmine.createSpy('close').and.returnValue(Promise.resolve()),
    };
    Object.defineProperty(component as object, 'detailsDrawer', { value: drawerMock });

    component.openDetails(MOCK_LOG);
    await Promise.resolve();

    expect(drawerMock.open).toHaveBeenCalled();
    expect(component.selectedAuditLog()).toEqual(MOCK_LOG);
  });

  it('closeDetails clears selectedAuditLog and integrity state', async () => {
    await init();
    component.openDetails(MOCK_LOG);
    component.closeDetails();
    expect(component.selectedAuditLog()).toBeNull();
    expect(component.integrityCheckResult()).toBeNull();
    expect(component.integrityCheckError()).toBeNull();
  });

  it('closeDetails swallows drawer close rejection (non-critical)', async () => {
    await init();
    const drawerMock = {
      open: jasmine.createSpy('open').and.returnValue(Promise.resolve()),
      close: jasmine.createSpy('close').and.returnValue(Promise.reject(new Error('drawer close failed'))),
    };
    Object.defineProperty(component as object, 'detailsDrawer', { value: drawerMock });

    component.openDetails(MOCK_LOG);
    component.closeDetails();
    await Promise.resolve();

    expect(drawerMock.close).toHaveBeenCalled();
    expect(component.selectedAuditLog()).toBeNull();
    expect(component.integrityCheckResult()).toBeNull();
    expect(component.integrityCheckError()).toBeNull();
  });

  it('exports current page as CSV', async () => {
    await init();
    component.logs$.next([{ ...MOCK_LOG, eventDataJson: '{"message":"hello, \"world\""}' }]);

    const anchor = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(anchor);
    spyOn(document.body, 'appendChild');
    spyOn(document.body, 'removeChild');
    spyOn(anchor, 'click');
    const createObjectUrlSpy = spyOn(URL, 'createObjectURL').and.returnValue('blob:csv');
    const revokeObjectUrlSpy = spyOn(URL, 'revokeObjectURL');

    component.exportCsv();

    // revokeObjectURL is called inside setTimeout(..., 0); flush via a real microtask tick.
    await new Promise<void>(resolve => setTimeout(resolve, 0));

    expect(anchor.download).toContain('audit-logs-');
    expect(anchor.href).toBe('blob:csv');
    expect(anchor.click).toHaveBeenCalled();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:csv');

    const csvBlob = createObjectUrlSpy.calls.mostRecent().args[0] as Blob;
    await expectAsync(csvBlob.text()).toBeResolvedTo(
      jasmine.stringMatching(/^id,eventId,eventType,userId,occurredAt,integrityStatus,eventHash,eventDataJson\r\n/),
    );
  });

  it('normalizeCsvValue falls back safely for circular objects', async () => {
    await init();

    const circular: { self?: unknown } = {};
    circular.self = circular;

    expect((component as any).normalizeCsvValue(circular)).toBe('[unserializable]');
  });

  it('normalizeCsvValue serializes plain objects and nullish values', async () => {
    await init();

    expect((component as any).normalizeCsvValue({ foo: 'bar' })).toBe('{"foo":"bar"}');
    expect((component as any).normalizeCsvValue(null)).toBe('');
    expect((component as any).normalizeCsvValue(undefined)).toBe('');
  });

  it('parseEventData parses valid JSON string', async () => {
    await init();
    const result = component.parseEventData({ ...MOCK_LOG, eventDataJson: '{"userId":"u1"}' });
    expect(result).toEqual({ userId: 'u1' });
  });

  it('parseEventData returns raw string for invalid JSON', async () => {
    await init();
    const result = component.parseEventData({ ...MOCK_LOG, eventDataJson: 'not-json' });
    expect(result).toBe('not-json');
  });

  it('does not apply pending load result after fixture destroy', async () => {
    const pendingList$ = new Subject<AuditLog[]>();
    await init(makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(pendingList$),
    }));

    fixture.destroy();
    pendingList$.next([MOCK_LOG]);
    pendingList$.complete();

    expect(component.logs$.value).toEqual([]);
  });

  it('applyFilters reloads when navigation is ignored for the same URL', async () => {
    await init();
    (serviceSpy.getAuditLogs as jasmine.Spy).calls.reset();

    routerSpy.navigate.and.returnValue(Promise.resolve(false));

    component.applyFilters();
    await fixture.whenStable();

    expect(routerSpy.navigate).toHaveBeenCalled();
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledTimes(1);
  });
});
