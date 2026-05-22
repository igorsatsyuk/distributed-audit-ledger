import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/**
 * Custom paginator label that shows "at least N" when the server has more results
 * than the current page (detected by fetching `limit + 1` items).
 */
@Injectable()
export class ApproximatePaginatorIntl extends MatPaginatorIntl {
  private hasMore = false;

  setHasMore(hasMore: boolean): void {
    this.hasMore = hasMore;
    this.changes.next();
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return `0 of ${this.hasMore ? 'at least ' : ''}${length}`;
    }

    const startIndex = page * pageSize;
    const endIndex = Math.min(startIndex + pageSize, length);
    return this.hasMore
      ? `${startIndex + 1} – ${endIndex} of at least ${length}`
      : `${startIndex + 1} – ${endIndex} of ${length}`;
  };
}

