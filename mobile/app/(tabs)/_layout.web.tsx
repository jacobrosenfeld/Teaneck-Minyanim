import { Tabs } from 'expo-router';
import { SymbolView } from 'expo-symbols';

import { isMobileFeedbackConfigured } from '@/api/client';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import { triggerScrollToNow, triggerGoToday } from '@/utils/tabEvents';

export default function WebTabLayout() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];
  const feedbackConfigured = isMobileFeedbackConfigured();

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: colors.tint,
        tabBarInactiveTintColor: colors.tabIconDefault,
        tabBarHideOnKeyboard: true,
        tabBarLabelPosition: 'below-icon',
        tabBarStyle: {
          backgroundColor: colors.card,
          borderTopColor: colors.border,
        },
        tabBarLabelStyle: {
          fontSize: 10,
          fontWeight: '600',
          letterSpacing: 0,
        },
        headerShown: false,
      }}>
      <Tabs.Screen
        name="index"
        options={{
          title: 'Minyanim',
          tabBarIcon: ({ color }) => (
            <SymbolView name="calendar" tintColor={color} size={24} />
          ),
        }}
        listeners={{
          tabPress: () => {
            triggerGoToday();
            triggerScrollToNow();
          },
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

      <Tabs.Screen
        name="feedback"
        options={{
          title: 'Feedback',
          href: feedbackConfigured ? undefined : null,
          tabBarIcon: ({ color, focused }) => (
            <SymbolView
              name={focused ? 'bubble.left.and.bubble.right.fill' : 'bubble.left.and.bubble.right'}
              tintColor={color}
              size={24}
            />
          ),
        }}
      />
    </Tabs>
  );
}
