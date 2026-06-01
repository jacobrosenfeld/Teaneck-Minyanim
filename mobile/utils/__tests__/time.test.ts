import { describe, expect, it } from 'vitest';

import { formatTime } from '../time';

describe('formatTime', () => {
  it('formats minute-only API zmanim without seconds', () => {
    expect(formatTime('18:42')).toBe('6:42 PM');
  });

  it('drops seconds from candle lighting and havdala times', () => {
    expect(formatTime('18:42:59')).toBe('6:42 PM');
    expect(formatTime('20:13:01')).toBe('8:13 PM');
  });
});
