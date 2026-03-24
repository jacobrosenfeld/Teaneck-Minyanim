import type { ConsentState, PlatformTrackingPermission } from './types';

export type ConsentAction = 'accept' | 'decline' | 'reset';

export function transitionConsentState(
  current: ConsentState,
  action: ConsentAction,
): ConsentState {
  if (action === 'accept') return 'accepted';
  if (action === 'decline') return 'declined';
  if (action === 'reset') return 'unknown';
  return current;
}

export function normalizeConsentState(value: string | null | undefined): ConsentState {
  if (value === 'accepted' || value === 'declined' || value === 'unknown') {
    return value;
  }
  return 'unknown';
}

export function normalizePlatformTrackingPermission(
  value: string | null | undefined,
): PlatformTrackingPermission {
  if (
    value === 'authorized' ||
    value === 'denied' ||
    value === 'not_required' ||
    value === 'unknown'
  ) {
    return value;
  }
  return 'unknown';
}

export function mapTrackingPermissionStatus(
  status: string | null | undefined,
  platform: 'ios' | 'android' | 'web',
): PlatformTrackingPermission {
  if (platform === 'android') return 'not_required';
  if (platform !== 'ios') return 'unknown';

  if (status === 'granted') return 'authorized';
  if (status === 'denied') return 'denied';
  return 'unknown';
}

export function computeAnalyticsEnabled(
  consent: ConsentState,
  platformTrackingPermission: PlatformTrackingPermission,
  platform: 'ios' | 'android' | 'web',
  analyticsGloballyEnabled: boolean,
): boolean {
  if (!analyticsGloballyEnabled) return false;
  if (consent !== 'accepted') return false;

  if (platform === 'ios') {
    return platformTrackingPermission === 'authorized';
  }

  if (platform === 'android') {
    return true;
  }

  return false;
}
