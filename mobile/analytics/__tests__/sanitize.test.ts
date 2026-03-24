import { describe, expect, it } from 'vitest';

import { sanitizeProperties } from '../sanitize';

describe('sanitizeProperties', () => {
  it('drops blocked PII-like fields', () => {
    const sanitized = sanitizeProperties({
      org_slug: 'beth-aaron',
      minyan_type: 'SHACHARIS',
      email: 'user@example.com',
      phone_number: '+1 (201) 555-0123',
      latitude: 40.0,
      nested: {
        address_line_1: '123 Main St',
        safe_value: 'ok',
      },
    });

    expect(sanitized).toEqual({
      org_slug: 'beth-aaron',
      minyan_type: 'SHACHARIS',
      nested: {
        safe_value: 'ok',
      },
    });
  });

  it('preserves non-PII primitives', () => {
    const sanitized = sanitizeProperties({
      screen: 'minyanim',
      selected_filter: 'MAARIV',
      count: 2,
      refreshed: true,
    });

    expect(sanitized).toEqual({
      screen: 'minyanim',
      selected_filter: 'MAARIV',
      count: 2,
      refreshed: true,
    });
  });
});
