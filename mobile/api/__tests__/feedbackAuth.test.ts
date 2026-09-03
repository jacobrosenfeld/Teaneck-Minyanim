import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockState = vi.hoisted(() => ({
  extra: {
    feedbackConfig: {
      appToken: 'extra-token',
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

async function loadFeedbackAuthModule() {
  return import('../feedbackAuth');
}

describe('mobile feedback auth config', () => {
  beforeEach(() => {
    vi.resetModules();
    delete process.env.EXPO_PUBLIC_FEEDBACK_APP_TOKEN;
    mockState.extra = {
      feedbackConfig: {
        appToken: 'extra-token',
      },
    };
  });

  it('falls back to Expo extra config when EXPO_PUBLIC_FEEDBACK_APP_TOKEN is absent', async () => {
    const {
      getMobileFeedbackAppToken,
      isMobileFeedbackConfigured,
      mobileFeedbackHeaders,
      MOBILE_FEEDBACK_APP_TOKEN_HEADER,
    } = await loadFeedbackAuthModule();

    expect(getMobileFeedbackAppToken()).toBe('extra-token');
    expect(isMobileFeedbackConfigured()).toBe(true);
    expect(mobileFeedbackHeaders()).toEqual({
      [MOBILE_FEEDBACK_APP_TOKEN_HEADER]: 'extra-token',
    });
  });

  it('prioritizes EXPO_PUBLIC_FEEDBACK_APP_TOKEN over Expo extra config', async () => {
    process.env.EXPO_PUBLIC_FEEDBACK_APP_TOKEN = 'env-token';
    const { getMobileFeedbackAppToken, mobileFeedbackHeaders, MOBILE_FEEDBACK_APP_TOKEN_HEADER } =
      await loadFeedbackAuthModule();

    expect(getMobileFeedbackAppToken()).toBe('env-token');
    expect(mobileFeedbackHeaders()).toEqual({
      [MOBILE_FEEDBACK_APP_TOKEN_HEADER]: 'env-token',
    });
  });

  it('returns no auth header when no token is configured', async () => {
    mockState.extra = { feedbackConfig: { appToken: '' } };
    const { getMobileFeedbackAppToken, isMobileFeedbackConfigured, mobileFeedbackHeaders } =
      await loadFeedbackAuthModule();

    expect(getMobileFeedbackAppToken()).toBe('');
    expect(isMobileFeedbackConfigured()).toBe(false);
    expect(mobileFeedbackHeaders()).toEqual({});
  });
});
