import { beforeEach, describe, expect, it } from 'vitest';

import {
  findOrganizationForFeedbackContext,
  getFeedbackNavigationContext,
  setFeedbackNavigationContext,
} from '../feedbackContext';
import type { Organization } from '@/api/types';

const orgs: Organization[] = [
  {
    id: 'org-1',
    name: 'First Shul',
    slug: 'first-shul',
    color: '#275ED8',
    nusach: null,
    nusachDisplay: null,
    address: null,
    websiteUrl: null,
    whatsapp: null,
    latitude: null,
    longitude: null,
  },
];

describe('feedback navigation context', () => {
  beforeEach(() => {
    setFeedbackNavigationContext('/', {});
  });

  it('stores the last non-feedback route and matches organization context', () => {
    setFeedbackNavigationContext('/shuls/[id]', { id: 'first-shul' });

    const context = getFeedbackNavigationContext();

    expect(context.pathname).toBe('/shuls/[id]');
    expect(context.screen).toBe('organization-detail');
    expect(findOrganizationForFeedbackContext(context, orgs)?.name).toBe('First Shul');
  });

  it('ignores feedback routes so the source page is retained', () => {
    setFeedbackNavigationContext('/map', {});
    setFeedbackNavigationContext('/feedback', {});

    expect(getFeedbackNavigationContext().pathname).toBe('/map');
  });
});
