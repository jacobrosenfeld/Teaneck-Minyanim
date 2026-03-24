import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Animated,
  ActivityIndicator,
  Dimensions,
  Linking,
  PanResponder,
  Platform,
  RefreshControl,
  ScrollView,
  SectionList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Reanimated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  runOnJS,
} from 'react-native-reanimated';
import { useLocalSearchParams, Stack } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { SymbolView } from 'expo-symbols';
import { addDays, subDays, addWeeks, subWeeks, format, parseISO, startOfWeek, endOfWeek } from 'date-fns';

import { capture } from '@/analytics';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import MinyanCard from '@/components/MinyanCard';
import ErrorState from '@/components/ErrorState';
import { useOrganization, useOrgSchedule } from '@/api/hooks';
import { toApiDate } from '@/api/client';
import type { ScheduleEvent } from '@/api/types';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

const TYPE_ORDER = ['SHACHARIS', 'MINCHA', 'MINCHA_MAARIV', 'MAARIV', 'SELICHOS', 'MEGILA_READING'];
function sortKey(t: string) { return TYPE_ORDER.indexOf(t) === -1 ? 99 : TYPE_ORDER.indexOf(t); }

interface Section { title: string; date: string; data: ScheduleEvent[] }

function buildSections(events: ScheduleEvent[]): Section[] {
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

function openDirections(address: string, properties: Record<string, unknown>) {
  capture('directions_tap', {
    source: 'shul_detail',
    ...properties,
  });
  const encoded = encodeURIComponent(address);
  const url = Platform.select({
    ios: `maps://?q=${encoded}`,
    android: `geo:0,0?q=${encoded}`,
    default: `https://maps.google.com/maps?q=${encoded}`,
  });
  Linking.openURL(url!);
}

export default function ShulDetailScreen() {
  const { id, selectedEventId } = useLocalSearchParams<{
    id: string;
    selectedEventId?: string;
  }>();
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  const [activeTab, setActiveTab] = useState<0 | 1>(0);
  const [selectedDate, setSelectedDate] = useState(toApiDate(new Date()));
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date(), { weekStartsOn: 0 }));
  const tabScrollRef = useRef<ScrollView>(null);

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

  const dayDragX = useRef(new Animated.Value(0)).current;
  const weekDragX = useRef(new Animated.Value(0)).current;
  const daySlideInXRef = useRef<number | null>(null);
  const weekSlideInXRef = useRef<number | null>(null);

  const today = toApiDate(new Date());
  const parsedDate = parseISO(selectedDate);
  const isToday = selectedDate === today;
  const weekEnd = endOfWeek(weekStart, { weekStartsOn: 0 });
  const isThisWeek = toApiDate(weekStart) === toApiDate(startOfWeek(new Date(), { weekStartsOn: 0 }));

  const selectedDateRef = useRef(selectedDate);
  selectedDateRef.current = selectedDate;
  const weekStartRef = useRef(weekStart);
  weekStartRef.current = weekStart;

  const prevDay = () => animateTransition(-1, () => setSelectedDate(toApiDate(subDays(parseISO(selectedDateRef.current), 1))));
  const nextDay = () => animateTransition(1, () => setSelectedDate(toApiDate(addDays(parseISO(selectedDateRef.current), 1))));
  const goToday = () => animateTransition(1, () => setSelectedDate(today));

  const prevWeek = () => animateTransition(-1, () => setWeekStart((w) => subWeeks(w, 1)));
  const nextWeek = () => animateTransition(1, () => setWeekStart((w) => addWeeks(w, 1)));
  const goThisWeek = () => animateTransition(1, () => setWeekStart(startOfWeek(new Date(), { weekStartsOn: 0 })));

  const switchTab = (tab: 0 | 1) => {
    setActiveTab(tab);
    tabScrollRef.current?.scrollTo({ x: tab * SCREEN_WIDTH, animated: true });
  };

  const gestureNextDay = useCallback(() => {
    setSelectedDate(toApiDate(addDays(parseISO(selectedDateRef.current), 1)));
  }, []);
  const gesturePrevDay = useCallback(() => {
    setSelectedDate(toApiDate(subDays(parseISO(selectedDateRef.current), 1)));
  }, []);
  const gestureNextWeek = useCallback(() => { setWeekStart((w) => addWeeks(w, 1)); }, []);
  const gesturePrevWeek = useCallback(() => { setWeekStart((w) => subWeeks(w, 1)); }, []);

  const gestureNextDayRef = useRef(gestureNextDay); gestureNextDayRef.current = gestureNextDay;
  const gesturePrevDayRef = useRef(gesturePrevDay); gesturePrevDayRef.current = gesturePrevDay;
  const gestureNextWeekRef = useRef(gestureNextWeek); gestureNextWeekRef.current = gestureNextWeek;
  const gesturePrevWeekRef = useRef(gesturePrevWeek); gesturePrevWeekRef.current = gesturePrevWeek;

  const completeSwipe = useCallback((
    goNext: boolean,
    anim: Animated.Value,
    onNext: () => void,
    onPrev: () => void,
    slideInXRef?: React.MutableRefObject<number | null>,
  ) => {
    const outX = goNext ? -SCREEN_WIDTH : SCREEN_WIDTH;
    const inX  = goNext ?  SCREEN_WIDTH * 0.25 : -SCREEN_WIDTH * 0.25;
    Animated.timing(anim, { toValue: outX, duration: 120, useNativeDriver: false }).start(() => {
      if (slideInXRef) {
        slideInXRef.current = inX;
        if (goNext) onNext(); else onPrev();
        anim.setValue(outX);
      } else {
        if (goNext) onNext(); else onPrev();
        anim.setValue(inX);
        Animated.spring(anim, { toValue: 0, useNativeDriver: false, stiffness: 260, damping: 28 }).start();
      }
    });
  }, []);

  const bounceBack = useCallback((anim: Animated.Value) => {
    Animated.spring(anim, { toValue: 0, useNativeDriver: false, stiffness: 300, damping: 30 }).start();
  }, []);

  const daySwipe = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (evt, gs) =>
        evt.nativeEvent.pageX > 60 &&
        Math.abs(gs.dx) > 10 &&
        Math.abs(gs.dx) > Math.abs(gs.dy) * 1.2,
      onPanResponderMove: (_, gs) => { dayDragX.setValue(gs.dx); },
      onPanResponderRelease: (_, gs) => {
        if (Math.abs(gs.dx) > 50 || Math.abs(gs.vx) > 0.3) {
          completeSwipe(gs.dx < 0, dayDragX, gestureNextDayRef.current, gesturePrevDayRef.current, daySlideInXRef);
        } else {
          bounceBack(dayDragX);
        }
      },
      onPanResponderTerminate: () => { bounceBack(dayDragX); },
    }),
  ).current;

  const weekSwipe = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (evt, gs) =>
        evt.nativeEvent.pageX > 60 &&
        Math.abs(gs.dx) > 10 &&
        Math.abs(gs.dx) > Math.abs(gs.dy) * 1.2,
      onPanResponderMove: (_, gs) => { weekDragX.setValue(gs.dx); },
      onPanResponderRelease: (_, gs) => {
        if (Math.abs(gs.dx) > 50 || Math.abs(gs.vx) > 0.3) {
          completeSwipe(gs.dx < 0, weekDragX, gestureNextWeekRef.current, gesturePrevWeekRef.current, weekSlideInXRef);
        } else {
          bounceBack(weekDragX);
        }
      },
      onPanResponderTerminate: () => { bounceBack(weekDragX); },
    }),
  ).current;

  const { data: org } = useOrganization(id ?? '');

  const {
    data: dailyEvents,
    isLoading: dailyLoading,
    isError: dailyError,
    refetch: refetchDaily,
    isFetching: dailyFetching,
  } = useOrgSchedule(id ?? '', { start: selectedDate, end: selectedDate });

  const {
    data: weeklyEvents,
    isLoading: weeklyLoading,
    isError: weeklyError,
    refetch: refetchWeekly,
    isFetching: weeklyFetching,
  } = useOrgSchedule(id ?? '', {
    start: toApiDate(weekStart),
    end: toApiDate(weekEnd),
  });

  const p1 = toApiDate(subDays(parseISO(selectedDate), 1));
  const p2 = toApiDate(subDays(parseISO(selectedDate), 2));
  const n1 = toApiDate(addDays(parseISO(selectedDate), 1));
  const n2 = toApiDate(addDays(parseISO(selectedDate), 2));
  useOrgSchedule(id ?? '', { start: p1, end: p1 });
  useOrgSchedule(id ?? '', { start: p2, end: p2 });
  useOrgSchedule(id ?? '', { start: n1, end: n1 });
  useOrgSchedule(id ?? '', { start: n2, end: n2 });

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (daySlideInXRef.current !== null) {
      const inX = daySlideInXRef.current;
      daySlideInXRef.current = null;
      dayDragX.setValue(inX);
      Animated.spring(dayDragX, { toValue: 0, useNativeDriver: false, stiffness: 260, damping: 28 }).start();
    }
  }, [selectedDate]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (weekSlideInXRef.current !== null) {
      const inX = weekSlideInXRef.current;
      weekSlideInXRef.current = null;
      weekDragX.setValue(inX);
      Animated.spring(weekDragX, { toValue: 0, useNativeDriver: false, stiffness: 260, damping: 28 }).start();
    }
  }, [weekStart]);

  const weeklySections = buildSections(weeklyEvents ?? []);
  const orgColor = org?.color ?? colors.tint;
  const orgSlug = org?.slug ?? org?.id ?? id ?? '';
  const dailySorted = [...(dailyEvents ?? [])].sort((a, b) => {
    const td = sortKey(a.minyanType) - sortKey(b.minyanType);
    return td !== 0 ? td : a.startTime.localeCompare(b.startTime);
  });

  const openWebsite = async () => {
    if (!org?.websiteUrl) return;
    const url = ensureHttps(org.websiteUrl);
    await WebBrowser.openBrowserAsync(url, {
      presentationStyle: WebBrowser.WebBrowserPresentationStyle.FORM_SHEET,
    });
  };

  const openWhatsApp = () => {
    if (!org?.whatsapp) return;
    capture('whatsapp_tap', {
      source: 'shul_detail',
      org_slug: orgSlug,
    });
    Linking.openURL(org.whatsapp);
  };

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: '',
          headerBackTitle: 'Shuls',
          headerStyle: { backgroundColor: colors.card },
          headerTintColor: orgColor,
          headerShadowVisible: false,
          // Disable iOS swipe-back — horizontal swipe is used for day navigation
          gestureEnabled: false,
        }}
      />

      <View style={[styles.root, { backgroundColor: colors.background }]}>

        {/* ── Org header card ── */}
        <View style={[styles.orgCard, { backgroundColor: colors.card, borderBottomColor: colors.border, borderLeftColor: orgColor }]}>
          <View style={styles.orgCardTop}>
            <View style={styles.orgCardInfo}>
              <Text style={[styles.siteNameSmall, { color: colors.tint }]}>Teaneck Minyanim</Text>
              <Text style={[styles.orgName, { color: colors.text }]}>{org?.name ?? ''}</Text>
            </View>
            {(org?.websiteUrl || org?.whatsapp) ? (
              <View style={styles.orgActions}>
                {org?.websiteUrl ? (
                  <TouchableOpacity
                    style={[styles.actionBtn, { backgroundColor: colors.background, borderColor: colors.border }]}
                    onPress={openWebsite}>
                    <Text style={[styles.actionLabel, { color: colors.textSecondary }]}>Website</Text>
                  </TouchableOpacity>
                ) : null}
                {org?.whatsapp ? (
                  <TouchableOpacity
                    style={[styles.actionBtn, { backgroundColor: '#E8FAF0', borderColor: '#25D366' }]}
                    onPress={openWhatsApp}>
                    <SymbolView name="message.fill" tintColor="#1A9E4A" size={16} />
                    <Text style={[styles.actionLabel, { color: '#1A9E4A' }]}>WhatsApp</Text>
                  </TouchableOpacity>
                ) : null}
              </View>
            ) : null}
          </View>

          {org?.nusachDisplay ? (
            <View style={styles.orgMeta}>
              <View style={[styles.chip, { backgroundColor: colors.background, borderColor: colors.border }]}>
                <Text style={[styles.chipText, { color: colors.textSecondary }]}>{org.nusachDisplay}</Text>
              </View>
            </View>
          ) : null}

          {org?.address ? (
            <View style={styles.addressRow}>
              <TouchableOpacity
                style={styles.addressTextWrap}
                onPress={() =>
                  openDirections(org.address!, {
                    org_slug: orgSlug,
                  })
                }>
                <SymbolView name="location.fill" tintColor={colors.textSecondary} size={12} />
                <Text style={[styles.orgAddress, { color: colors.textSecondary }]} numberOfLines={1}>
                  {org.address}
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.dirBtn, { backgroundColor: colors.tint }]}
                onPress={() =>
                  openDirections(org.address!, {
                    org_slug: orgSlug,
                  })
                }>
                <Text style={styles.dirBtnText}>Directions</Text>
              </TouchableOpacity>
            </View>
          ) : null}
        </View>

        {/* ── Daily / Weekly tab toggle ── */}
        <View style={[styles.tabBar, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
          <View style={[styles.tabTrack, { backgroundColor: colors.background }]}>
            {(['Daily', 'Weekly'] as const).map((label, i) => (
              <TouchableOpacity
                key={label}
                style={[
                  styles.tabBtn,
                  activeTab === i && { backgroundColor: colors.card, shadowColor: colors.shadow },
                ]}
                onPress={() => switchTab(i as 0 | 1)}>
                <Text style={[
                  styles.tabText,
                  { color: activeTab === i ? colors.text : colors.textSecondary },
                  activeTab === i && { fontWeight: '700' },
                ]}>
                  {label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* ── Sliding tab content ── */}
        <ScrollView
          ref={tabScrollRef}
          horizontal
          pagingEnabled
          scrollEnabled={false}
          showsHorizontalScrollIndicator={false}
          style={{ flex: 1 }}>

          {/* ── Daily tab ── */}
          <View style={{ width: SCREEN_WIDTH, flex: 1 }}>
            <View style={[styles.dayNav, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
              <TouchableOpacity onPress={prevDay} style={styles.navBtn} hitSlop={8}>
                <Text style={[styles.navArrow, { color: colors.tint }]}>‹</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.navCenter} onPress={isToday ? undefined : goToday}>
                <Text style={[styles.navDate, { color: colors.text }]}>
                  {format(parsedDate, 'EEEE, MMMM d')}
                </Text>
                {!isToday && (
                  <Text style={[styles.todayHint, { color: colors.tint }]}>↩ Today</Text>
                )}
              </TouchableOpacity>
              <TouchableOpacity onPress={nextDay} style={styles.navBtn} hitSlop={8}>
                <Text style={[styles.navArrow, { color: colors.tint }]}>›</Text>
              </TouchableOpacity>
            </View>

            <Animated.View
              style={{ flex: 1, transform: [{ translateX: dayDragX }] }}
              {...daySwipe.panHandlers}>
              <Reanimated.View style={[{ flex: 1 }, animatedContentStyle]}>
                {dailyLoading ? (
                  <View style={styles.center}>
                    <ActivityIndicator color={orgColor} size="large" />
                  </View>
                ) : dailyError ? (
                  <ErrorState message="Could not load schedule." onRetry={refetchDaily} />
                ) : dailySorted.length === 0 ? (
                  <View style={styles.center}>
                    <Text style={[styles.emptyText, { color: colors.textSecondary }]}>
                      No minyanim scheduled{isToday ? ' today' : ' this day'}.
                    </Text>
                    {isToday && (
                      <TouchableOpacity onPress={() => switchTab(1)} style={{ marginTop: 12 }}>
                        <Text style={[styles.switchHint, { color: colors.tint }]}>View this week →</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                ) : (
                  <ScrollView
                    contentContainerStyle={styles.list}
                    refreshControl={
                      <RefreshControl
                        refreshing={dailyFetching && !dailyLoading}
                        onRefresh={() => {
                          capture('pull_to_refresh', {
                            screen: 'shul_detail_daily',
                            org_slug: orgSlug,
                          });
                          refetchDaily();
                        }}
                        tintColor={orgColor}
                      />
                    }>
                    {dailySorted.map((item) => (
                      <MinyanCard
                        key={item.id}
                        event={item}
                        showOrg={false}
                        isHighlighted={item.id === selectedEventId}
                      />
                    ))}
                  </ScrollView>
                )}
              </Reanimated.View>
            </Animated.View>
          </View>

          {/* ── Weekly tab ── */}
          <View style={{ width: SCREEN_WIDTH, flex: 1 }}>
            <View style={[styles.dayNav, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
              <TouchableOpacity onPress={prevWeek} style={styles.navBtn} hitSlop={8}>
                <Text style={[styles.navArrow, { color: colors.tint }]}>‹</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.navCenter} onPress={isThisWeek ? undefined : goThisWeek}>
                <Text style={[styles.navDate, { color: colors.text }]}>
                  {format(weekStart, 'MMM d')} – {format(weekEnd, 'MMM d')}
                </Text>
                {!isThisWeek && (
                  <Text style={[styles.todayHint, { color: colors.tint }]}>↩ This Week</Text>
                )}
              </TouchableOpacity>
              <TouchableOpacity onPress={nextWeek} style={styles.navBtn} hitSlop={8}>
                <Text style={[styles.navArrow, { color: colors.tint }]}>›</Text>
              </TouchableOpacity>
            </View>

            <Animated.View
              style={{ flex: 1, transform: [{ translateX: weekDragX }] }}
              {...weekSwipe.panHandlers}>
              <Reanimated.View style={[{ flex: 1 }, animatedContentStyle]}>
                {weeklyLoading ? (
                  <View style={styles.center}>
                    <ActivityIndicator color={orgColor} size="large" />
                  </View>
                ) : weeklyError ? (
                  <ErrorState message="Could not load weekly schedule." onRetry={refetchWeekly} />
                ) : weeklySections.length === 0 ? (
                  <View style={styles.center}>
                    <Text style={styles.emptyIcon}>📅</Text>
                    <Text style={[styles.emptyText, { color: colors.textSecondary }]}>
                      No minyanim this week.
                    </Text>
                  </View>
                ) : (
                  <SectionList
                    sections={weeklySections}
                    keyExtractor={(item) => item.id}
                    renderItem={({ item }) => (
                      <MinyanCard
                        event={item}
                        showOrg={false}
                        isHighlighted={item.id === selectedEventId}
                      />
                    )}
                    renderSectionHeader={({ section }) => (
                      <View style={[styles.sectionHeader, { backgroundColor: colors.background }]}>
                        <View style={[styles.sectionLine, { backgroundColor: colors.border }]} />
                        <Text style={[styles.sectionTitle, { color: colors.textSecondary }]}>
                          {section.title}
                        </Text>
                        <View style={[styles.sectionLine, { backgroundColor: colors.border }]} />
                      </View>
                    )}
                    contentContainerStyle={styles.list}
                    stickySectionHeadersEnabled={false}
                    refreshControl={
                      <RefreshControl
                        refreshing={weeklyFetching && !weeklyLoading}
                        onRefresh={() => {
                          capture('pull_to_refresh', {
                            screen: 'shul_detail_weekly',
                            org_slug: orgSlug,
                          });
                          refetchWeekly();
                        }}
                        tintColor={orgColor}
                      />
                    }
                  />
                )}
              </Reanimated.View>
            </Animated.View>
          </View>
        </ScrollView>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },

  orgCard: {
    borderBottomWidth: 1,
    borderLeftWidth: 4,
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 12,
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 3 },
      android: { elevation: 1 },
    }),
  },
  orgCardTop: { flexDirection: 'row', alignItems: 'flex-start' },
  orgCardInfo: { flex: 1, marginRight: 12 },
  siteNameSmall: { fontSize: 9, fontWeight: '800', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 2 },
  orgName: { fontSize: 20, fontWeight: '800', letterSpacing: -0.3 },
  orgMeta: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginTop: 6 },
  chip: { borderRadius: 6, borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3 },
  chipText: { fontSize: 11, fontWeight: '600' },

  addressRow: { flexDirection: 'row', alignItems: 'center', marginTop: 8, gap: 8 },
  addressTextWrap: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 5 },
  orgAddress: { fontSize: 13, fontWeight: '500', flex: 1 },
  dirBtn: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 5, flexShrink: 0 },
  dirBtnText: { fontSize: 12, fontWeight: '700', color: '#fff' },

  orgActions: { gap: 6, alignItems: 'flex-end', flexShrink: 0 },
  actionBtn: {
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 5,
    alignItems: 'center',
    minWidth: 72,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 4,
  },
  actionLabel: { fontSize: 11, fontWeight: '600' },

  tabBar: { paddingHorizontal: 16, paddingVertical: 10, borderBottomWidth: 1 },
  tabTrack: { flexDirection: 'row', borderRadius: 10, padding: 3 },
  tabBtn: {
    flex: 1,
    paddingVertical: 7,
    alignItems: 'center',
    borderRadius: 8,
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.08, shadowRadius: 3 },
      android: { elevation: 1 },
    }),
  },
  tabText: { fontSize: 14 },

  dayNav: { flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, paddingVertical: 10 },
  navBtn: { paddingHorizontal: 16, minWidth: 50, alignItems: 'center' },
  navArrow: { fontSize: 30, fontWeight: '300', lineHeight: 34 },
  navCenter: { flex: 1, alignItems: 'center' },
  navDate: { fontSize: 15, fontWeight: '700' },
  todayHint: { fontSize: 11, marginTop: 2, fontWeight: '500' },

  list: { paddingTop: 8, paddingBottom: 100 },
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
