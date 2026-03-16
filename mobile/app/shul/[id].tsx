import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Linking,
  Platform,
  RefreshControl,
  ScrollView,
  SectionList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams, Stack } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { addDays, format, parseISO, startOfWeek, endOfWeek } from 'date-fns';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import MinyanCard from '@/components/MinyanCard';
import ErrorState from '@/components/ErrorState';
import { useOrganization, useOrgSchedule } from '@/api/hooks';
import { toApiDate } from '@/api/client';
import type { ScheduleEvent } from '@/api/types';

// ── Helpers ──────────────────────────────────────────────────────────────────

type ViewMode = 'today' | 'week';

const TYPE_ORDER = ['SHACHARIS', 'MINCHA', 'MINCHA_MAARIV', 'MAARIV', 'SELICHOS', 'MEGILA_READING'];
function sortKey(t: string) { return TYPE_ORDER.indexOf(t) === -1 ? 99 : TYPE_ORDER.indexOf(t); }

interface Section { title: string; date: string; data: ScheduleEvent[] }

function buildSections(events: ScheduleEvent[], highlightId?: string): Section[] {
  const byDate: Record<string, ScheduleEvent[]> = {};
  for (const e of events) {
    if (!byDate[e.date]) byDate[e.date] = [];
    byDate[e.date].push(e);
  }
  return Object.entries(byDate)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, items]) => ({
      title: format(parseISO(date), 'EEEE, MMM d'),
      date,
      data: items.sort((a, b) => {
        const td = sortKey(a.minyanType) - sortKey(b.minyanType);
        return td !== 0 ? td : a.startTime.localeCompare(b.startTime);
      }),
    }))
    .filter((s) => s.data.length > 0);
}

function ensureHttps(url: string): string {
  if (!url) return url;
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  return `https://${url}`;
}

function openDirections(address: string) {
  const encoded = encodeURIComponent(address);
  // Apple Maps on iOS, Google Maps on Android
  const url = Platform.select({
    ios: `maps://?q=${encoded}`,
    android: `geo:0,0?q=${encoded}`,
    default: `https://maps.google.com/maps?q=${encoded}`,
  });
  Linking.openURL(url!);
}

// ── Screen ────────────────────────────────────────────────────────────────────

