import { of, throwError } from 'rxjs';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
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

describe('AuditDashboardComponent', () => {
  let component: AuditDashboardComponent;
  let fixture: ComponentFixture<AuditDashboardComponent>;
  let serviceSpy: ReturnType<typeof makeServiceSpy>;

  async function init(spy = makeServiceSpy()) {
    serviceSpy = spy;
    await TestBed.configureTestingModule({
      imports: [AuditDashboardComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AuditLogService, useValue: spy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('creates the component', async () => {
    await init();
    expect(component).toBeTruthy();
  });

  it('renders audit table title', async () => {
    await init();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Audit log');
  });

  it('calls getAuditLogs on init with default page size and offset 0', async () => {
    await init();
    // limit = pageSize + 1 (one extra to detect next page)
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({ limit: 21, offset: 0 }),
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
    const retryBtn = (fixture.nativeElement as HTMLElement).querySelector('button[color="primary"]');
    expect(retryBtn).toBeTruthy();
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

  it('applyFilters resets pageIndex to 0', async () => {
    await init();
    component.pageIndex.set(3);
    component.applyFilters();
    expect(component.pageIndex()).toBe(0);
  });

  it('clearFilters resets pageIndex and clears controls', async () => {
    await init();
    component.userIdControl.setValue('user1');
    component.eventTypeControl.setValue('LOGIN');
    component.pageIndex.set(2);
    component.clearFilters();
    expect(component.pageIndex()).toBe(0);
    expect(component.userIdControl.value).toBe('');
    expect(component.eventTypeControl.value).toBe('');
  });

  it('onPageChange updates pageSize and pageIndex then reloads', fakeAsync(async () => {
    await init();
    (serviceSpy.getAuditLogs as jasmine.Spy).calls.reset();
    component.onPageChange({ pageSize: 50, pageIndex: 1, length: 100, previousPageIndex: 0 });
    tick();
    expect(component.pageSize()).toBe(50);
    expect(component.pageIndex()).toBe(1);
    // limit = pageSize + 1 = 51, offset = pageIndex * pageSize = 50
    expect(serviceSpy.getAuditLogs).toHaveBeenCalledWith(
      jasmine.objectContaining({ limit: 51, offset: 50 }),
    );
  }));

  it('estimatedTotal is set correctly when page is not full', async () => {
    const partlyFull = makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(of([MOCK_LOG])),
    });
    await init(partlyFull);
    // requested limit=21, got 1 item (1 < 21) → no next page → total = 1
    expect(component.estimatedTotal()).toBe(1);
  });

  it('estimatedTotal assumes next page when response fills limit+1', async () => {
    // Return pageSize+1 items to signal that another page exists
    const fullPageItems = Array.from({ length: 21 }, (_, i) => ({ ...MOCK_LOG, id: i + 1 }));
    const fullSpy = makeServiceSpy({
      getAuditLogs: jasmine.createSpy().and.returnValue(of(fullPageItems)),
    });
    await init(fullSpy);
    // 21 > 20 → hasMore: true → display 20 items, total = 0 + 20 + 20 = 40
    expect(component.estimatedTotal()).toBe(40);
    // Only pageSize items shown, not the sentinel extra
    const displayed = await new Promise<AuditLog[]>(res => component.logs$.subscribe(res));
    expect(displayed.length).toBe(20);
  });

  it('integrityClass returns correct CSS class for each status', async () => {
    await init();
    expect(component.integrityClass('ON_CHAIN')).toBe('status-chip--on_chain');
    expect(component.integrityClass('MISMATCH')).toBe('status-chip--mismatch');
    expect(component.integrityClass('PENDING')).toBe('status-chip--pending');
    expect(component.integrityClass('unknown')).toBe('status-chip--pending');
  });

  it('openDetails sets selectedAuditLog and triggers integrity check', fakeAsync(async () => {
    await init();
    component.openDetails(MOCK_LOG);
    tick();
    expect(component.selectedAuditLog()).toEqual(MOCK_LOG);
    expect(serviceSpy.checkIntegrity).toHaveBeenCalledWith(MOCK_LOG.id);
    expect(component.integrityCheckResult()).toEqual(MOCK_INTEGRITY);
  }));

  it('openDetails sets integrityCheckError when integrity check fails', fakeAsync(async () => {
    await init(makeServiceSpy({
      checkIntegrity: jasmine.createSpy().and.returnValue(throwError(() => new Error('BC error'))),
    }));
    component.openDetails(MOCK_LOG);
    tick();
    expect(component.integrityCheckError()).toBeTruthy();
    expect(component.integrityCheckResult()).toBeNull();
  }));

  it('closeDetails clears selectedAuditLog and integrity state', async () => {
    await init();
    component.openDetails(MOCK_LOG);
    component.closeDetails();
    expect(component.selectedAuditLog()).toBeNull();
    expect(component.integrityCheckResult()).toBeNull();
    expect(component.integrityCheckError()).toBeNull();
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
});
