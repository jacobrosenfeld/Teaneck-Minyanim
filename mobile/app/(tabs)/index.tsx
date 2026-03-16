import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  LayoutChangeEvent,
  Modal,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Reanimated, { FadeInDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { format } from 'date-fns';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import MinyanCard from '@/components/MinyanCard';
import ErrorState from '@/components/ErrorState';
import { useTodaySchedule, useZmanim, useOrganizations } from '@/api/hooks';
import type { ScheduleEvent, Organization, ZmanimTimes } from '@/api/types';
import { formatTime, getNextZman } from '@/utils/time';

// ── Types ─────────────────────────────────────────────────────────────────────

type TypeFilter = 'ALL' | 'SHACHARIS' | 'MINCHA' | 'MAARIV';

type ListItem =
  | { _type: 'event'; event: ScheduleEvent; key: string }
  | { _type: 'now'; key: 'now-divider'; timeStr: string }
  | { _type: 'no_more'; key: 'no-more-today' };

// ── Constants ─────────────────────────────────────────────────────────────────

const MINCHA_GROUP = new Set(['MINCHA', 'MINCHA_MAARIV']);

const TYPE_FILTERS: { key: TypeFilter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'SHACHARIS', label: 'Shacharis' },
  { key: 'MINCHA', label: 'Mincha' },
  { key: 'MAARIV', label: 'Maariv' },
];

const ZMANIM_ORDERED: { label: string; key: keyof ZmanimTimes }[] = [
  { label: 'Alos HaShachar', key: 'alotHashachar' },
  { label: 'Misheyakir', key: 'misheyakir' },
  { label: 'Netz', key: 'netz' },
  { label: 'Sof Zman Krias Shema', key: 'sofZmanShmaGra' },
  { label: 'Sof Zman Tefilla', key: 'sofZmanTfilaGra' },
  { label: 'Chatzos', key: 'chatzos' },
  { label: 'Mincha Gedola', key: 'minchaGedola' },
  { label: 'Mincha Ketana', key: 'minchaKetana' },
  { label: 'Plag HaMincha', key: 'plagHamincha' },
  { label: 'Shekiya', key: 'shekiya' },
  { label: 'Tzes HaKochavim', key: 'tzeis' },
];

function getNowTime(): string {
  const n = new Date();
  return `${n.getHours().toString().padStart(2, '0')}:${n.getMinutes().toString().padStart(2, '0')}`;
}

function matchesTypeFilter(eventType: string, filter: TypeFilter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'MINCHA') return MINCHA_GROUP.has(eventType);
  return eventType === filter;
}

// ── Screen ────────────────────────────────────────────────────────────────────

