export interface AnalyticsConfig {
  posthogKey: string;
  posthogHost: string;
  analyticsGloballyEnabled: boolean;
  sessionReplayEnabled: boolean;
  sessionReplaySampleRate: number;
}

const TRUE_VALUES = new Set(['1', 'true', 'yes', 'on']);

function parseBoolean(value: string | undefined, fallback: boolean): boolean {
  if (value == null) return fallback;
  return TRUE_VALUES.has(value.trim().toLowerCase());
}

export function getAnalyticsConfig(): AnalyticsConfig {
  return {
    posthogKey: process.env.EXPO_PUBLIC_POSTHOG_KEY?.trim() ?? '',
    posthogHost: process.env.EXPO_PUBLIC_POSTHOG_HOST?.trim() || 'https://us.i.posthog.com',
    analyticsGloballyEnabled: parseBoolean(process.env.EXPO_PUBLIC_ANALYTICS_ENABLED, false),
    sessionReplayEnabled: parseBoolean(process.env.EXPO_PUBLIC_SESSION_REPLAY_ENABLED, false),
    sessionReplaySampleRate: 0.2,
  };
}
