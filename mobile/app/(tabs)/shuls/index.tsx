import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Platform,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import * as Location from 'expo-location';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import ErrorState from '@/components/ErrorState';
import { useOrganizations } from '@/api/hooks';
import type { Organization } from '@/api/types';

type SortMode = 'alpha' | 'distance';

function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(km: number): string {
  const miles = km * 0.621371;
  return miles < 0.1 ? 'nearby' : `${miles.toFixed(1)} mi`;
}

export default function ShulsScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  const { data: rawOrgs, isLoading, isError, refetch, isFetching } = useOrganizations();
  const [sortMode, setSortMode] = useState<SortMode>('alpha');
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null);
  const [locationGranted, setLocationGranted] = useState<boolean>(false);

  const fetchLocation = useCallback(async () => {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status === 'granted') {
      setLocationGranted(true);
      const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setUserLocation({ lat: loc.coords.latitude, lng: loc.coords.longitude });
    }
  }, []);

  // Request location once; enable distance sort button if granted
  useEffect(() => { fetchLocation(); }, [fetchLocation]);

  const orgs = React.useMemo(() => {
    if (!rawOrgs) return rawOrgs;
    const copy = [...rawOrgs];
    if (sortMode === 'distance' && userLocation) {
      copy.sort((a, b) => {
        const da =
          a.latitude != null && a.longitude != null
            ? haversineKm(userLocation.lat, userLocation.lng, a.latitude, a.longitude)
            : Infinity;
        const db =
          b.latitude != null && b.longitude != null
            ? haversineKm(userLocation.lat, userLocation.lng, b.latitude, b.longitude)
            : Infinity;
        return da - db;
      });
    } else {
      copy.sort((a, b) => a.name.localeCompare(b.name));
    }
    return copy;
  }, [rawOrgs, sortMode, userLocation]);

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]} edges={['top']}>
      {/* Header */}
      <View style={[styles.header, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
        <View style={styles.headerLeft}>
          <Text style={[styles.siteName, { color: colors.tint }]}>Teaneck Minyanim</Text>
          <Text style={[styles.title, { color: colors.text }]}>Shuls</Text>
          {orgs ? (
            <Text style={[styles.subtitle, { color: colors.textSecondary }]}>
              {orgs.length} shul{orgs.length !== 1 ? 's' : ''} · Teaneck, NJ
            </Text>
          ) : null}
        </View>

        {/* Sort toggle — only show distance if location was granted */}
        <View style={[styles.sortToggle, { backgroundColor: colors.background, borderColor: colors.border }]}>
          <TouchableOpacity
            style={[styles.sortBtn, sortMode === 'alpha' && { backgroundColor: colors.card }]}
            onPress={() => setSortMode('alpha')}>
            <Text style={[styles.sortBtnText, { color: sortMode === 'alpha' ? colors.text : colors.textSecondary }]}>
              A–Z
            </Text>
          </TouchableOpacity>
          {locationGranted && (
            <TouchableOpacity
              style={[styles.sortBtn, sortMode === 'distance' && { backgroundColor: colors.card }]}
              onPress={() => setSortMode('distance')}>
              <Text style={[styles.sortBtnText, { color: sortMode === 'distance' ? colors.text : colors.textSecondary }]}>
                Near Me
              </Text>
            </TouchableOpacity>
          )}
        </View>
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
          renderItem={({ item }) => (
            <ShulRow
              org={item}
              colors={colors}
              distance={
                sortMode === 'distance' && userLocation && item.latitude != null && item.longitude != null
                  ? formatDistance(haversineKm(userLocation.lat, userLocation.lng, item.latitude, item.longitude))
                  : null
              }
            />
          )}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => (
            <View style={[styles.separator, { backgroundColor: colors.border }]} />
          )}
          refreshControl={
            <RefreshControl
              refreshing={isFetching && !isLoading}
              onRefresh={() => { refetch(); fetchLocation(); }}
              tintColor={colors.tint}
            />
          }
        />
      )}
    </SafeAreaView>
  );
}

function ShulRow({
  org,
  colors,
  distance,
}: {
  org: Organization;
  colors: typeof Colors.light;
  distance: string | null;
}) {
  const orgColor = org.color ?? colors.tint;

  return (
    <Pressable
      style={({ pressed }) => [
        styles.row,
        { backgroundColor: pressed ? colors.surfaceHover : colors.card },
      ]}
      onPress={() =>
        router.push({ pathname: '/shul/[id]', params: { id: org.slug ?? org.id } } as never)
      }>
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

      <View style={styles.rowRight}>
        {distance ? (
          <Text style={[styles.distance, { color: colors.tint }]}>{distance}</Text>
        ) : null}
        <Text style={[styles.chevron, { color: colors.textTertiary }]}>›</Text>
      </View>
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
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.06, shadowRadius: 4 },
      android: { elevation: 2 },
    }),
  },
  headerLeft: { flex: 1, marginRight: 12 },
  siteName: { fontSize: 11, fontWeight: '800', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 3 },
  title: { fontSize: 20, fontWeight: '800', letterSpacing: -0.3 },
  subtitle: { fontSize: 13, marginTop: 2 },

  sortToggle: {
    flexDirection: 'row',
    borderRadius: 10,
    borderWidth: 1,
    padding: 3,
    alignSelf: 'flex-end',
    marginBottom: 2,
  },
  sortBtn: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 7,
  },
  sortBtnText: { fontSize: 12, fontWeight: '600' },

  list: { paddingBottom: 100 },
  separator: { height: 1, marginLeft: 50 },

  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    paddingRight: 14,
  },
  swatch: { width: 4, alignSelf: 'stretch', borderRadius: 2 },
  rowBody: { flex: 1, paddingHorizontal: 14 },
  orgName: { fontSize: 16, fontWeight: '700', marginBottom: 4 },
  rowMeta: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  chip: { borderRadius: 5, borderWidth: 1, paddingHorizontal: 7, paddingVertical: 2 },
  chipText: { fontSize: 11, fontWeight: '600' },
  address: { fontSize: 12, flex: 1 },

  rowRight: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  distance: { fontSize: 12, fontWeight: '600' },
  chevron: { fontSize: 22, fontWeight: '300' },

  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
