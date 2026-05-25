import * as Application from 'expo-application';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { Platform } from 'react-native';

import type { AnalyticsProperties } from './types';

export type AnalyticsPlatform = 'ios' | 'android' | 'web';

interface ExpoConfigWithNativeVersions {
  version?: string;
  slug?: string;
  ios?: {
    buildNumber?: string;
    bundleIdentifier?: string;
  };
  android?: {
    package?: string;
    versionCode?: number | string;
  };
}

function nonEmptyString(value: unknown): string | null {
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }

  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value);
  }

  return null;
}

function expoConfig(): ExpoConfigWithNativeVersions {
  return (Constants.expoConfig ?? {}) as ExpoConfigWithNativeVersions;
}

export function getAnalyticsPlatform(): AnalyticsPlatform {
  if (Platform.OS === 'ios') return 'ios';
  if (Platform.OS === 'android') return 'android';
  return 'web';
}

function readNativeBuildVersion(platform: AnalyticsPlatform): string | null {
  const config = expoConfig();

  return (
    nonEmptyString(Application.nativeBuildVersion) ??
    (platform === 'ios' ? nonEmptyString(config.ios?.buildNumber) : null) ??
    (platform === 'android' ? nonEmptyString(config.android?.versionCode) : null)
  );
}

function readAppNamespace(platform: AnalyticsPlatform): string | null {
  const config = expoConfig();

  return (
    nonEmptyString(Application.applicationId) ??
    (platform === 'ios' ? nonEmptyString(config.ios?.bundleIdentifier) : null) ??
    (platform === 'android' ? nonEmptyString(config.android?.package) : null) ??
    nonEmptyString(config.slug)
  );
}

function readOsName(platform: AnalyticsPlatform): string | null {
  if (platform === 'android') return 'Android';
  if (platform === 'ios') return nonEmptyString(Device.osName) ?? 'iOS';
  return nonEmptyString(Device.osName);
}

function withoutEmptyValues(properties: Record<string, string | null>): Record<string, string> {
  return Object.entries(properties).reduce<Record<string, string>>((cleaned, [key, value]) => {
    if (value) {
      cleaned[key] = value;
    }
    return cleaned;
  }, {});
}

export function buildPostHogAppProperties(): Record<string, string> {
  const platform = getAnalyticsPlatform();
  const appVersion =
    nonEmptyString(Application.nativeApplicationVersion) ?? nonEmptyString(expoConfig().version);
  const appBuild = readNativeBuildVersion(platform);
  const appNamespace = readAppNamespace(platform);
  const osName = readOsName(platform);
  const osVersion = nonEmptyString(Device.osVersion);

  return withoutEmptyValues({
    $app_version: appVersion,
    $app_build: appBuild,
    $app_namespace: appNamespace,
    $os_name: osName,
    $os_version: osVersion,
  });
}

export function buildAnalyticsEventMetadata(): AnalyticsProperties {
  const postHogProperties = buildPostHogAppProperties();

  return {
    ...postHogProperties,
    ...withoutEmptyValues({
      app_platform: getAnalyticsPlatform(),
      app_version: postHogProperties.$app_version ?? null,
      app_build: postHogProperties.$app_build ?? null,
      app_namespace: postHogProperties.$app_namespace ?? null,
      os_name: postHogProperties.$os_name ?? null,
      os_version: postHogProperties.$os_version ?? null,
    }),
  };
}
