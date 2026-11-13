import { Stack } from 'expo-router';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';

// Keep the Shuls list underneath a shul detail when the detail is opened from
// outside this tab (map "View schedule" and legacy /shul/[id] links navigate
// with withAnchor, and deep links honour this too), so back returns to the list.
export const unstable_settings = {
  initialRouteName: 'index',
};

export default function ShulsLayout() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  return (
    <Stack
      screenOptions={{
        headerShown: false,
        animation: 'slide_from_right',
        contentStyle: { backgroundColor: colors.background },
      }}
    />
  );
}
