import React from 'react';
import {
  ActivityIndicator,
  FlatList,
  Platform,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import ErrorState from '@/components/ErrorState';
import { useOrganizations } from '@/api/hooks';
import type { Organization } from '@/api/types';

export default function ShulsScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  const { data: rawOrgs, isLoading, isError, refetch, isFetching } = useOrganizations();
  // Alphabetical by default; distance sort added once map geocoding is live (#143)
  const orgs = rawOrgs ? [...rawOrgs].sort((a, b) => a.name.localeCompare(b.name)) : rawOrgs;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]} edges={['top']}>
      {/* Header */}
      <View style={[styles.header, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
        <Text style={[styles.siteName, { color: colors.tint }]}>Teaneck Minyanim</Text>
        <Text style={[styles.title, { color: colors.text }]}>Shuls</Text>
        {orgs ? (
          <Text style={[styles.subtitle, { color: colors.textSecondary }]}>
            {orgs.length} shul{orgs.length !== 1 ? 's' : ''} · Teaneck, NJ
          </Text>
        ) : null}
      </View>

      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator color={colors.tint} size="large" />
        </View>
      ) : isError ? (
        <ErrorState message="Could not load shuls." onRetry={refetch} />
      ) : (
        <FlatList
          data={orgs}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => <ShulRow org={item} colors={colors} />}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => (
            <View style={[styles.separator, { backgroundColor: colors.border }]} />
          )}
          refreshControl={
            <RefreshControl
              refreshing={isFetching && !isLoading}
              onRefresh={refetch}
              tintColor={colors.tint}
            />
          }
        />
      )}
    </SafeAreaView>
  );
}

function ShulRow({ org, colors }: { org: Organization; colors: typeof Colors.light }) {
  const orgColor = org.color ?? colors.tint;

  return (
    <Pressable
      style={({ pressed }) => [
        styles.row,
        { backgroundColor: pressed ? colors.surfaceHover : colors.card },
      ]}
      onPress={() =>
        router.push({ pathname: '/shuls/[id]', params: { id: org.slug ?? org.id } } as never)
      }>
      {/* Color swatch left strip */}
      <View style={[styles.swatch, { backgroundColor: orgColor }]} />

      <View style={styles.rowBody}>
        <Text style={[styles.orgName, { color: colors.text }]}>{org.name}</Text>
        <View style={styles.rowMeta}>
          {org.nusachDisplay ? (
            <View style={[styles.chip, { backgroundColor: colors.background, borderColor: colors.border }]}>
              <Text style={[styles.chipText, { color: colors.textSecondary }]}>{org.nusachDisplay}</Text>
            </View>
          ) : null}
          {org.address ? (
            <Text style={[styles.address, { color: colors.textSecondary }]} numberOfLines={1}>
              {org.address}
            </Text>
          ) : null}
        </View>
      </View>

      <Text style={[styles.chevron, { color: colors.textTertiary }]}>›</Text>
    </Pressable>
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

  list: { paddingBottom: 32 },
  separator: { height: 1, marginLeft: 50 },

  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    paddingRight: 14,
  },
  swatch: { width: 4, alignSelf: 'stretch', borderRadius: 2, marginRight: 0 },
  rowBody: { flex: 1, paddingHorizontal: 14 },
  orgName: { fontSize: 16, fontWeight: '700', marginBottom: 4 },
  rowMeta: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  chip: { borderRadius: 5, borderWidth: 1, paddingHorizontal: 7, paddingVertical: 2 },
  chipText: { fontSize: 11, fontWeight: '600' },
  address: { fontSize: 12, flex: 1 },
  chevron: { fontSize: 22, fontWeight: '300' },

  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
