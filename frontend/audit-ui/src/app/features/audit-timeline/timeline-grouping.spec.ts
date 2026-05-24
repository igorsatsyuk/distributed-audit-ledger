import { AuditLog } from '../../models/audit-log.model';
import { groupAuditLogsByTimeline } from './timeline-grouping';

const buildLog = (overrides: Partial<AuditLog>): AuditLog => ({
  id: 1,
  eventId: 'event-1',
  eventType: 'USER_LOGGED_IN',
  userId: 'user-1',
  occurredAt: '2026-05-24T10:15:00Z',
  eventDataJson: '{}',
  integrityStatus: 'ON_CHAIN',
  ...overrides,
});

describe('groupAuditLogsByTimeline', () => {
  it('returns empty groups for empty input', () => {
    expect(groupAuditLogsByTimeline([])).toEqual([]);
  });

  it('groups logs by day and hour in descending order', () => {
    const groups = groupAuditLogsByTimeline([
      buildLog({ id: 1, eventId: 'event-1', occurredAt: '2026-05-24T10:15:00Z' }),
      buildLog({ id: 2, eventId: 'event-2', occurredAt: '2026-05-24T10:45:00Z' }),
      buildLog({ id: 3, eventId: 'event-3', occurredAt: '2026-05-23T08:00:00Z' }),
    ]);

    expect(groups.length).toBe(2);
    expect(groups[0].key).toBe('2026-05-24');
    expect(groups[0].total).toBe(2);
    expect(groups[0].hours.length).toBe(1);
    expect(groups[0].hours[0].key).toBe('2026-05-24 10:00');
    expect(groups[0].hours[0].total).toBe(2);
    expect(groups[0].hours[0].events.map(event => event.id)).toEqual([2, 1]);
    expect(groups[1].key).toBe('2026-05-23');
    expect(groups[1].hours[0].events[0].id).toBe(3);
  });

  it('ignores logs with invalid dates', () => {
    const groups = groupAuditLogsByTimeline([
      buildLog({ id: 1, occurredAt: 'invalid-date' }),
      buildLog({ id: 2, eventId: 'event-2', occurredAt: '2026-05-24T10:15:00Z' }),
    ]);

    expect(groups.length).toBe(1);
    expect(groups[0].total).toBe(1);
    expect(groups[0].hours[0].events[0].id).toBe(2);
  });
});

