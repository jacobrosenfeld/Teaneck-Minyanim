import React from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { SymbolView } from 'expo-symbols';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import { useOrganizations } from '@/api/hooks';

export default function MapScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];
  const { data: orgs } = useOrganizations();
  const shulCount = orgs?.length ?? 0;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]} edges={['top']}>

      {/* Header */}
      <View style={[styles.header, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
        <Text style={[styles.siteName, { color: colors.tint }]}>Teaneck Minyanim</Text>
        <Text style={[styles.title, { color: colors.text }]}>Map</Text>
        {shulCount > 0 && (
          <Text style={[styles.subtitle, { color: colors.textSecondary }]}>
            {shulCount} shul{shulCount !== 1 ? 's' : ''} · Teaneck, NJ
          </Text>
        )}
      </View>

      {/* Placeholder */}
      <View style={styles.center}>
        <View style={[styles.iconWrap, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <SymbolView
            name="map.fill"
            tintColor={colors.tint}
            size={48}
          />
        </View>
        <Text style={[styles.heading, { color: colors.text }]}>Map Coming Soon</Text>
        <Text style={[styles.body, { color: colors.textSecondary }]}>
          Interactive map showing all {shulCount > 0 ? shulCount : ''} shuls in Teaneck with
          directions, schedules, and distance sorting.
        </Text>
      </View>

    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: {
    paddingHorizontal: 16,
    paddingTop: 14,
    paddingBottom: 14,
    borderBottomWidth: 1,
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.06, shadowRadius: 4 },
      android: { elevation: 2 },
    }),
  },
  siteName: { fontSize: 11, fontWeight: '800', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 3 },
  title: { fontSize: 28, fontWeight: '800', letterSpacing: -0.5 },
  subtitle: { fontSize: 13, marginTop: 2 },

  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 40,
    paddingBottom: 80,
  },
  iconWrap: {
    width: 96,
    height: 96,
    borderRadius: 24,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 24,
  },
  heading: { fontSize: 22, fontWeight: '800', marginBottom: 10, letterSpacing: -0.3 },
  body: { fontSize: 15, textAlign: 'center', lineHeight: 22 },
});
