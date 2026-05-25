import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockState = vi.hoisted(() => ({
  extra: {
    analyticsConfig: {
      analyticsGloballyEnabled: true,
      posthogKey: 'phc_extra_key',
      posthogHost: 'https://t.teaneckminyanim.com',
      sessionReplayEnabled: true,
    },
  },
}));

vi.mock('expo-constants', () => ({
  default: {
    get expoConfig() {
      return { extra: mockState.extra };
    },
  },
}));

function clearAnalyticsEnv() {
  delete process.env.EXPO_PUBLIC_ANALYTICS_ENABLED;
  delete process.env.EXPO_PUBLIC_POSTHOG_KEY;
  delete process.env.EXPO_PUBLIC_POSTHOG_HOST;
  delete process.env.EXPO_PUBLIC_SESSION_REPLAY_ENABLED;
}

async function loadConfigModule() {
  return import('../config');
}

describe('getAnalyticsConfig', () => {
  beforeEach(() => {
    vi.resetModules();
    clearAnalyticsEnv();
  });

  it('falls back to Expo extra analytics config when EXPO_PUBLIC vars are absent', async () => {
    const { getAnalyticsConfig } = await loadConfigModule();
    const config = getAnalyticsConfig();

    expect(config.analyticsGloballyEnabled).toBe(true);
    expect(config.posthogKey).toBe('phc_extra_key');
    expect(config.posthogHost).toBe('https://t.teaneckminyanim.com');
    expect(config.sessionReplayEnabled).toBe(true);
  });

  it('prioritizes EXPO_PUBLIC vars over Expo extra analytics config', async () => {
    process.env.EXPO_PUBLIC_ANALYTICS_ENABLED = 'false';
    process.env.EXPO_PUBLIC_POSTHOG_KEY = 'phc_env_key';
    process.env.EXPO_PUBLIC_POSTHOG_HOST = 'https://us.i.posthog.com';
    process.env.EXPO_PUBLIC_SESSION_REPLAY_ENABLED = 'false';

    const { getAnalyticsConfig } = await loadConfigModule();
    const config = getAnalyticsConfig();

    expect(config.analyticsGloballyEnabled).toBe(false);
    expect(config.posthogKey).toBe('phc_env_key');
    expect(config.posthogHost).toBe('https://us.i.posthog.com');
    expect(config.sessionReplayEnabled).toBe(false);
  });
});
