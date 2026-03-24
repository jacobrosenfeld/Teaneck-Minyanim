import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import { useFonts } from 'expo-font';
import { router, Stack, useGlobalSearchParams, usePathname } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect, useRef, useState } from 'react';
import 'react-native-reanimated';
import { QueryClient } from '@tanstack/react-query';
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Notifications from 'expo-notifications';
import { PostHogProvider } from 'posthog-react-native';

import { capture, getAnalyticsClient, initAnalytics, setConsent } from '@/analytics';
import TrackingConsentModal from '@/components/TrackingConsentModal';
import { useColorScheme } from '@/components/useColorScheme';
import { requestNotificationPermission, SNOOZE_ACTION } from '@/utils/notifications';
import { triggerOpenSheet } from '@/utils/tabEvents';

export { ErrorBoundary } from 'expo-router';

export const unstable_settings = {
  initialRouteName: '(tabs)',
};

SplashScreen.preventAutoHideAsync();

// Cache persists to disk for 7 days — industry standard offline-first approach:
// 1. On cold start, data loads instantly from cache while background refetch runs
// 2. staleTime = 5 min: won't refetch if data is fresh (e.g. switching tabs)
// 3. gcTime = 7 days: cached data survives app restarts
// 4. Pull-to-refresh forces a manual refetch regardless of staleness
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      refetchOnWindowFocus: false,
      staleTime: 5 * 60 * 1000,        // 5 min — fresh enough to skip refetch
      gcTime: 7 * 24 * 60 * 60 * 1000, // 7 days — keep in disk cache
    },
  },
});

const asyncStoragePersister = createAsyncStoragePersister({
  storage: AsyncStorage,
  key: 'teaneck-minyanim-rq-cache',
  throttleTime: 1000,
});

export default function RootLayout() {
  const [loaded, error] = useFonts({
    SpaceMono: require('../assets/fonts/SpaceMono-Regular.ttf'),
  });
  const [analyticsEnabled, setAnalyticsEnabled] = useState(false);
  const [consentModalVisible, setConsentModalVisible] = useState(false);
  const [consentActionLoading, setConsentActionLoading] = useState(false);
  const pathname = usePathname();
  const params = useGlobalSearchParams();

  useEffect(() => {
    if (error) throw error;
  }, [error]);

  // Request notification permission early so reminders work immediately
  useEffect(() => {
    requestNotificationPermission();
  }, []);

  // Initialize consent state and analytics gating before any analytics calls
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const snapshot = await initAnalytics();
        if (!cancelled) {
          setAnalyticsEnabled(snapshot.analyticsEnabled);
          setConsentModalVisible(snapshot.consent === 'unknown');
        }
      } catch {
        if (!cancelled) {
          setAnalyticsEnabled(false);
          setConsentModalVisible(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Track screen views via Expo Router pathname changes
  useEffect(() => {
    const routeParams: Record<string, string> = {};
    for (const [key, value] of Object.entries(params)) {
      if (typeof value === 'string' && value.length > 0) {
        routeParams[key] = value;
      } else if (Array.isArray(value) && value.length > 0) {
        routeParams[key] = value.join(',');
      }
    }
    capture('screen_view', {
      pathname,
      ...routeParams,
    });
  }, [pathname, params]);

  // Handle notification tap: navigate to minyanim tab and open the ShulDaySheet
  // Use a ref so we can call triggerOpenSheet after navigation settles
  const pendingSheetRef = useRef<Parameters<typeof triggerOpenSheet>[0] | null>(null);
  useEffect(() => {
    const sub = Notifications.addNotificationResponseReceivedListener((response) => {
      if (response.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) return;
      const data = response.notification.request.content.data as {
        eventId?: string;
        date?: string;
        orgSlug?: string;
        orgName?: string;
      };
      if (!data.eventId || !data.date || !data.orgSlug) return;
      pendingSheetRef.current = {
        eventId: data.eventId,
        date: data.date,
        orgSlug: data.orgSlug,
        orgName: data.orgName ?? '',
      };
      // Navigate to minyanim tab first; index.tsx will consume pendingSheetRef
      router.navigate('/(tabs)');
      // Give the tab a frame to mount/focus before triggering the sheet
      setTimeout(() => {
        if (pendingSheetRef.current) {
          triggerOpenSheet(pendingSheetRef.current);
          pendingSheetRef.current = null;
        }
      }, 300);
    });
    return () => sub.remove();
  }, []);

  // Handle snooze action: reschedule the same notification 5 minutes later
  useEffect(() => {
    const sub = Notifications.addNotificationResponseReceivedListener(async (response) => {
      if (response.actionIdentifier !== SNOOZE_ACTION) return;
      const data = response.notification.request.content.data as {
        minyanType?: string;
        originalTitle?: string;
        originalBody?: string;
      };
      const snoozeDate = new Date(Date.now() + 5 * 60 * 1000);
      // Use minyanType alone as the title — no "in X minutes" countdown after a snooze
      const snoozeTitle = data.minyanType ?? data.originalTitle ?? response.notification.request.content.title ?? '';
      await Notifications.scheduleNotificationAsync({
        content: {
          title: snoozeTitle,
          body: data.originalBody ?? response.notification.request.content.body ?? '',
          sound: true,
          data: response.notification.request.content.data,
        },
        trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: snoozeDate },
      });
    });
    return () => sub.remove();
  }, []);

  useEffect(() => {
    if (loaded) {
      SplashScreen.hideAsync();
    }
  }, [loaded]);

  if (!loaded) {
    return null;
  }

  const handleAcceptTracking = async () => {
    setConsentActionLoading(true);
    try {
      const snapshot = await setConsent('accept');
      setAnalyticsEnabled(snapshot.analyticsEnabled);
      setConsentModalVisible(snapshot.consent === 'unknown');
    } finally {
      setConsentActionLoading(false);
    }
  };

  const handleDeclineTracking = async () => {
    setConsentActionLoading(true);
    try {
      const snapshot = await setConsent('decline');
      setAnalyticsEnabled(snapshot.analyticsEnabled);
      setConsentModalVisible(snapshot.consent === 'unknown');
    } finally {
      setConsentActionLoading(false);
    }
  };

  const appTree = (
    <>
      <RootLayoutNav />
      <TrackingConsentModal
        visible={consentModalVisible}
        loading={consentActionLoading}
        onAccept={handleAcceptTracking}
        onDecline={handleDeclineTracking}
      />
    </>
  );

  const posthogClient = getAnalyticsClient();
  const analyticsWrappedTree = analyticsEnabled && posthogClient ? (
    <PostHogProvider client={posthogClient} autocapture>
      {appTree}
    </PostHogProvider>
  ) : appTree;

  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{ persister: asyncStoragePersister, maxAge: 7 * 24 * 60 * 60 * 1000 }}>
      {analyticsWrappedTree}
    </PersistQueryClientProvider>
  );
}

function RootLayoutNav() {
  const colorScheme = useColorScheme();

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <Stack>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="shul/[id]" options={{ headerShown: false, animation: 'slide_from_right' }} />
        <Stack.Screen name="+not-found" />
      </Stack>
    </ThemeProvider>
  );
}
