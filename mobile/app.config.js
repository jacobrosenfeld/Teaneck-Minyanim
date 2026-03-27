const appJson = require('./app.json');
const fs = require('fs');
const path = require('path');

const ANALYTICS_ENV_KEYS = [
  'EXPO_PUBLIC_ANALYTICS_ENABLED',
  'EXPO_PUBLIC_POSTHOG_KEY',
  'EXPO_PUBLIC_POSTHOG_HOST',
  'EXPO_PUBLIC_SESSION_REPLAY_ENABLED',
];

function isProductionProfile() {
  return process.env.EAS_BUILD_PROFILE === 'production' || process.env.NODE_ENV === 'production';
}

function parseDotEnv(content) {
  return content.split(/\r?\n/).reduce((acc, line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return acc;
    const eqIndex = trimmed.indexOf('=');
    if (eqIndex <= 0) return acc;
    const key = trimmed.slice(0, eqIndex).trim();
    let value = trimmed.slice(eqIndex + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    acc[key] = value;
    return acc;
  }, {});
}

function loadProductionEnvFile() {
  const envPath = path.join(__dirname, '.env.production');
  if (!fs.existsSync(envPath)) return;

  const parsed = parseDotEnv(fs.readFileSync(envPath, 'utf8'));
  for (const key of ANALYTICS_ENV_KEYS) {
    if (Object.prototype.hasOwnProperty.call(parsed, key)) {
      process.env[key] = parsed[key];
    }
  }

  if (Object.prototype.hasOwnProperty.call(parsed, 'GOOGLE_MAPS_API_KEY')) {
    process.env.GOOGLE_MAPS_API_KEY = parsed.GOOGLE_MAPS_API_KEY;
  }
}

function withoutReactNativeMapsPlugin(plugins) {
  return (plugins || []).filter((plugin) => {
    if (typeof plugin === 'string') {
      return plugin !== 'react-native-maps';
    }
    if (Array.isArray(plugin)) {
      return plugin[0] !== 'react-native-maps';
    }
    return true;
  });
}

module.exports = () => {
  if (isProductionProfile()) {
    loadProductionEnvFile();
  }

  const baseExpoConfig = appJson.expo;
  const mapsApiKey = process.env.GOOGLE_MAPS_API_KEY;
  const plugins = withoutReactNativeMapsPlugin(baseExpoConfig.plugins);
  const analyticsEnabled =
    (process.env.EXPO_PUBLIC_ANALYTICS_ENABLED || '').trim().toLowerCase() === 'true';
  const posthogKey = (process.env.EXPO_PUBLIC_POSTHOG_KEY || '').trim();

  if (!mapsApiKey && process.env.EAS_BUILD_PROFILE === 'production') {
    throw new Error(
      'Missing GOOGLE_MAPS_API_KEY. Set this EAS environment variable before running a production Android build.',
    );
  }
  if (isProductionProfile() && analyticsEnabled && !posthogKey) {
    throw new Error(
      'Analytics is enabled but EXPO_PUBLIC_POSTHOG_KEY is empty. Set it in .env.production before building production.',
    );
  }

  if (mapsApiKey) {
    plugins.push([
      'react-native-maps',
      {
        androidGoogleMapsApiKey: mapsApiKey,
      },
    ]);
  }

  return {
    ...baseExpoConfig,
    plugins,
  };
};
