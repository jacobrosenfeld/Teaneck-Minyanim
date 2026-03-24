import { Platform } from 'react-native';
import PostHog from 'posthog-react-native';
import {
  getTrackingPermissionsAsync,
  requestTrackingPermissionsAsync,
  getAdvertisingId,
} from 'expo-tracking-transparency';

import { getAnalyticsConfig } from './config';
import {
  computeAnalyticsEnabled,
  mapTrackingPermissionStatus,
  transitionConsentState,
} from './consent';
import { dispatchCapture } from './dispatch';
import { sanitizeProperties } from './sanitize';
import {
  loadStoredConsentState,
  persistStoredConsentState,
} from './storage';
import type {
  AnalyticsProperties,
  ConsentSnapshot,
  ConsentState,
  PlatformTrackingPermission,
} from './types';

interface RuntimeState {
  consent: ConsentState;
  platformTrackingPermission: PlatformTrackingPermission;
  analyticsEnabled: boolean;
  hydrated: boolean;
  appOpenCaptured: boolean;
}

const config = getAnalyticsConfig();

const runtime: RuntimeState = {
  consent: 'unknown',
  platformTrackingPermission: 'unknown',
  analyticsEnabled: false,
  hydrated: false,
  appOpenCaptured: false,
};

let posthogClient: PostHog | null = null;

function currentPlatform(): 'ios' | 'android' | 'web' {
  if (Platform.OS === 'ios') return 'ios';
  if (Platform.OS === 'android') return 'android';
  return 'web';
}

function ensureClient(): PostHog | null {
  if (posthogClient) return posthogClient;
  if (!config.analyticsGloballyEnabled) return null;
  if (!config.posthogKey) return null;

  posthogClient = new PostHog(config.posthogKey, {
    host: config.posthogHost,
    defaultOptIn: false,
    captureAppLifecycleEvents: true,
    disableGeoip: true,
    enableSessionReplay: config.sessionReplayEnabled,
    sessionReplayConfig: config.sessionReplayEnabled
      ? {
          maskAllTextInputs: true,
          maskAllImages: true,
          captureLog: false,
          captureNetworkTelemetry: false,
          sampleRate: config.sessionReplaySampleRate,
          throttleDelayMs: 1200,
        }
      : undefined,
    before_send: (event) => {
      if (!event || !runtime.analyticsEnabled) {
        return null;
      }

      if (event.properties && typeof event.properties === 'object') {
        event.properties = sanitizeProperties(event.properties as AnalyticsProperties) as any;
      }

      return event;
    },
  });

  posthogClient.optOut();
  return posthogClient;
}

async function syncAdvertisingId(analyticsEnabled: boolean): Promise<void> {
  if (!posthogClient) return;

  if (!analyticsEnabled) {
    posthogClient.unregister('advertising_id');
    return;
  }

  try {
    const advertisingId = await getAdvertisingId();
    if (advertisingId) {
      posthogClient.register({ advertising_id: advertisingId });
    }
  } catch {
    // Ignore; tracking may be unavailable on this device.
  }
}

async function readPlatformTrackingPermission(): Promise<PlatformTrackingPermission> {
  const platform = currentPlatform();

  if (platform === 'android') return 'not_required';
  if (platform !== 'ios') return 'unknown';

  try {
    const response = await getTrackingPermissionsAsync();
    return mapTrackingPermissionStatus(response.status, 'ios');
  } catch {
    return runtime.platformTrackingPermission;
  }
}

async function applyAnalyticsState(captureAppOpen: boolean): Promise<void> {
  runtime.analyticsEnabled = computeAnalyticsEnabled(
    runtime.consent,
    runtime.platformTrackingPermission,
    currentPlatform(),
    config.analyticsGloballyEnabled,
  );

  if (!runtime.analyticsEnabled) {
    if (posthogClient) {
      posthogClient.optOut();
      await syncAdvertisingId(false);
    }
    return;
  }

  const client = ensureClient();
  if (!client) return;

  client.optIn();
  await syncAdvertisingId(true);

  if (captureAppOpen && !runtime.appOpenCaptured) {
    runtime.appOpenCaptured = true;
    dispatchCapture(client, true, 'app_open', {
      platform: currentPlatform(),
      consent: runtime.consent,
      tracking_permission: runtime.platformTrackingPermission,
    });
  }
}

function buildSnapshot(): ConsentSnapshot {
  return {
    consent: runtime.consent,
    platformTrackingPermission: runtime.platformTrackingPermission,
    analyticsEnabled: runtime.analyticsEnabled,
  };
}

async function persistRuntimeState(): Promise<void> {
  await persistStoredConsentState({
    consent: runtime.consent,
    platformTrackingPermission: runtime.platformTrackingPermission,
  });
}

export async function initAnalytics(): Promise<ConsentSnapshot> {
  if (!runtime.hydrated) {
    const stored = await loadStoredConsentState();
    runtime.consent = stored.consent;
    runtime.platformTrackingPermission = stored.platformTrackingPermission;

    runtime.platformTrackingPermission = await readPlatformTrackingPermission();
    await persistRuntimeState();

    runtime.hydrated = true;
  }

  await applyAnalyticsState(true);
  return buildSnapshot();
}

export async function setConsent(
  action: 'accept' | 'decline' | 'reset',
): Promise<ConsentSnapshot> {
  runtime.consent = transitionConsentState(runtime.consent, action);

  if (currentPlatform() === 'android') {
    runtime.platformTrackingPermission = 'not_required';
  }

  if (currentPlatform() === 'ios' && action === 'accept') {
    try {
      const response = await requestTrackingPermissionsAsync();
      runtime.platformTrackingPermission = mapTrackingPermissionStatus(response.status, 'ios');
    } catch {
      runtime.platformTrackingPermission = await readPlatformTrackingPermission();
    }
  }

  if (currentPlatform() === 'ios' && action !== 'accept') {
    runtime.platformTrackingPermission = await readPlatformTrackingPermission();
  }

  await persistRuntimeState();
  await applyAnalyticsState(false);

  return buildSnapshot();
}

export async function optIn(): Promise<ConsentSnapshot> {
  return setConsent('accept');
}

export async function optOut(): Promise<ConsentSnapshot> {
  return setConsent('decline');
}

export function capture(
  eventName: string,
  properties: AnalyticsProperties = {},
): boolean {
  if (!runtime.analyticsEnabled) return false;

  const client = ensureClient();
  if (!client) return false;

  const safeProperties = sanitizeProperties(properties);
  return dispatchCapture(client, runtime.analyticsEnabled, eventName, safeProperties);
}

export function getConsentSnapshot(): ConsentSnapshot {
  return buildSnapshot();
}

export function getAnalyticsClient(): PostHog | null {
  if (!runtime.analyticsEnabled) return null;
  return ensureClient();
}
