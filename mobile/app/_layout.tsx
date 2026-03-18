import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import 'react-native-reanimated';
import { QueryClient } from '@tanstack/react-query';
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Notifications from 'expo-notifications';

import { useColorScheme } from '@/components/useColorScheme';
import { requestNotificationPermission, SNOOZE_ACTION } from '@/utils/notifications';

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

  useEffect(() => {
    if (error) throw error;
  }, [error]);

  // Request notification permission early so reminders work immediately
  useEffect(() => {
    requestNotificationPermission();
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

  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{ persister: asyncStoragePersister, maxAge: 7 * 24 * 60 * 60 * 1000 }}>
      <RootLayoutNav />
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
