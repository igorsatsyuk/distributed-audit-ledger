import { ApproximatePaginatorIntl } from './approximate-paginator-intl';

describe('ApproximatePaginatorIntl', () => {
  it('shows plain range label when hasMore is false', () => {
    const intl = new ApproximatePaginatorIntl();

    expect(intl.getRangeLabel(0, 20, 20)).toBe('1 – 20 of 20');
  });

  it('shows "at least" hint when hasMore is true', () => {
    const intl = new ApproximatePaginatorIntl();
    intl.setHasMore(true);

    expect(intl.getRangeLabel(0, 20, 21)).toBe('1 – 20 of at least 21');
  });

  it('shows "0 of at least N" for empty result when hasMore is true', () => {
    const intl = new ApproximatePaginatorIntl();
    intl.setHasMore(true);

    expect(intl.getRangeLabel(0, 20, 0)).toBe('0 of at least 0');
  });
});