export default function ShulDetailScreen() {
  const { id, selectedEventId } = useLocalSearchParams<{ id: string; selectedEventId?: string }>();
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  const today = toApiDate(new Date());
  const [viewMode, setViewMode] = useState<ViewMode>('today');

  const rangeStart = viewMode === 'today' ? today : toApiDate(startOfWeek(new Date(), { weekStartsOn: 0 }));
  const rangeEnd   = viewMode === 'today' ? today : toApiDate(endOfWeek(new Date(), { weekStartsOn: 0 }));

  const { data: org, isLoading: orgLoading } = useOrganization(id ?? '');
  const {
    data: events,
    isLoading: eventsLoading,
    isError,
    refetch,
    isFetching,
  } = useOrgSchedule(id ?? '', { start: rangeStart, end: rangeEnd });

  const sections = useMemo(
    () => buildSections(events ?? [], selectedEventId),
    [events, selectedEventId],
  );

  const orgColor = org?.color ?? colors.tint;
  const isLoading = orgLoading || eventsLoading;

  const openWebsite = async () => {
    if (!org?.websiteUrl) return;
    const url = ensureHttps(org.websiteUrl);
    await WebBrowser.openBrowserAsync(url, { presentationStyle: WebBrowser.WebBrowserPresentationStyle.FORM_SHEET });
  };

  const openWhatsApp = () => {
    if (!org?.whatsapp) return;
    const num = org.whatsapp.replace(/\D/g, '');
    WebBrowser.openBrowserAsync(`https://wa.me/${num}`);
  };

  return (
    <>
      <Stack.Screen
        options={{
          title: '',
          headerBackTitle: 'Today',
          headerStyle: { backgroundColor: colors.card },
          headerTintColor: orgColor,
          headerShadowVisible: false,
        }}
      />
      <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]} edges={['bottom']}>

        {/* ── Org header card ── */}
        <View style={[styles.orgCard, { backgroundColor: colors.card, borderBottomColor: colors.border, borderLeftColor: orgColor }]}>
          <View style={styles.orgCardBody}>
            <Text style={[styles.siteNameSmall, { color: colors.tint }]}>Teaneck Minyanim</Text>
            <Text style={[styles.orgName, { color: colors.text }]}>{org?.name ?? ''}</Text>
            <View style={styles.orgMeta}>
              {org?.nusachDisplay ? (
                <View style={[styles.chip, { backgroundColor: colors.background, borderColor: colors.border }]}>
                  <Text style={[styles.chipText, { color: colors.textSecondary }]}>{org.nusachDisplay}</Text>
                </View>
              ) : null}
            </View>
            {org?.address ? (
              <TouchableOpacity
                style={styles.addressRow}
                onPress={() => openDirections(org.address!)}>
                <Text style={[styles.orgAddress, { color: colors.tint }]} numberOfLines={1}>
                  📍 {org.address}
                </Text>
                <View style={[styles.dirBtn, { backgroundColor: colors.tint }]}>
                  <Text style={styles.dirBtnText}>Directions</Text>
                </View>
              </TouchableOpacity>
            ) : null}
          </View>

          {/* Action buttons */}
          <View style={styles.orgActions}>
            {org?.websiteUrl ? (
              <TouchableOpacity
                style={[styles.actionBtn, { backgroundColor: colors.background, borderColor: colors.border }]}
                onPress={openWebsite}>
                <Text style={styles.actionIcon}>🌐</Text>
                <Text style={[styles.actionLabel, { color: colors.textSecondary }]}>Website</Text>
              </TouchableOpacity>
            ) : null}
            {org?.whatsapp ? (
              <TouchableOpacity
                style={[styles.actionBtn, { backgroundColor: '#E8FAF0', borderColor: '#25D366' }]}
                onPress={openWhatsApp}>
                <Text style={styles.actionIcon}>💬</Text>
                <Text style={[styles.actionLabel, { color: '#1A9E4A' }]}>WhatsApp</Text>
              </TouchableOpacity>
            ) : null}
          </View>
        </View>

        {/* ── View mode toggle ── */}
        <View style={[styles.toggleBar, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
          <View style={[styles.toggleTrack, { backgroundColor: colors.background }]}>
            {(['today', 'week'] as ViewMode[]).map((mode) => (
              <TouchableOpacity
                key={mode}
                style={[
                  styles.toggleBtn,
                  viewMode === mode && { backgroundColor: colors.card, shadowColor: colors.shadow },
                ]}
                onPress={() => setViewMode(mode)}>
                <Text style={[
                  styles.toggleText,
                  { color: viewMode === mode ? colors.text : colors.textSecondary },
                  viewMode === mode && { fontWeight: '700' },
                ]}>
                  {mode === 'today' ? 'Today' : 'This Week'}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* ── Content ── */}
        {isLoading ? (
          <View style={styles.center}>
            <ActivityIndicator color={orgColor} size="large" />
          </View>
        ) : isError ? (
          <ErrorState message="Could not load schedule." onRetry={refetch} />
        ) : sections.length === 0 ? (
          <View style={styles.center}>
            <Text style={styles.emptyIcon}>
              {viewMode === 'today' ? '🌙' : '📅'}
            </Text>
            <Text style={[styles.emptyText, { color: colors.textSecondary }]}>
              {viewMode === 'today'
                ? 'No minyanim scheduled today.'
                : 'No minyanim this week.'}
            </Text>
            {viewMode === 'today' && (
              <TouchableOpacity onPress={() => setViewMode('week')} style={{ marginTop: 12 }}>
                <Text style={[styles.switchHint, { color: colors.tint }]}>View this week →</Text>
              </TouchableOpacity>
            )}
          </View>
        ) : (
          <SectionList
            sections={sections}
            keyExtractor={(item) => item.id}
            renderItem={({ item }) => (
              <MinyanCard
                event={item}
                showOrg={false}
                isHighlighted={item.id === selectedEventId}
              />
            )}
            renderSectionHeader={({ section }) => (
              viewMode === 'week' ? (
                <View style={[styles.sectionHeader, { backgroundColor: colors.background }]}>
                  <View style={[styles.sectionLine, { backgroundColor: colors.border }]} />
                  <Text style={[styles.sectionTitle, { color: colors.textSecondary }]}>
                    {section.title}
                  </Text>
                  <View style={[styles.sectionLine, { backgroundColor: colors.border }]} />
                </View>
              ) : null
            )}
            contentContainerStyle={styles.list}
            stickySectionHeadersEnabled={false}
            refreshControl={
              <RefreshControl
                refreshing={isFetching && !eventsLoading}
                onRefresh={refetch}
                tintColor={orgColor}
              />
            }
          />
        )}
      </SafeAreaView>
    </>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  safe: { flex: 1 },

  orgCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    borderBottomWidth: 1,
    borderLeftWidth: 4,
    paddingHorizontal: 16,
    paddingVertical: 14,
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 3 },
      android: { elevation: 1 },
    }),
  },
  orgCardBody: { flex: 1 },
  siteNameSmall: {
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 2,
  },
  orgName: { fontSize: 20, fontWeight: '800', letterSpacing: -0.3, marginBottom: 6 },
  orgMeta: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  chip: { borderRadius: 6, borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3 },
  chipText: { fontSize: 11, fontWeight: '600' },
  addressRow: { flexDirection: 'row', alignItems: 'center', marginTop: 6, gap: 8, flexShrink: 1 },
  orgAddress: { fontSize: 13, flex: 1, fontWeight: '500' },
  dirBtn: {
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  dirBtnText: { fontSize: 12, fontWeight: '700', color: '#fff' },

  orgActions: { gap: 8, marginLeft: 12, alignItems: 'flex-end' },
  actionBtn: {
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 6,
    alignItems: 'center',
    minWidth: 72,
  },
  actionIcon: { fontSize: 18 },
  actionLabel: { fontSize: 11, fontWeight: '600', marginTop: 2 },

  toggleBar: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: 1,
  },
  toggleTrack: {
    flexDirection: 'row',
    borderRadius: 10,
    padding: 3,
  },
  toggleBtn: {
    flex: 1,
    paddingVertical: 7,
    alignItems: 'center',
    borderRadius: 8,
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.08, shadowRadius: 3 },
      android: { elevation: 1 },
    }),
  },
  toggleText: { fontSize: 14 },

  list: { paddingTop: 8, paddingBottom: 36 },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 18,
    paddingBottom: 8,
    gap: 10,
  },
  sectionLine: { flex: 1, height: 1 },
  sectionTitle: { fontSize: 12, fontWeight: '700', letterSpacing: 0.5 },

  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  emptyIcon: { fontSize: 36, marginBottom: 12 },
  emptyText: { fontSize: 15, textAlign: 'center' },
  switchHint: { fontSize: 14, fontWeight: '600' },
});
