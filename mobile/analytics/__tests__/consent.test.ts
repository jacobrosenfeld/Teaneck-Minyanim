import { describe, expect, it } from 'vitest';

import {
  computeAnalyticsEnabled,
  mapTrackingPermissionStatus,
  transitionConsentState,
} from '../consent';

describe('transitionConsentState', () => {
  it('moves unknown -> accepted', () => {
    expect(transitionConsentState('unknown', 'accept')).toBe('accepted');
  });

  it('moves accepted -> declined', () => {
    expect(transitionConsentState('accepted', 'decline')).toBe('declined');
  });

  it('resets any state to unknown', () => {
    expect(transitionConsentState('declined', 'reset')).toBe('unknown');
  });
});

describe('mapTrackingPermissionStatus', () => {
  it('maps iOS granted to authorized', () => {
    expect(mapTrackingPermissionStatus('granted', 'ios')).toBe('authorized');
  });

  it('maps iOS denied to denied', () => {
    expect(mapTrackingPermissionStatus('denied', 'ios')).toBe('denied');
  });

  it('maps Android to not_required', () => {
    expect(mapTrackingPermissionStatus('granted', 'android')).toBe('not_required');
  });
});

describe('computeAnalyticsEnabled', () => {
  it('enables iOS when consent is accepted, regardless of ATT status', () => {
    expect(computeAnalyticsEnabled('accepted', 'authorized', 'ios', true)).toBe(true);
    expect(computeAnalyticsEnabled('accepted', 'denied', 'ios', true)).toBe(true);
    expect(computeAnalyticsEnabled('accepted', 'unknown', 'ios', true)).toBe(true);
  });

  it('enables Android when consent accepted', () => {
    expect(computeAnalyticsEnabled('accepted', 'not_required', 'android', true)).toBe(true);
  });

  it('disables when consent declined or global kill switch off', () => {
    expect(computeAnalyticsEnabled('declined', 'authorized', 'ios', true)).toBe(false);
    expect(computeAnalyticsEnabled('accepted', 'authorized', 'ios', false)).toBe(false);
  });
});
