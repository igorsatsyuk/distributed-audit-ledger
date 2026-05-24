import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { AuditLog, IntegrityStatus } from '../../models/audit-log.model';
import { groupAuditLogsByTimeline, TimelineDayGroup } from './timeline-grouping';

@Component({
  selector: 'app-audit-timeline',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audit-timeline.component.html',
  styleUrl: './audit-timeline.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditTimelineComponent {
  @Input() selectedId: number | null = null;

  @Output() readonly logSelected = new EventEmitter<AuditLog>();

  timelineGroups: TimelineDayGroup[] = [];

  @Input()
  set logs(value: readonly AuditLog[] | null) {
    this.timelineGroups = groupAuditLogsByTimeline(value ?? []);
  }

  selectLog(log: AuditLog): void {
    this.logSelected.emit(log);
  }

  isSelected(log: AuditLog): boolean {
    return this.selectedId === log.id;
  }

  statusClass(status: IntegrityStatus): string {
    switch (status) {
      case 'ON_CHAIN':
        return 'timeline-event--on-chain';
      case 'MISMATCH':
        return 'timeline-event--mismatch';
      case 'PENDING':
        return 'timeline-event--pending';
      default:
        return 'timeline-event--unknown';
    }
  }
}