export default function TodayScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
  const [orgFilter, setOrgFilter] = useState<string | null>(null);
  const [orgPickerVisible, setOrgPickerVisible] = useState(false);
  const [nowTime, setNowTime] = useState(getNowTime);
  const [scrollY, setScrollY] = useState(0);

  // Use ScrollView + exact onLayout position — eliminates getItemLayout estimation errors
  const scrollViewRef = useRef<ScrollView>(null);
  const nowYRef = useRef(-1);       // exact Y of NOW divider, set by onLayout
  const hasAutoScrolled = useRef(false);
  const jumpBtnOpacity = useRef(new Animated.Value(0)).current;

  const { data: events, isLoading, isError, refetch, isFetching } = useTodaySchedule();
  const { data: zmanim } = useZmanim();
  const { data: orgs } = useOrganizations();

  // Update current time every minute
  useEffect(() => {
    const id = setInterval(() => setNowTime(getNowTime()), 60_000);
    return () => clearInterval(id);
  }, []);

  // Next upcoming zman
  const nextZman = useMemo(() => {
    if (!zmanim?.times) return null;
    const entries = ZMANIM_ORDERED.map((z) => ({
      label: z.label,
      key: z.key,
      time: zmanim.times[z.key] ?? null,
    }));
    return getNextZman(entries);
  }, [zmanim]);

  // Filter events
  const filtered = useMemo(() => {
    if (!events) return [];
    return events.filter((e) => {
      if (!matchesTypeFilter(e.minyanType, typeFilter)) return false;
      if (orgFilter && e.organization?.id !== orgFilter) return false;
      return true;
    });
  }, [events, typeFilter, orgFilter]);

  // Build list: events sorted by time, NOW divider at current time,
  // "no more" message when all minyanim are in the past
  const listItems = useMemo((): ListItem[] => {
    const sorted = [...filtered].sort((a, b) => a.startTime.localeCompare(b.startTime));
    const nowTimeStr = formatTime(nowTime);
    const insertAt = sorted.findIndex((e) => e.startTime >= nowTime);
    const allPast = insertAt === -1;
    const splitAt = allPast ? sorted.length : insertAt;

    const items: ListItem[] = [];
    for (let i = 0; i < sorted.length; i++) {
      if (i === splitAt) {
        items.push({ _type: 'now', key: 'now-divider', timeStr: nowTimeStr });
      }
      items.push({ _type: 'event', event: sorted[i], key: sorted[i].id });
    }
    // If all events are in the past: NOW divider + "no more" message at end
    if (allPast && sorted.length > 0) {
      items.push({ _type: 'now', key: 'now-divider', timeStr: nowTimeStr });
      items.push({ _type: 'no_more', key: 'no-more-today' });
    }
    // If no events at all, still add the NOW divider so it shows on empty days
    if (sorted.length === 0) {
      items.push({ _type: 'now', key: 'now-divider', timeStr: nowTimeStr });
    }
    return items;
  }, [filtered, nowTime]);

  // Called by onLayout on the NOW divider — gives us the exact pixel Y position.
  // This is the reliable alternative to scrollToIndex with estimated heights.
  const onNowDividerLayout = useCallback((e: LayoutChangeEvent) => {
    const y = e.nativeEvent.layout.y;
    nowYRef.current = y;
    if (!hasAutoScrolled.current) {
      hasAutoScrolled.current = true;
      // Small rAF to ensure the layout pass is fully committed before scrolling
      requestAnimationFrame(() => {
        scrollViewRef.current?.scrollTo({ y: Math.max(0, y - 130), animated: false });
      });
    }
  }, []);

  const scrollToNow = useCallback(() => {
    if (nowYRef.current >= 0) {
      scrollViewRef.current?.scrollTo({ y: Math.max(0, nowYRef.current - 130), animated: true });
    }
  }, []);

  // Show Jump to Now when scrolled >300px away from the NOW divider
  const showJump = !isLoading && nowYRef.current >= 0 &&
    listItems.filter((i) => i._type === 'event').length > 0 &&
    Math.abs(scrollY - nowYRef.current) > 300;

  useEffect(() => {
    Animated.timing(jumpBtnOpacity, {
      toValue: showJump ? 1 : 0,
      duration: 200,
      useNativeDriver: true,
    }).start();
  }, [showJump, jumpBtnOpacity]);

  const selectedOrg = orgs?.find((o) => o.id === orgFilter);
  const activeFilters = typeFilter !== 'ALL' || orgFilter !== null;
  const today = format(new Date(), 'EEEE, MMMM d');
  const hasEvents = listItems.some((i) => i._type === 'event');

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]} edges={['top']}>

      {/* ── Header ── */}
      <View style={[styles.header, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
        <View style={styles.headerMain}>
          <View style={styles.headerLeft}>
            <Text style={[styles.siteName, { color: colors.tint }]}>Teaneck Minyanim</Text>
            <View style={styles.dateRow}>
              <Text style={[styles.headerDate, { color: colors.text }]}>{today}</Text>
              {zmanim?.hebrewDate ? (
                <Text style={[styles.hebrewDate, { color: colors.textSecondary }]}>
                  {zmanim.hebrewDate}
                </Text>
              ) : null}
            </View>
          </View>

          {nextZman && (
            <View style={[styles.nextZmanBox, { backgroundColor: colors.background, borderColor: colors.border }]}>
              <Text style={[styles.nextZmanLabel, { color: colors.textSecondary }]}>Next</Text>
              <Text style={[styles.nextZmanName, { color: colors.text }]} numberOfLines={1}>
                {nextZman.label}
              </Text>
              <Text style={[styles.nextZmanTime, { color: colors.tint }]}>
                {formatTime(nextZman.time)}
              </Text>
            </View>
          )}
        </View>
      </View>

      {/* ── Filter chips ── */}
      <View style={[styles.filterBar, { borderBottomColor: colors.border }]}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterChips}>
          {TYPE_FILTERS.map((f) => {
            const active = typeFilter === f.key;
            return (
              <TouchableOpacity
                key={f.key}
                style={[
                  styles.chip,
                  active
                    ? { backgroundColor: colors.tint }
                    : { backgroundColor: colors.card, borderColor: colors.border, borderWidth: 1 },
                ]}
                onPress={() => setTypeFilter(f.key)}>
                <Text style={[styles.chipText, { color: active ? '#fff' : colors.textSecondary }]}>
                  {f.label}
                </Text>
              </TouchableOpacity>
            );
          })}

          <TouchableOpacity
            style={[
              styles.chip,
              orgFilter
                ? { backgroundColor: colors.tint }
                : { backgroundColor: colors.card, borderColor: colors.border, borderWidth: 1 },
            ]}
            onPress={() => setOrgPickerVisible(true)}>
            <Text style={[styles.chipText, { color: orgFilter ? '#fff' : colors.textSecondary }]}>
              {selectedOrg ? selectedOrg.name : 'All Shuls'} ▾
            </Text>
          </TouchableOpacity>
        </ScrollView>
      </View>

      {/* ── Content ── */}
      {isLoading && !hasEvents ? (
        <View style={styles.center}>
          <ActivityIndicator color={colors.tint} size="large" />
        </View>
      ) : isError ? (
        <ErrorState message="Could not load today's schedule." onRetry={refetch} />
      ) : !hasEvents ? (
        <View style={styles.center}>
          {activeFilters ? (
            <>
              <Text style={styles.emptyIcon}>🔍</Text>
              <Text style={[styles.emptyText, { color: colors.textSecondary }]}>
                No minyanim match these filters.
              </Text>
              <TouchableOpacity onPress={() => { setTypeFilter('ALL'); setOrgFilter(null); }}>
                <Text style={[styles.clearLink, { color: colors.tint }]}>Clear filters</Text>
              </TouchableOpacity>
            </>
          ) : (
            <Text style={[styles.emptyText, { color: colors.textSecondary }]}>
              No minyanim scheduled today.
            </Text>
          )}
        </View>
      ) : (
        <ScrollView
          ref={scrollViewRef}
          contentContainerStyle={styles.list}
          onScroll={(e) => setScrollY(e.nativeEvent.contentOffset.y)}
          scrollEventThrottle={16}
          refreshControl={
            <RefreshControl
              refreshing={isFetching && !isLoading}
              onRefresh={() => {
                hasAutoScrolled.current = false;
                nowYRef.current = -1;
                refetch();
              }}
              tintColor={colors.tint}
            />
          }>
          {listItems.map((item, index) => {
            if (item._type === 'now') {
              return (
                // onLayout gives the exact Y coordinate — used for scrollTo (reliable vs scrollToIndex)
                <View key={item.key} onLayout={onNowDividerLayout}>
                  <View style={styles.nowDivider}>
                    <View style={[styles.nowLine, { backgroundColor: colors.tint }]} />
                    <View style={[styles.nowBadge, { backgroundColor: colors.tint }]}>
                      <Text style={styles.nowBadgeText}>NOW  {item.timeStr}</Text>
                    </View>
                    <View style={[styles.nowLine, { backgroundColor: colors.tint }]} />
                  </View>
                </View>
              );
            }

            if (item._type === 'no_more') {
              return (
                <View key={item.key} style={styles.noMoreBox}>
                  <Text style={[styles.noMoreText, { color: colors.textTertiary }]}>
                    No more minyanim today
                  </Text>
                </View>
              );
            }

            const { event } = item;
            const delay = Math.min(index * 20, 300);
            return (
              <Reanimated.View key={item.key} entering={FadeInDown.delay(delay).duration(320)}>
                <MinyanCard
                  event={event}
                  showOrg
                  isNext={false}
                  onPress={() =>
                    router.push({
                      pathname: '/shuls/[id]',
                      params: {
                        id: event.organization?.slug ?? event.organization?.id ?? '',
                        selectedEventId: event.id,
                      },
                    } as never)
                  }
                />
              </Reanimated.View>
            );
          })}
        </ScrollView>
      )}

      {/* ── Jump to Now FAB ── */}
      <Animated.View
        style={[styles.jumpBtn, { opacity: jumpBtnOpacity }]}
        pointerEvents={showJump ? 'auto' : 'none'}>
        <TouchableOpacity
          style={[styles.jumpBtnInner, { backgroundColor: colors.tint }]}
          onPress={scrollToNow}>
          <Text style={styles.jumpBtnText}>Next Minyan ↓</Text>
        </TouchableOpacity>
      </Animated.View>

      {/* ── Shul picker modal ── */}
      <OrgPickerModal
        visible={orgPickerVisible}
        orgs={orgs ?? []}
        selected={orgFilter}
        colors={colors}
        onSelect={(id) => { setOrgFilter(id); setOrgPickerVisible(false); }}
        onClose={() => setOrgPickerVisible(false)}
      />
    </SafeAreaView>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────────

function OrgPickerModal({
  visible, orgs, selected, colors, onSelect, onClose,
}: {
  visible: boolean;
  orgs: Organization[];
  selected: string | null;
  colors: typeof Colors.light;
  onSelect: (id: string | null) => void;
  onClose: () => void;
}) {
  return (
    <Modal visible={visible} animationType="slide" transparent presentationStyle="pageSheet">
      <View style={styles.overlay}>
        <View style={[styles.sheet, { backgroundColor: colors.card }]}>
          <View style={[styles.handle, { backgroundColor: colors.border }]} />
          <Text style={[styles.sheetTitle, { color: colors.text }]}>Filter by Shul</Text>

          <ScrollView style={styles.sheetList} showsVerticalScrollIndicator={false}>
            <Pressable
              style={({ pressed }) => [
                styles.sheetRow,
                { borderBottomColor: colors.border },
                (pressed || selected === null) && { backgroundColor: colors.background },
              ]}
              onPress={() => onSelect(null)}>
              <Text style={[styles.sheetRowText, { color: colors.text }]}>All Shuls</Text>
              {selected === null && <Text style={{ color: colors.tint, fontSize: 16 }}>✓</Text>}
            </Pressable>

            {orgs.map((org) => (
              <Pressable
                key={org.id}
                style={({ pressed }) => [
                  styles.sheetRow,
                  { borderBottomColor: colors.border },
                  (pressed || selected === org.id) && { backgroundColor: colors.background },
                ]}
                onPress={() => onSelect(org.id)}>
                <View style={styles.sheetRowInner}>
                  <View style={[styles.orgDot, { backgroundColor: org.color ?? colors.tint }]} />
                  <Text style={[styles.sheetRowText, { color: colors.text }]}>{org.name}</Text>
                </View>
                {selected === org.id && <Text style={{ color: colors.tint, fontSize: 16 }}>✓</Text>}
              </Pressable>
            ))}
          </ScrollView>

          <TouchableOpacity
            style={[styles.sheetClose, { borderTopColor: colors.border }]}
            onPress={onClose}>
            <Text style={[styles.sheetCloseText, { color: colors.tint }]}>Done</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

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
  headerMain: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
  },
  headerLeft: { flex: 1, marginRight: 12 },
  siteName: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  dateRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
  },
  headerDate: { fontSize: 22, fontWeight: '800', letterSpacing: -0.5 },
  hebrewDate: { fontSize: 20, fontWeight: '600', letterSpacing: -0.3, textAlign: 'right' },

  nextZmanBox: {
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
    alignItems: 'flex-end',
    minWidth: 110,
  },
  nextZmanLabel: { fontSize: 9, fontWeight: '700', letterSpacing: 0.8, textTransform: 'uppercase', marginBottom: 2 },
  nextZmanName: { fontSize: 12, fontWeight: '700', marginBottom: 1, maxWidth: 110 },
  nextZmanTime: { fontSize: 15, fontWeight: '800' },

  filterBar: { paddingVertical: 10, borderBottomWidth: 1 },
  filterChips: { paddingHorizontal: 16, gap: 8 },
  chip: { borderRadius: 20, paddingHorizontal: 15, paddingVertical: 7 },
  chipText: { fontSize: 13, fontWeight: '600' },

  list: { paddingTop: 10, paddingBottom: 120 },

  nowDivider: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 16,
    marginVertical: 8,
    gap: 8,
  },
  nowLine: { flex: 1, height: 2, borderRadius: 1 },
  nowBadge: {
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 3,
  },
  nowBadgeText: {
    fontSize: 11,
    fontWeight: '800',
    color: '#fff',
    letterSpacing: 0.5,
  },

  noMoreBox: {
    alignItems: 'center',
    paddingVertical: 20,
    paddingHorizontal: 16,
  },
  noMoreText: {
    fontSize: 14,
    fontStyle: 'italic',
  },

  jumpBtn: {
    position: 'absolute',
    bottom: 28,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  jumpBtnInner: {
    borderRadius: 20,
    paddingHorizontal: 18,
    paddingVertical: 9,
    ...Platform.select({
      ios: { shadowColor: '#000', shadowOffset: { width: 0, height: 3 }, shadowOpacity: 0.2, shadowRadius: 8 },
      android: { elevation: 6 },
    }),
  },
  jumpBtnText: { fontSize: 13, fontWeight: '700', color: '#fff' },

  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  emptyIcon: { fontSize: 36, marginBottom: 12 },
  emptyText: { fontSize: 15, textAlign: 'center', lineHeight: 22 },
  clearLink: { fontSize: 14, fontWeight: '600', marginTop: 12 },

  overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.35)' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, paddingTop: 10, maxHeight: '78%' },
  handle: { width: 36, height: 4, borderRadius: 2, alignSelf: 'center', marginBottom: 14 },
  sheetTitle: { fontSize: 17, fontWeight: '700', paddingHorizontal: 20, marginBottom: 8 },
  sheetList: { flexGrow: 0 },
  sheetRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
  },
  sheetRowInner: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  orgDot: { width: 10, height: 10, borderRadius: 5, marginRight: 12 },
  sheetRowText: { fontSize: 15, flex: 1 },
  sheetClose: { padding: 18, alignItems: 'center', borderTopWidth: 1 },
  sheetCloseText: { fontSize: 16, fontWeight: '600' },
});
