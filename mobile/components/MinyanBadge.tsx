import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';

interface Props {
  type: string;
  label: string;
  size?: 'sm' | 'md';
}

export default function MinyanBadge({ type, label, size = 'md' }: Props) {
  const scheme = useColorScheme() ?? 'light';
  const badgePalette = Colors[scheme].badge as Record<string, { bg: string; text: string }>;
  const colors = badgePalette[type] ?? badgePalette.default;

  return (
    <View style={[styles.badge, size === 'sm' && styles.badgeSm, { backgroundColor: colors.bg }]}>
      <Text style={[styles.label, size === 'sm' && styles.labelSm, { color: colors.text }]}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    borderRadius: 6,
    paddingHorizontal: 9,
    paddingVertical: 3,
    alignSelf: 'flex-start',
  },
  badgeSm: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  label: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.2,
  },
  labelSm: {
    fontSize: 10,
  },
});
