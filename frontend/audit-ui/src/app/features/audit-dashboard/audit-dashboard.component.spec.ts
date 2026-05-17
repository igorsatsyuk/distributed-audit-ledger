import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AuditDashboardComponent } from './audit-dashboard.component';
import { AuditLogService } from '../../services/audit-log.service';

describe('AuditDashboardComponent', () => {
  let component: AuditDashboardComponent;
  let fixture: ComponentFixture<AuditDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditDashboardComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: AuditLogService,
          useValue: {
            getAuditLogs: () =>
              of([
                {
                  id: 1,
                  eventId: 'event-1',
                  eventType: 'USER_LOGIN',
                  userId: 'user1',
                  createdAt: '2026-05-17T10:15:00Z',
                  status: 'ON_CHAIN',
                  payload: { key: 'value' },
                },
              ]),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates component', () => {
    expect(component).toBeTruthy();
  });

  it('renders audit table title', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Audit log table');
  });
});

