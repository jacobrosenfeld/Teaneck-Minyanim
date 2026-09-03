import { NativeTabs } from 'expo-router/unstable-native-tabs';

import { isMobileFeedbackConfigured } from '@/api/client';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import { triggerScrollToNow, triggerGoToday } from '@/utils/tabEvents';

export default function TabLayout() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];
  const feedbackConfigured = isMobileFeedbackConfigured();

  return (
    <NativeTabs
      tintColor={colors.tint}
      iconColor={{ default: colors.tabIconDefault, selected: colors.tint }}
      labelStyle={{
        default: { color: colors.tabIconDefault, fontSize: 10 },
        selected: { color: colors.tint, fontSize: 10, fontWeight: '600' },
      }}
      disableTransparentOnScrollEdge
      minimizeBehavior="automatic"
      tabBarRespectsIMEInsets>
      <NativeTabs.Trigger
        name="index"
        disableScrollToTop
        listeners={{
          tabPress: () => {
            triggerGoToday();
            triggerScrollToNow();
          },
        }}>
        <NativeTabs.Trigger.Label>Minyanim</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon
          sf="calendar"
          md="calendar_month"
        />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="shuls">
        <NativeTabs.Trigger.Label>Shuls</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon
          sf={{ default: 'building.2', selected: 'building.2.fill' }}
          md="apartment"
        />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="map">
        <NativeTabs.Trigger.Label>Map</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon
          sf={{ default: 'map', selected: 'map.fill' }}
          md="map"
        />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="zmanim">
        <NativeTabs.Trigger.Label>Zmanim</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon
          sf={{ default: 'clock', selected: 'clock.fill' }}
          md="schedule"
        />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger
        name="feedback"
        hidden={!feedbackConfigured}>
        <NativeTabs.Trigger.Label>Feedback</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon
          sf={{
            default: 'bubble.left.and.bubble.right',
            selected: 'bubble.left.and.bubble.right.fill',
          }}
          md="feedback"
        />
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
