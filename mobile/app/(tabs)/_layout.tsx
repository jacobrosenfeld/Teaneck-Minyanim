import { Tabs } from 'expo-router';
import { Platform, StyleSheet } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { BlurView } from 'expo-blur';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';

export default function TabLayout() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: colors.tint,
        tabBarInactiveTintColor: colors.tabIconDefault,

        // ── Liquid glass on iOS, solid on Android ──────────────────────────
        tabBarBackground: Platform.OS === 'ios'
          ? () => (
              <BlurView
                intensity={90}
                tint={scheme === 'dark' ? 'systemChromeMaterialDark' : 'systemChromeMaterial'}
                style={StyleSheet.absoluteFill}
              />
            )
          : undefined,

        tabBarStyle: Platform.select({
          ios: {
            position: 'absolute',
            backgroundColor: 'transparent',
            borderTopWidth: StyleSheet.hairlineWidth,
            borderTopColor: scheme === 'dark'
              ? 'rgba(255,255,255,0.12)'
              : 'rgba(0,0,0,0.08)',
            shadowColor: '#000',
            shadowOffset: { width: 0, height: -2 },
            shadowOpacity: scheme === 'dark' ? 0.25 : 0.08,
            shadowRadius: 16,
          },
          android: {
            backgroundColor: colors.card,
            borderTopColor: colors.border,
            borderTopWidth: 1,
            elevation: 8,
          },
          default: {
            backgroundColor: colors.card,
            borderTopColor: colors.border,
            borderTopWidth: 1,
          },
        }),

        tabBarLabelStyle: { fontSize: 11, fontWeight: '600', letterSpacing: 0.2 },
        headerShown: false,
      }}>

      <Tabs.Screen
        name="index"
        options={{
          title: 'Today',
          tabBarIcon: ({ color, focused }) => (
            <SymbolView
              name={focused ? 'calendar.day.timeline.left' : 'calendar'}
              tintColor={color}
              size={24}
            />
          ),
        }}
      />

      <Tabs.Screen
        name="shuls"
        options={{
          title: 'Shuls',
          tabBarIcon: ({ color, focused }) => (
            <SymbolView
              name={focused ? 'building.2.fill' : 'building.2'}
              tintColor={color}
              size={24}
            />
          ),
        }}
      />

      <Tabs.Screen
        name="map"
        options={{
          title: 'Map',
          tabBarIcon: ({ color, focused }) => (
            <SymbolView
              name={focused ? 'map.fill' : 'map'}
              tintColor={color}
              size={24}
            />
          ),
        }}
      />

      <Tabs.Screen
        name="zmanim"
        options={{
          title: 'Zmanim',
          tabBarIcon: ({ color, focused }) => (
            <SymbolView
              name={focused ? 'clock.fill' : 'clock'}
              tintColor={color}
              size={24}
            />
          ),
        }}
      />
    </Tabs>
  );
}
