import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  Dimensions,
  LayoutChangeEvent,
  Modal,
  PanResponder,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
import Reanimated, {
  FadeInDown,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  runOnJS,
} from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { format, addDays, subDays, parseISO } from 'date-fns';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import MinyanCard from '@/components/MinyanCard';
import ShulDaySheet from '@/components/ShulDaySheet';
import ErrorState from '@/components/ErrorState';
import { useSchedule, useZmanim, useOrganizations } from '@/api/hooks';
import { toApiDate } from '@/api/client';
import type { ScheduleEvent, Organization } from '@/api/types';
import { formatTime } from '@/utils/time';
import { registerScrollToNow, unregisterScrollToNow, registerGoToday, unregisterGoToday, registerOpenSheet, unregisterOpenSheet } from '@/utils/tabEvents';
import type { SheetTarget } from '@/utils/tabEvents';

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

export default function MinyanimScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];
  const insets = useSafeAreaInsets();

  const today = toApiDate(new Date());

  const [selectedDate, setSelectedDate] = useState(today);
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
  const [orgFilter, setOrgFilter] = useState<string | null>(null);
  const [sheetEvent, setSheetEvent] = useState<ScheduleEvent | null>(null);
  const [orgPickerVisible, setOrgPickerVisible] = useState(false);
  const [nowTime, setNowTime] = useState(getNowTime);
  const [scrollY, setScrollY] = useState(0);

  const parsedDate = parseISO(selectedDate);
  const isToday = selectedDate === today;

  // Use ScrollView + exact onLayout position for reliable scroll-to-now
  const scrollViewRef = useRef<ScrollView>(null);
  const nowYRef = useRef(-1);
  const hasAutoScrolled = useRef(false);
  const jumpBtnOpacity = useRef(new Animated.Value(0)).current;

  // Slide animation for day transitions — mirrors shul detail page
  const contentOpacity = useSharedValue(1);
  const contentTranslateX = useSharedValue(0);
  const animatedContentStyle = useAnimatedStyle(() => ({
    opacity: contentOpacity.value,
    transform: [{ translateX: contentTranslateX.value }],
  }));

  const animateTransition = useCallback((direction: 1 | -1, apply: () => void) => {
    'worklet';
    const SLIDE = 24;
    const OUT_MS = 130;
    const IN_MS = 220;
    contentTranslateX.value = withTiming(-direction * SLIDE, { duration: OUT_MS });
    contentOpacity.value = withTiming(0, { duration: OUT_MS }, () => {
      runOnJS(apply)();
      contentTranslateX.value = direction * SLIDE;
      contentOpacity.value = 0;
      contentTranslateX.value = withTiming(0, { duration: IN_MS });
      contentOpacity.value = withTiming(1, { duration: IN_MS });
    });
  }, [contentOpacity, contentTranslateX]);

  // Track whether initial card entrance animation has been shown (#166)
  const cardEnterAnimation = useRef(true);

  // Use a ref to always call the latest parsedDate without recreating PanResponder
  const parsedDateRef = useRef(parsedDate);
  parsedDateRef.current = parsedDate;

  const prevDay = useCallback(() =>
    animateTransition(-1, () => {
      cardEnterAnimation.current = false;
      hasAutoScrolled.current = false;
      nowYRef.current = -1;
      scrollViewRef.current?.scrollTo({ y: 0, animated: false });
      setSelectedDate(toApiDate(subDays(parsedDateRef.current, 1)));
    }), [animateTransition]);

  const nextDay = useCallback(() =>
    animateTransition(1, () => {
      cardEnterAnimation.current = false;
      hasAutoScrolled.current = false;
      nowYRef.current = -1;
      scrollViewRef.current?.scrollTo({ y: 0, animated: false });
      setSelectedDate(toApiDate(addDays(parsedDateRef.current, 1)));
    }), [animateTransition]);

  const goToday = useCallback(() => {
    if (selectedDate !== today) {
      animateTransition(1, () => {
        cardEnterAnimation.current = false;
        hasAutoScrolled.current = false;
        nowYRef.current = -1;
        scrollViewRef.current?.scrollTo({ y: 0, animated: false });
        setSelectedDate(today);
      });
    }
  }, [animateTransition, today, selectedDate]);

  // ── Smooth gesture: Animated.Value tracks finger in real time ────────────
  const dragX = useRef(new Animated.Value(0)).current;
  // Stores the inX value for the deferred slide-in (set in swipe callback,
  // consumed by useEffect after React commits the new-day render).
  const slideInXRef = useRef<number | null>(null);

  // Gesture-only day changers (no Reanimated slide — the drag animation handles visuals)
  const gesturePrevDay = useCallback(() => {
    cardEnterAnimation.current = false;
    hasAutoScrolled.current = false;
    nowYRef.current = -1;
    scrollViewRef.current?.scrollTo({ y: 0, animated: false });
    setSelectedDate(toApiDate(subDays(parsedDateRef.current, 1)));
  }, []);
  const gestureNextDay = useCallback(() => {
    cardEnterAnimation.current = false;
    hasAutoScrolled.current = false;
    nowYRef.current = -1;
    scrollViewRef.current?.scrollTo({ y: 0, animated: false });
    setSelectedDate(toApiDate(addDays(parsedDateRef.current, 1)));
  }, []);
  const gesturePrevDayRef = useRef(gesturePrevDay); gesturePrevDayRef.current = gesturePrevDay;
  const gestureNextDayRef = useRef(gestureNextDay); gestureNextDayRef.current = gestureNextDay;

  const swipe = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (evt, gs) =>
        evt.nativeEvent.pageX > 45 &&
        Math.abs(gs.dx) > 10 &&
        Math.abs(gs.dx) > Math.abs(gs.dy) * 1.2,
      onPanResponderMove: (_, gs) => { dragX.setValue(gs.dx); },
      onPanResponderRelease: (_, gs) => {
        const goNext = gs.dx < 0;
        if (Math.abs(gs.dx) > 50 || Math.abs(gs.vx) > 0.3) {
          const outX = goNext ? -SCREEN_WIDTH : SCREEN_WIDTH;
          const inX  = goNext ?  SCREEN_WIDTH * 0.25 : -SCREEN_WIDTH * 0.25;
          Animated.timing(dragX, { toValue: outX, duration: 120, useNativeDriver: false }).start(() => {
            // Store inX for the useEffect to consume after React commits the new render.
            // Keep dragX fully off-screen (outX) so the old content is never visible
            // during the state transition — the spring fires only after new content paints.
            slideInXRef.current = inX;
            if (goNext) gestureNextDayRef.current(); else gesturePrevDayRef.current();
            dragX.setValue(outX);
          });
        } else {
          Animated.spring(dragX, { toValue: 0, useNativeDriver: false, stiffness: 300, damping: 30 }).start();
        }
      },
      onPanResponderTerminate: () => {
        Animated.spring(dragX, { toValue: 0, useNativeDriver: false, stiffness: 300, damping: 30 }).start();
      },
    }),
  ).current;

  const { data: events, isLoading, isError, refetch, isFetching } = useSchedule({ date: selectedDate });
  const { data: zmanim } = useZmanim();
  const { data: orgs } = useOrganizations();

  // ── Prefetch ±2 days so adjacent days load instantly on swipe ─────────────
  useSchedule({ date: toApiDate(subDays(parseISO(selectedDate), 1)) });
  useSchedule({ date: toApiDate(subDays(parseISO(selectedDate), 2)) });
  useSchedule({ date: toApiDate(addDays(parseISO(selectedDate), 1)) });
  useSchedule({ date: toApiDate(addDays(parseISO(selectedDate), 2)) });

  // Deferred slide-in: fires after React commits the new selectedDate render,
  // ensuring the spring animation starts with the correct day's content visible.
  useEffect(() => {
    if (slideInXRef.current !== null) {
      const inX = slideInXRef.current;
      slideInXRef.current = null;
      dragX.setValue(inX);
      Animated.spring(dragX, { toValue: 0, useNativeDriver: false, stiffness: 260, damping: 28 }).start();
    }
  }, [selectedDate]); // eslint-disable-line react-hooks/exhaustive-deps

  // Update current time every minute
  useEffect(() => {
    const id = setInterval(() => setNowTime(getNowTime()), 60_000);
    return () => clearInterval(id);
  }, []);

  // Hebrew date: advances at tzeis per Jewish law (#159)
  const isAfterTzeis = !!(zmanim?.times?.tzeis && nowTime >= zmanim.times.tzeis);
  const tomorrowStr = toApiDate(addDays(new Date(), 1));
  const { data: tomorrowZmanim } = useZmanim(isAfterTzeis ? tomorrowStr : undefined);
  const hebrewDate = isAfterTzeis ? tomorrowZmanim?.hebrewDate : zmanim?.hebrewDate;

  // Filter events
  const filtered = useMemo(() => {
    if (!events) return [];
    return events.filter((e) => {
      if (!matchesTypeFilter(e.minyanType, typeFilter)) return false;
      if (orgFilter && e.organization?.id !== orgFilter) return false;
      return true;
    });
  }, [events, typeFilter, orgFilter]);

  // Build list — NOW divider only on today
  const listItems = useMemo((): ListItem[] => {
    const sorted = [...filtered].sort((a, b) => a.startTime.localeCompare(b.startTime));

    if (!isToday) {
      return sorted.map((e) => ({ _type: 'event' as const, event: e, key: e.id }));
    }

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
    if (allPast && sorted.length > 0) {
      items.push({ _type: 'now', key: 'now-divider', timeStr: nowTimeStr });
      items.push({ _type: 'no_more', key: 'no-more-today' });
    }
    if (sorted.length === 0) {
      items.push({ _type: 'now', key: 'now-divider', timeStr: nowTimeStr });
    }
    return items;
  }, [filtered, nowTime, isToday]);

  // onLayout on the NOW divider — exact Y for scroll-to-now
  const onNowDividerLayout = useCallback((e: LayoutChangeEvent) => {
    const y = e.nativeEvent.layout.y;
    nowYRef.current = y;
    if (!hasAutoScrolled.current) {
      hasAutoScrolled.current = true;
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

  // Register scroll/today callbacks so tab press triggers them
  useEffect(() => {
    registerScrollToNow(scrollToNow);
    registerGoToday(goToday);
    return () => { unregisterScrollToNow(); unregisterGoToday(); };
  }, [scrollToNow, goToday]);

  // Open ShulDaySheet when a notification is tapped
  useEffect(() => {
    registerOpenSheet((target: SheetTarget) => {
      setSelectedDate(target.date);
      setSheetEvent({
        id: target.eventId,
        date: target.date,
        startTime: '',
        minyanType: '',
        minyanTypeDisplay: '',
        locationName: null,
        notes: null,
        nusach: null,
        nusachDisplay: null,
        dynamicTimeString: null,
        source: 'RULES',
        whatsapp: null,
        organization: {
          id: target.orgSlug,
          slug: target.orgSlug,
          name: target.orgName,
          color: colors.tint,
          whatsapp: null,
        },
      });
    });
    return () => unregisterOpenSheet();
  }, []);

  // Show FAB only on today, when scrolled >300px from the NOW divider
  const showJump = isToday && !isLoading && nowYRef.current >= 0 &&
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
  const hasEvents = listItems.some((i) => i._type === 'event');

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]} edges={['top']}>

      {/* ── Header ── */}
      <View style={[styles.header, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
        <Text style={[styles.siteName, { color: colors.tint }]}>Teaneck Minyanim</Text>
        <View style={styles.headerRow}>
          <Text style={[styles.headerDate, { color: colors.text }]}>
            Today · {format(parseISO(today), 'MMM d')}
          </Text>
          {hebrewDate ? (
            <Text style={[styles.hebrewDate, { color: colors.textSecondary }]} numberOfLines={1}>
              {hebrewDate}
            </Text>
          ) : null}
        </View>
      </View>

      {/* ── Date navigator ── */}
      <View style={[styles.dateNav, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
        <TouchableOpacity onPress={prevDay} style={styles.navBtn} hitSlop={8}>
          <Text style={[styles.navArrow, { color: colors.tint }]}>‹</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.navCenter} onPress={isToday ? undefined : goToday}>
          <Text style={[styles.navDate, { color: colors.text }]}>
            {format(parsedDate, 'EEEE, MMMM d')}
          </Text>
          {!isToday && (
            <Text style={[styles.todayHint, { color: colors.tint }]}>↩ Back to today</Text>
          )}
        </TouchableOpacity>
        <TouchableOpacity onPress={nextDay} style={styles.navBtn} hitSlop={8}>
          <Text style={[styles.navArrow, { color: colors.tint }]}>›</Text>
        </TouchableOpacity>
      </View>

      {/* ── Filter chips ── */}
      <View style={[styles.filterBar, { borderBottomColor: colors.border }]}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterChips}>
          <TouchableOpacity
            style={[
              styles.chip,
              orgFilter
                ? { backgroundColor: colors.tint }
                : { backgroundColor: colors.card, borderColor: colors.border, borderWidth: 1 },
            ]}
            onPress={() => setOrgPickerVisible(true)}>
            <Text style={[styles.chipText, { color: orgFilter ? '#fff' : colors.textSecondary }]}>
              {selectedOrg ? `Shul: ${selectedOrg.name}` : 'Shuls'} ▾
            </Text>
          </TouchableOpacity>

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
        </ScrollView>
      </View>

      {/* ── Content (swipeable) ── */}
      {/* Outer Animated.View: real-time finger tracking. Inner Reanimated.View: arrow-button slide. */}
      <Animated.View style={{ flex: 1, transform: [{ translateX: dragX }] }} {...swipe.panHandlers}>
      <Reanimated.View style={[{ flex: 1 }, animatedContentStyle]}>
        {isLoading && !hasEvents ? (
          <View style={styles.center}>
            <ActivityIndicator color={colors.tint} size="large" />
          </View>
        ) : isError ? (
          <ErrorState message="Could not load schedule." onRetry={refetch} />
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
                No minyanim scheduled{isToday ? ' today' : ' this day'}.
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
                const filterLabel = typeFilter === 'ALL'
                  ? 'minyanim'
                  : (TYPE_FILTERS.find((f) => f.key === typeFilter)?.label ?? 'minyanim');
                return (
                  <View key={item.key} style={styles.noMoreBox}>
                    <Text style={[styles.noMoreText, { color: colors.textTertiary }]}>
                      No more {filterLabel} today
                    </Text>
                  </View>
                );
              }

              const { event } = item;
              const delay = Math.min(index * 20, 300);
              return (
                <Reanimated.View key={item.key} entering={cardEnterAnimation.current ? FadeInDown.delay(delay).duration(320) : undefined}>
                  <MinyanCard
                    event={event}
                    showOrg
                    isNext={false}
                    onPress={() => setSheetEvent(event)}
                  />
                </Reanimated.View>
              );
            })}
          </ScrollView>
        )}
      </Reanimated.View>
      </Animated.View>

      {/* ── Jump to Now FAB (today only) ── */}
      <Animated.View
        style={[styles.jumpBtn, { opacity: jumpBtnOpacity, bottom: insets.bottom + 58 }]}
        pointerEvents={showJump ? 'auto' : 'none'}>
        <TouchableOpacity
          style={[styles.jumpBtnInner, { backgroundColor: colors.tint }]}
          onPress={scrollToNow}>
          <Text style={styles.jumpBtnText}>Next Minyan</Text>
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

      {/* ── Shul day sheet (tap a card to see that org's full day schedule) ── */}
      <ShulDaySheet
        event={sheetEvent}
        date={selectedDate}
        onClose={() => setSheetEvent(null)}
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
  const dismissPan = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onPanResponderRelease: (_, gs) => {
        if (gs.dy > 60 || gs.vy > 0.5) onClose();
      },
    }),
  ).current;

  return (
    <Modal visible={visible} animationType="slide" transparent presentationStyle="pageSheet">
      <View style={styles.overlay}>
        <View style={[styles.sheet, { backgroundColor: colors.card }]}>
          <View style={styles.handleArea} {...dismissPan.panHandlers}>
            <View style={[styles.handle, { backgroundColor: colors.border }]} />
          </View>
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
    paddingBottom: 12,
    borderBottomWidth: 1,
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.06, shadowRadius: 4 },
      android: { elevation: 2 },
    }),
  },
  siteName: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    gap: 8,
  },
  headerDate: { fontSize: 20, fontWeight: '800', letterSpacing: -0.4, flex: 1 },
  hebrewDate: { fontSize: 17, fontWeight: '600', letterSpacing: -0.2, textAlign: 'right' },

  dateNav: {
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    paddingVertical: 8,
  },
  navBtn: { paddingHorizontal: 16, minWidth: 50, alignItems: 'center' },
  navArrow: { fontSize: 30, fontWeight: '300', lineHeight: 34 },
  navCenter: { flex: 1, alignItems: 'center' },
  navDate: { fontSize: 15, fontWeight: '700' },
  todayHint: { fontSize: 11, marginTop: 2, fontWeight: '500' },

  filterBar: { paddingVertical: 10, borderBottomWidth: 1 },
  filterChips: { paddingHorizontal: 16, gap: 8 },
  chip: { borderRadius: 20, paddingHorizontal: 15, paddingVertical: 7 },
  chipText: { fontSize: 13, fontWeight: '600' },

  list: { paddingTop: 10, paddingBottom: 140 },

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
  handleArea: { alignSelf: 'stretch', alignItems: 'center', paddingTop: 0, paddingBottom: 14 },
  handle: { width: 36, height: 4, borderRadius: 2 },
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
