import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuditTimelineComponent } from './audit-timeline.component';
import { AuditLog } from '../../models/audit-log.model';

describe('AuditTimelineComponent', () => {
  let fixture: ComponentFixture<AuditTimelineComponent>;
  let component: AuditTimelineComponent;

  const logs: AuditLog[] = [
    {
      id: 1,
      eventId: 'event-1',
      eventType: 'USER_LOGGED_IN',
      userId: 'user-1',
      occurredAt: '2026-05-24T10:15:00Z',
      eventDataJson: '{}',
      eventHash: 'hash-1',
      integrityStatus: 'ON_CHAIN',
    },
    {
      id: 2,
      eventId: 'event-2',
      eventType: 'DATA_UPDATED',
      userId: 'user-2',
      occurredAt: '2026-05-24T10:45:00Z',
      eventDataJson: '{}',
      integrityStatus: 'PENDING',
    },
    {
      id: 3,
      eventId: 'event-3',
      eventType: 'DATA_DELETED',
      userId: null,
      occurredAt: '2026-05-23T08:00:00Z',
      eventDataJson: '{}',
      integrityStatus: 'MISMATCH',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditTimelineComponent],
    }).compileComponents();
  });

  function createComponent(selectedId: number | null = null): void {
    fixture = TestBed.createComponent(AuditTimelineComponent);
    component = fixture.componentInstance;
    component.selectedId = selectedId;
    component.logs = logs;
    fixture.detectChanges();
  }

  it('renders an empty state when there are no logs', async () => {
    fixture = TestBed.createComponent(AuditTimelineComponent);
    component = fixture.componentInstance;
    component.logs = [];
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="timeline-empty"]')).toBeTruthy();
  });

  it('renders grouped timeline data', async () => {
    createComponent();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('[data-testid^="timeline-day-"]').length).toBe(2);
    expect(el.querySelectorAll('[data-testid^="timeline-hour-"]').length).toBe(2);
    expect(el.querySelector('[data-testid="timeline-event-2"]')).toBeTruthy();
    expect(el.textContent).toContain('2026-05-24');
    expect(el.textContent).toContain('10:00');
    expect(el.textContent).toContain('USER_LOGGED_IN');
  });

  it('emits selected log when an event is clicked', async () => {
    createComponent();
    spyOn(component.logSelected, 'emit');

    const button = fixture.nativeElement.querySelector('[data-testid="timeline-event-2"]') as HTMLButtonElement;
    button.click();

    expect(component.logSelected.emit).toHaveBeenCalledWith(logs[1]);
  });

  it('marks the selected event with the active class', async () => {
    createComponent(2);

    const button = fixture.nativeElement.querySelector('[data-testid="timeline-event-2"]') as HTMLButtonElement;
    expect(button.classList.contains('timeline-event--selected')).toBeTrue();
    expect(button.getAttribute('aria-current')).toBe('true');
  });
});


