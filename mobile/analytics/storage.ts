import AsyncStorage from '@react-native-async-storage/async-storage';

import {
  normalizeConsentState,
  normalizePlatformTrackingPermission,
} from './consent';
import type { ConsentState, PlatformTrackingPermission } from './types';

const CONSENT_KEY = 'analytics_consent_v1';
const PLATFORM_PERMISSION_KEY = 'platform_tracking_permission_v1';

export interface StoredConsentState {
  consent: ConsentState;
  platformTrackingPermission: PlatformTrackingPermission;
}

export async function loadStoredConsentState(): Promise<StoredConsentState> {
  const [[, consentValue], [, permissionValue]] = await AsyncStorage.multiGet([
    CONSENT_KEY,
    PLATFORM_PERMISSION_KEY,
  ]);

  return {
    consent: normalizeConsentState(consentValue),
    platformTrackingPermission: normalizePlatformTrackingPermission(permissionValue),
  };
}

export async function persistStoredConsentState(
  state: StoredConsentState,
): Promise<void> {
  await AsyncStorage.multiSet([
    [CONSENT_KEY, state.consent],
    [PLATFORM_PERMISSION_KEY, state.platformTrackingPermission],
  ]);
}
