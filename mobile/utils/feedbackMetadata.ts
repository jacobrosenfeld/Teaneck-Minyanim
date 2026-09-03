import * as Application from 'expo-application';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as ExpoLinking from 'expo-linking';
import { Platform } from 'react-native';

import { getPostHogFeedbackContext } from '@/analytics';
import type { FeedbackMetadata, Organization } from '@/api/types';
import {
  findOrganizationForFeedbackContext,
  getFeedbackNavigationContext,
} from '@/utils/feedbackContext';

interface ExpoConfigWithNativeVersions {
  version?: string;
  ios?: {
    buildNumber?: string;
  };
  android?: {
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

function platformName(): FeedbackMetadata['platform'] {
  if (Platform.OS === 'ios') return 'ios';
  if (Platform.OS === 'android') return 'android';
  return 'mobile';
}

function expoConfig(): ExpoConfigWithNativeVersions {
  return (Constants.expoConfig ?? {}) as ExpoConfigWithNativeVersions;
}

function readAppVersion(): string | null {
  return nonEmptyString(Application.nativeApplicationVersion)
    ?? nonEmptyString(expoConfig().version);
}

function readBuildNumber(): string | null {
  const platform = platformName();
  const config = expoConfig();

  return nonEmptyString(Application.nativeBuildVersion)
    ?? (platform === 'ios' ? nonEmptyString(config.ios?.buildNumber) : null)
    ?? (platform === 'android' ? nonEmptyString(config.android?.versionCode) : null);
}

function readOsName(): string | null {
  if (Platform.OS === 'android') return 'Android';
  if (Platform.OS === 'ios') return nonEmptyString(Device.osName) ?? 'iOS';
  return nonEmptyString(Device.osName);
}

function cleanExtra(extra: Record<string, string | null>): Record<string, string> {
  return Object.entries(extra).reduce<Record<string, string>>((cleaned, [key, value]) => {
    if (value) {
      cleaned[key] = value;
    }
    return cleaned;
  }, {});
}

export function buildMobileFeedbackMetadata(
  organizations: Organization[] | undefined,
): FeedbackMetadata {
  const sourceContext = getFeedbackNavigationContext();
  const sourceOrganization = findOrganizationForFeedbackContext(sourceContext, organizations);

  return {
    platform: platformName(),
    screen: sourceContext.screen,
    page: 'mobile-feedback',
    route: sourceContext.pathname,
    url: ExpoLinking.createURL(sourceContext.pathname),
    selectedDate: sourceContext.routeParams.selectedDate ?? null,
    appVersion: readAppVersion(),
    buildNumber: readBuildNumber(),
    deviceModel: nonEmptyString(Device.modelName),
    osName: readOsName(),
    osVersion: nonEmptyString(Device.osVersion),
    organization: sourceOrganization
      ? {
          id: sourceOrganization.id,
          slug: sourceOrganization.slug,
          name: sourceOrganization.name,
        }
      : null,
    posthog: getPostHogFeedbackContext(),
    routeParams: sourceContext.routeParams,
    extra: cleanExtra({
      sourceCapturedAt: sourceContext.capturedAt,
      submittedFromScreen: 'feedback',
      currentRoute: '/feedback',
    }),
  };
}
