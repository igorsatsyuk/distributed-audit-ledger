import { AuditLog } from '../../models/audit-log.model';

export interface TimelineHourGroup {
  key: string;
  label: string;
  total: number;
  events: AuditLog[];
}

export interface TimelineDayGroup {
  key: string;
  label: string;
  total: number;
  hours: TimelineHourGroup[];
}

interface DayAccumulator {
  key: string;
  label: string;
  total: number;
  hours: Map<string, HourAccumulator>;
}

interface HourAccumulator {
  key: string;
  label: string;
  total: number;
  events: AuditLog[];
}

export function groupAuditLogsByTimeline(logs: readonly AuditLog[]): TimelineDayGroup[] {
  const sortedLogs = logs
    .map(log => ({ log, date: new Date(log.occurredAt) }))
    .filter(({ date }) => !Number.isNaN(date.getTime()))
    .sort((left, right) => right.date.getTime() - left.date.getTime() || right.log.id - left.log.id);

  const dayBuckets = new Map<string, DayAccumulator>();

  for (const { log, date } of sortedLogs) {
    const dayKey = buildDayKey(date);
    const hourKey = buildHourKey(date);
    const dayLabel = buildDayLabel(date);
    const hourLabel = buildHourLabel(date);

    let dayBucket = dayBuckets.get(dayKey);
    if (!dayBucket) {
      dayBucket = {
        key: dayKey,
        label: dayLabel,
        total: 0,
        hours: new Map<string, HourAccumulator>(),
      };
      dayBuckets.set(dayKey, dayBucket);
    }

    dayBucket.total += 1;

    let hourBucket = dayBucket.hours.get(hourKey);
    if (!hourBucket) {
      hourBucket = {
        key: hourKey,
        label: hourLabel,
        total: 0,
        events: [],
      };
      dayBucket.hours.set(hourKey, hourBucket);
    }

    hourBucket.total += 1;
    hourBucket.events.push(log);
  }

  return Array.from(dayBuckets.values()).map(day => ({
    key: day.key,
    label: day.label,
    total: day.total,
    hours: Array.from(day.hours.values()).map(hour => ({
      key: hour.key,
      label: hour.label,
      total: hour.total,
      events: hour.events,
    })),
  }));
}

function buildDayKey(date: Date): string {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

function buildHourKey(date: Date): string {
  return `${buildDayKey(date)} ${pad(date.getUTCHours())}:00`;
}

function buildDayLabel(date: Date): string {
  return buildDayKey(date);
}

function buildHourLabel(date: Date): string {
  return `${pad(date.getUTCHours())}:00`;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

