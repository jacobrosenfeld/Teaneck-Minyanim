import Constants from 'expo-constants';

export interface AnalyticsConfig {
  posthogKey: string;
  posthogHost: string;
  analyticsGloballyEnabled: boolean;
  sessionReplayEnabled: boolean;
  sessionReplaySampleRate: number;
}

interface AnalyticsExtraConfig {
  posthogKey?: string;
  posthogHost?: string;
  analyticsGloballyEnabled?: boolean | string;
  sessionReplayEnabled?: boolean | string;
}

const TRUE_VALUES = new Set(['1', 'true', 'yes', 'on']);

function parseBoolean(value: string | undefined, fallback: boolean): boolean {
  if (value == null) return fallback;
  return TRUE_VALUES.has(value.trim().toLowerCase());
}

function getExtraConfig(): AnalyticsExtraConfig {
  const rootExtra =
    ((Constants as any).expoConfig?.extra ?? (Constants as any).manifest2?.extra ?? {}) as Record<
      string,
      unknown
    >;
  const analyticsConfig = rootExtra.analyticsConfig;

  if (!analyticsConfig || typeof analyticsConfig !== 'object') {
    return {};
  }

  return analyticsConfig as AnalyticsExtraConfig;
}

function coerceString(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

export function getAnalyticsConfig(): AnalyticsConfig {
  const extraConfig = getExtraConfig();
  const posthogKeyFromExtra = coerceString(extraConfig.posthogKey);
  const posthogHostFromExtra = coerceString(extraConfig.posthogHost);
  const analyticsEnabledFromExtra =
    typeof extraConfig.analyticsGloballyEnabled === 'boolean'
      ? extraConfig.analyticsGloballyEnabled
      : parseBoolean(extraConfig.analyticsGloballyEnabled, false);
  const sessionReplayEnabledFromExtra =
    typeof extraConfig.sessionReplayEnabled === 'boolean'
      ? extraConfig.sessionReplayEnabled
      : parseBoolean(extraConfig.sessionReplayEnabled, false);

  return {
    posthogKey: process.env.EXPO_PUBLIC_POSTHOG_KEY?.trim() || posthogKeyFromExtra || '',
    posthogHost:
      process.env.EXPO_PUBLIC_POSTHOG_HOST?.trim() ||
      posthogHostFromExtra ||
      'https://us.i.posthog.com',
    analyticsGloballyEnabled:
      parseBoolean(process.env.EXPO_PUBLIC_ANALYTICS_ENABLED, analyticsEnabledFromExtra),
    sessionReplayEnabled: parseBoolean(
      process.env.EXPO_PUBLIC_SESSION_REPLAY_ENABLED,
      sessionReplayEnabledFromExtra,
    ),
    sessionReplaySampleRate: 0.2,
  };
}
