export type ConsentState = 'unknown' | 'accepted' | 'declined';

export type PlatformTrackingPermission =
  | 'unknown'
  | 'authorized'
  | 'denied'
  | 'not_required';

export interface ConsentSnapshot {
  consent: ConsentState;
  platformTrackingPermission: PlatformTrackingPermission;
  analyticsEnabled: boolean;
}

export type AnalyticsPrimitive = string | number | boolean | null;
export type AnalyticsValue =
  | AnalyticsPrimitive
  | AnalyticsValue[]
  | { [key: string]: AnalyticsValue };

export type AnalyticsProperties = Record<string, AnalyticsValue>;
