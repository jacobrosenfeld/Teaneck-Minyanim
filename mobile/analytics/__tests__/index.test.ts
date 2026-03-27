import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockState = vi.hoisted(() => ({
  platform: 'ios' as 'ios' | 'android' | 'web',
  config: {
    posthogKey: 'phc_test_key',
    posthogHost: 'https://t.teaneckminyanim.com',
    analyticsGloballyEnabled: true,
    sessionReplayEnabled: false,
    sessionReplaySampleRate: 0.2,
  },
  stored: {
    consent: 'unknown' as const,
    platformTrackingPermission: 'unknown' as const,
  },
  requestTrackingStatus: 'denied',
  getTrackingStatus: 'denied',
  advertisingId: 'ios-ad-id-123',
  posthogInstances: [] as any[],
  requestTrackingCalls: 0,
  getTrackingCalls: 0,
  advertisingIdCalls: 0,
}));

vi.mock('react-native', () => ({
  Platform: {
    get OS() {
      return mockState.platform;
    },
  },
}));

vi.mock('posthog-react-native', () => {
  class MockPostHog {
    capture = vi.fn();
    optIn = vi.fn(async () => {});
    optOut = vi.fn(async () => {});
    register = vi.fn(async () => {});
    unregister = vi.fn(async () => {});

    constructor(
      public apiKey: string,
      public options: Record<string, unknown>,
    ) {
      mockState.posthogInstances.push(this);
    }
  }

  return { default: MockPostHog };
});

vi.mock('../config', () => ({
  getAnalyticsConfig: () => mockState.config,
}));

vi.mock('../storage', () => ({
  loadStoredConsentState: async () => mockState.stored,
  persistStoredConsentState: async () => {},
}));

vi.mock('expo-tracking-transparency', () => ({
  getTrackingPermissionsAsync: async () => {
    mockState.getTrackingCalls += 1;
    return { status: mockState.getTrackingStatus };
  },
  requestTrackingPermissionsAsync: async () => {
    mockState.requestTrackingCalls += 1;
    return { status: mockState.requestTrackingStatus };
  },
  getAdvertisingId: () => {
    mockState.advertisingIdCalls += 1;
    return mockState.advertisingId;
  },
}));

async function loadAnalyticsModule() {
  return import('../index');
}

describe('analytics runtime state', () => {
  beforeEach(() => {
    vi.resetModules();
    mockState.platform = 'ios';
    mockState.config = {
      posthogKey: 'phc_test_key',
      posthogHost: 'https://t.teaneckminyanim.com',
      analyticsGloballyEnabled: true,
      sessionReplayEnabled: false,
      sessionReplaySampleRate: 0.2,
    };
    mockState.stored = {
      consent: 'unknown',
      platformTrackingPermission: 'unknown',
    };
    mockState.requestTrackingStatus = 'denied';
    mockState.getTrackingStatus = 'denied';
    mockState.advertisingId = 'ios-ad-id-123';
    mockState.posthogInstances = [];
    mockState.requestTrackingCalls = 0;
    mockState.getTrackingCalls = 0;
    mockState.advertisingIdCalls = 0;
  });

  it('captures events on iOS after consent even when ATT is denied, without registering advertising_id', async () => {
    const analytics = await loadAnalyticsModule();

    await analytics.initAnalytics();
    const accepted = await analytics.setConsent('accept');

    expect(accepted.analyticsEnabled).toBe(true);
    expect(mockState.requestTrackingCalls).toBe(1);
    expect(mockState.posthogInstances).toHaveLength(1);

    const posthog = mockState.posthogInstances[0];
    expect(posthog.optOut).not.toHaveBeenCalled();
    expect(posthog.register).not.toHaveBeenCalled();
    expect(posthog.unregister).toHaveBeenCalledWith('advertising_id');

    const didCapture = analytics.capture('screen_view', { pathname: '/(tabs)' });
    expect(didCapture).toBe(true);
    expect(posthog.capture).toHaveBeenCalledWith('screen_view', { pathname: '/(tabs)' });
  });

  it('captures events and registers advertising_id when iOS ATT is authorized', async () => {
    mockState.requestTrackingStatus = 'granted';
    mockState.getTrackingStatus = 'granted';

    const analytics = await loadAnalyticsModule();
    await analytics.initAnalytics();

    const accepted = await analytics.setConsent('accept');
    expect(accepted.analyticsEnabled).toBe(true);
    expect(mockState.posthogInstances).toHaveLength(1);

    const posthog = mockState.posthogInstances[0];
    expect(posthog.optOut).not.toHaveBeenCalled();
    expect(posthog.register).toHaveBeenCalledWith({ advertising_id: 'ios-ad-id-123' });
    expect(mockState.advertisingIdCalls).toBe(1);

    const didCapture = analytics.capture('screen_view', { pathname: '/(tabs)' });
    expect(didCapture).toBe(true);
    expect(posthog.capture).toHaveBeenCalledWith('screen_view', { pathname: '/(tabs)' });
  });

  it('blocks capture when consent is declined', async () => {
    mockState.requestTrackingStatus = 'granted';
    mockState.getTrackingStatus = 'granted';

    const analytics = await loadAnalyticsModule();
    await analytics.initAnalytics();
    await analytics.setConsent('accept');

    const declined = await analytics.setConsent('decline');
    expect(declined.analyticsEnabled).toBe(false);
    expect(mockState.posthogInstances).toHaveLength(1);

    const posthog = mockState.posthogInstances[0];
    expect(posthog.optOut).toHaveBeenCalled();
    expect(posthog.unregister).toHaveBeenCalledWith('advertising_id');
    expect(analytics.capture('screen_view', { pathname: '/(tabs)' })).toBe(false);
  });

  it('blocks capture when global analytics kill switch is disabled', async () => {
    mockState.config.analyticsGloballyEnabled = false;

    const analytics = await loadAnalyticsModule();
    await analytics.initAnalytics();

    const accepted = await analytics.setConsent('accept');
    expect(accepted.analyticsEnabled).toBe(false);
    expect(mockState.posthogInstances).toHaveLength(0);
    expect(analytics.capture('screen_view', { pathname: '/(tabs)' })).toBe(false);
  });
});
