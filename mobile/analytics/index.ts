import PostHog from 'posthog-react-native';

import { getAnalyticsConfig } from './config';
import {
  buildAnalyticsEventMetadata,
  buildPostHogAppProperties,
  getAnalyticsPlatform,
} from './appMetadata';
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

type TrackingTransparencyModule = typeof import('expo-tracking-transparency');

interface PostHogFeedbackContext {
  distinctId?: string;
  sessionId?: string;
  sessionReplayUrl?: string;
}

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
let posthogReadyPromise: Promise<void> | null = null;
let trackingTransparencyPromise: Promise<TrackingTransparencyModule | null> | null = null;

function currentPlatform(): 'ios' | 'android' | 'web' {
  return getAnalyticsPlatform();
}

async function loadTrackingTransparency(): Promise<TrackingTransparencyModule | null> {
  if (currentPlatform() === 'web') return null;

  if (!trackingTransparencyPromise) {
    trackingTransparencyPromise = import('expo-tracking-transparency').catch(() => null);
  }

  return trackingTransparencyPromise;
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
    customAppProperties: (properties) => ({
      ...properties,
      ...buildPostHogAppProperties(),
    }),
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

  posthogReadyPromise = posthogClient.ready().catch(() => {});

  return posthogClient;
}

async function waitForClientReady(): Promise<void> {
  if (!posthogReadyPromise) return;
  await posthogReadyPromise;
}

function canAttachAdvertisingId(): boolean {
  if (!runtime.analyticsEnabled) return false;

  const platform = currentPlatform();
  if (platform === 'ios') {
    return runtime.platformTrackingPermission === 'authorized';
  }
  if (platform === 'android') {
    return true;
  }
  return false;
}

async function syncAdvertisingId(shouldAttachAdvertisingId: boolean): Promise<void> {
  if (!posthogClient) return;

  if (!shouldAttachAdvertisingId) {
    await posthogClient.unregister('advertising_id');
    return;
  }

  try {
    const trackingTransparency = await loadTrackingTransparency();
    if (!trackingTransparency?.getAdvertisingId) return;

    const advertisingId = await trackingTransparency.getAdvertisingId();
    if (advertisingId) {
      await posthogClient.register({ advertising_id: advertisingId });
    }
  } catch {
    // Ignore; tracking may be unavailable on this device.
  }
}

async function syncAppMetadata(): Promise<void> {
  if (!posthogClient) return;

  const metadata = buildAnalyticsEventMetadata();
  if (Object.keys(metadata).length === 0) return;

  await posthogClient.register(metadata);
}

async function readPlatformTrackingPermission(): Promise<PlatformTrackingPermission> {
  const platform = currentPlatform();

  if (platform === 'android') return 'not_required';
  if (platform !== 'ios') return 'unknown';

  try {
    const trackingTransparency = await loadTrackingTransparency();
    if (!trackingTransparency?.getTrackingPermissionsAsync) return 'unknown';

    const response = await trackingTransparency.getTrackingPermissionsAsync();
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
      await posthogClient.optOut();
      await syncAdvertisingId(false);
    }
    return;
  }

  const client = ensureClient();
  if (!client) return;

  await waitForClientReady();
  await client.optIn();
  await syncAppMetadata();
  await syncAdvertisingId(canAttachAdvertisingId());

  if (captureAppOpen && !runtime.appOpenCaptured) {
    runtime.appOpenCaptured = true;
    dispatchCapture(client, true, 'app_open', {
      ...buildAnalyticsEventMetadata(),
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
      const trackingTransparency = await loadTrackingTransparency();
      if (trackingTransparency?.requestTrackingPermissionsAsync) {
        const response = await trackingTransparency.requestTrackingPermissionsAsync();
        runtime.platformTrackingPermission = mapTrackingPermissionStatus(response.status, 'ios');
      } else {
        runtime.platformTrackingPermission = 'unknown';
      }
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

  const safeProperties = {
    ...sanitizeProperties(properties),
    ...buildAnalyticsEventMetadata(),
  };
  return dispatchCapture(client, runtime.analyticsEnabled, eventName, safeProperties);
}

export function getConsentSnapshot(): ConsentSnapshot {
  return buildSnapshot();
}

export function getAnalyticsClient(): PostHog | null {
  if (!runtime.analyticsEnabled) return null;
  return ensureClient();
}

export function getPostHogFeedbackContext(): PostHogFeedbackContext | null {
  const client = getAnalyticsClient();
  if (!client) return null;

  const distinctId = safePostHogString(() => client.getDistinctId());
  const sessionId = safePostHogString(() => client.getSessionId());
  const sessionReplayUrl = safePostHogString(() => {
    const clientWithReplayUrl = client as PostHog & {
      getSessionReplayUrl?: (options?: { withTimestamp?: boolean }) => string;
      get_session_replay_url?: (options?: { withTimestamp?: boolean }) => string;
    };
    return clientWithReplayUrl.getSessionReplayUrl?.({ withTimestamp: true })
      ?? clientWithReplayUrl.get_session_replay_url?.({ withTimestamp: true })
      ?? null;
  });

  if (!distinctId && !sessionId && !sessionReplayUrl) {
    return null;
  }

  return {
    distinctId: distinctId ?? undefined,
    sessionId: sessionId ?? undefined,
    sessionReplayUrl: sessionReplayUrl ?? undefined,
  };
}

function safePostHogString(read: () => string | null | undefined): string | null {
  try {
    const value = read();
    if (!value) return null;
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  } catch {
    return null;
  }
}
