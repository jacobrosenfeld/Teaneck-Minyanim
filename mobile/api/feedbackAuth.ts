import Constants from 'expo-constants';

interface FeedbackExtraConfig {
  appToken?: string;
}

export const MOBILE_FEEDBACK_APP_TOKEN_HEADER = 'X-Teaneck-Minyanim-App-Token';

function getExtraConfig(): FeedbackExtraConfig {
  const rootExtra =
    ((Constants as any).expoConfig?.extra ?? (Constants as any).manifest2?.extra ?? {}) as Record<
      string,
      unknown
    >;
  const feedbackConfig = rootExtra.feedbackConfig;

  if (!feedbackConfig || typeof feedbackConfig !== 'object') {
    return {};
  }

  return feedbackConfig as FeedbackExtraConfig;
}

function coerceString(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

export function getMobileFeedbackAppToken(): string {
  const extraConfig = getExtraConfig();
  return process.env.EXPO_PUBLIC_FEEDBACK_APP_TOKEN?.trim()
    || coerceString(extraConfig.appToken)
    || '';
}

export function isMobileFeedbackConfigured(): boolean {
  return getMobileFeedbackAppToken().length > 0;
}

export function mobileFeedbackHeaders(): Record<string, string> {
  const token = getMobileFeedbackAppToken();
  if (!token) {
    return {};
  }
  return {
    [MOBILE_FEEDBACK_APP_TOKEN_HEADER]: token,
  };
}
