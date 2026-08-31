import { Tabs } from 'expo-router';
import { Platform, StyleSheet, View } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { BlurView } from 'expo-blur';

import { isMobileFeedbackConfigured } from '@/api/client';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import { triggerScrollToNow, triggerGoToday } from '@/utils/tabEvents';

export default function TabLayout() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];
  const feedbackConfigured = isMobileFeedbackConfigured();
  const glassOverlay = scheme === 'dark'
    ? 'rgba(18, 24, 38, 0.72)'
    : 'rgba(255, 255, 255, 0.64)';
  const glassBorder = scheme === 'dark'
    ? 'rgba(255, 255, 255, 0.14)'
    : 'rgba(255, 255, 255, 0.78)';

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: colors.tint,
        tabBarInactiveTintColor: colors.tabIconDefault,
        tabBarActiveBackgroundColor: scheme === 'dark'
          ? 'rgba(91, 143, 255, 0.18)'
          : 'rgba(39, 94, 216, 0.11)',
        tabBarInactiveBackgroundColor: 'transparent',
        tabBarHideOnKeyboard: true,
        tabBarLabelPosition: 'below-icon',

        tabBarBackground: Platform.OS === 'ios'
          ? () => (
              <View style={StyleSheet.absoluteFill}>
                <BlurView
                  intensity={82}
                  tint={scheme === 'dark' ? 'systemChromeMaterialDark' : 'systemChromeMaterial'}
                  style={StyleSheet.absoluteFill}
                />
                <View
                  style={[
                    StyleSheet.absoluteFill,
                    { backgroundColor: glassOverlay },
                  ]}
                />
              </View>
            )
          : undefined,

        tabBarStyle: Platform.select({
          ios: {
            position: 'absolute',
            left: 12,
            right: 12,
            bottom: 10,
            height: 68,
            borderRadius: 34,
            borderWidth: StyleSheet.hairlineWidth,
            borderTopWidth: StyleSheet.hairlineWidth,
            borderColor: glassBorder,
            backgroundColor: 'transparent',
            overflow: 'hidden',
            paddingTop: 7,
            paddingBottom: 7,
            shadowColor: '#000',
            shadowOffset: { width: 0, height: 8 },
            shadowOpacity: scheme === 'dark' ? 0.34 : 0.14,
            shadowRadius: 24,
          },
          android: {
            position: 'absolute',
            left: 12,
            right: 12,
            bottom: 10,
            height: 68,
            borderRadius: 34,
            borderWidth: 1,
            borderTopWidth: 1,
            borderColor: colors.border,
            backgroundColor: scheme === 'dark' ? 'rgba(22, 27, 34, 0.96)' : 'rgba(255, 255, 255, 0.96)',
            paddingTop: 7,
            paddingBottom: 7,
            elevation: 10,
          },
          default: {
            position: 'absolute',
            left: 12,
            right: 12,
            bottom: 10,
            height: 68,
            borderRadius: 34,
            borderWidth: 1,
            borderTopWidth: 1,
            borderColor: colors.border,
            backgroundColor: colors.card,
            paddingTop: 7,
            paddingBottom: 7,
          },
        }),

        tabBarItemStyle: styles.tabItem,
        tabBarIconStyle: styles.tabIcon,
        tabBarLabelStyle: styles.tabLabel,
        headerShown: false,
      }}>

      <Tabs.Screen
        name="index"
        options={{
          title: 'Minyanim',
          tabBarIcon: ({ color }) => (
            <SymbolView
              name="calendar"
              tintColor={color}
              size={24}
            />
          ),
        }}
        listeners={{
          tabPress: () => { triggerGoToday(); triggerScrollToNow(); },
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

const styles = StyleSheet.create({
  tabItem: {
    borderRadius: 28,
    marginHorizontal: 1,
    minHeight: 52,
  },
  tabIcon: {
    marginBottom: 1,
  },
  tabLabel: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0,
  },
});
