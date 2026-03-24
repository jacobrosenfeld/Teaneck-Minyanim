import type { AnalyticsProperties, AnalyticsValue } from './types';

const FORBIDDEN_KEY_FRAGMENTS = [
  'email',
  'phone',
  'address',
  'latitude',
  'longitude',
  'coordinate',
  'idfa',
  'aaid',
  'advertising_id',
  'device_id',
];

const FORBIDDEN_EXACT_KEYS = new Set([
  'name',
  'first_name',
  'last_name',
  'full_name',
  'lat',
  'lng',
]);

const EMAIL_PATTERN = /\S+@\S+\.\S+/;
const PHONE_PATTERN = /\+?[0-9][0-9().\-\s]{7,}[0-9]/;

function shouldDropKey(key: string): boolean {
  const normalized = key.trim().toLowerCase();
  if (FORBIDDEN_EXACT_KEYS.has(normalized)) {
    return true;
  }
  if (normalized.endsWith('_lat') || normalized.endsWith('_lng')) {
    return true;
  }
  return FORBIDDEN_KEY_FRAGMENTS.some((fragment) => normalized.includes(fragment));
}

function sanitizeValue(value: unknown): AnalyticsValue | undefined {
  if (value == null) return undefined;

  if (typeof value === 'string') {
    if (EMAIL_PATTERN.test(value) || PHONE_PATTERN.test(value)) {
      return undefined;
    }
    return value;
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return value;
  }

  if (Array.isArray(value)) {
    const cleaned = value
      .map((item) => sanitizeValue(item))
      .filter((item) => item !== undefined);
    return cleaned.length > 0 ? cleaned : undefined;
  }

  if (typeof value === 'object') {
    const cleaned = sanitizeProperties(value as AnalyticsProperties);
    return Object.keys(cleaned).length > 0 ? cleaned : undefined;
  }

  return undefined;
}

export function sanitizeProperties(
  properties: AnalyticsProperties = {},
): AnalyticsProperties {
  const cleaned: AnalyticsProperties = {};

  for (const [key, value] of Object.entries(properties)) {
    if (shouldDropKey(key)) continue;
    const sanitizedValue = sanitizeValue(value);
    if (sanitizedValue !== undefined) {
      cleaned[key] = sanitizedValue;
    }
  }

  return cleaned;
}
