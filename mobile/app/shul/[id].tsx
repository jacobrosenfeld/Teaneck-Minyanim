import React, { useCallback, useRef, useState } from 'react';
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
import { BlurView } from 'expo-blur';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useLocalSearchParams, Stack, router } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { SymbolView } from 'expo-symbols';
import { addDays, subDays, addWeeks, subWeeks, format, parseISO, startOfWeek, endOfWeek } from 'date-fns';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import MinyanCard from '@/components/MinyanCard';
import ErrorState from '@/components/ErrorState';
import { useOrganization, useOrgSchedule } from '@/api/hooks';
import { toApiDate } from '@/api/client';
import type { ScheduleEvent } from '@/api/types';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

const TAB_BAR_HEIGHT = 49;

const BOTTOM_TABS = [
  { key: 'minyanim', label: 'Minyanim', icon: 'calendar',        iconFocused: 'calendar',           path: '/(tabs)/'      },
  { key: 'shuls',    label: 'Shuls',    icon: 'building.2',       iconFocused: 'building.2.fill',    path: '/(tabs)/shuls' },
  { key: 'map',      label: 'Map',      icon: 'map',              iconFocused: 'map.fill',            path: '/(tabs)/map'   },
  { key: 'zmanim',   label: 'Zmanim',   icon: 'clock',            iconFocused: 'clock.fill',         path: '/(tabs)/zmanim'},
] as const;

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

function openDirections(address: string) {
  const encoded = encodeURIComponent(address);
  const url = Platform.select({
    ios: `maps://?q=${encoded}`,
    android: `geo:0,0?q=${encoded}`,
    default: `https://maps.google.com/maps?q=${encoded}`,
  });
  Linking.openURL(url!);
}

// ── Custom bottom tab bar ──────────────────────────────────────────────────────

function CustomTabBar({
  activeTab,
  colors,
  scheme,
}: {
  activeTab: string | undefined;
  colors: typeof Colors.light;
  scheme: 'light' | 'dark';
}) {
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.customTabBarWrap, { height: TAB_BAR_HEIGHT + insets.bottom }]}>
      {Platform.OS === 'ios' ? (
        <BlurView
          intensity={90}
          tint={scheme === 'dark' ? 'systemChromeMaterialDark' : 'systemChromeMaterial'}
          style={StyleSheet.absoluteFill}
        />
      ) : (
        <View style={[StyleSheet.absoluteFill, { backgroundColor: colors.card }]} />
      )}
      <View
        style={[
          styles.customTabBarBorder,
          {
            borderTopColor:
              scheme === 'dark' ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)',
          },
        ]}
      />
      <View style={[styles.customTabBarRow, { paddingBottom: 0, height: TAB_BAR_HEIGHT }]}>
        {BOTTOM_TABS.map((tab) => {
          const isActive = tab.key === activeTab;
          const iconColor = isActive ? colors.tint : colors.tabIconDefault;
          return (
            <TouchableOpacity
              key={tab.key}
              style={styles.customTabItem}
              onPress={() => router.navigate(tab.path as never)}>
              <SymbolView
                name={isActive ? tab.iconFocused : tab.icon}
                tintColor={iconColor}
                size={22}
              />
              <Text style={[styles.customTabLabel, { color: iconColor }]}>{tab.label}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );
}

// ── Main screen ───────────────────────────────────────────────────────────────

export default function ShulDetailScreen() {
  const { id, selectedEventId, sourceTab } = useLocalSearchParams<{
    id: string;
    selectedEventId?: string;
    sourceTab?: string;
  }>();
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  const [activeTab, setActiveTab] = useState<0 | 1>(0);
  const [selectedDate, setSelectedDate] = useState(toApiDate(new Date()));
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date(), { weekStartsOn: 0 }));
  const tabScrollRef = useRef<ScrollView>(null);

  // ── Reanimated slide: used only for arrow-button navigation ───────────────
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

  // ── Animated.Value: used for real-time gesture tracking ───────────────────
  const dayDragX = useRef(new Animated.Value(0)).current;
  const weekDragX = useRef(new Animated.Value(0)).current;

  const today = toApiDate(new Date());
  const parsedDate = parseISO(selectedDate);
  const isToday = selectedDate === today;
  const weekEnd = endOfWeek(weekStart, { weekStartsOn: 0 });
  const isThisWeek = toApiDate(weekStart) === toApiDate(startOfWeek(new Date(), { weekStartsOn: 0 }));

  // Refs so gesture callbacks always see the latest state
  const selectedDateRef = useRef(selectedDate);
  selectedDateRef.current = selectedDate;
  const weekStartRef = useRef(weekStart);
  weekStartRef.current = weekStart;

  // Arrow navigation — uses Reanimated slide animation
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

  // Gesture-only day/week changers (no Reanimated animation — gesture handles visuals)
  const gestureNextDay = useCallback(() => {
    setSelectedDate(toApiDate(addDays(parseISO(selectedDateRef.current), 1)));
  }, []);
  const gesturePrevDay = useCallback(() => {
    setSelectedDate(toApiDate(subDays(parseISO(selectedDateRef.current), 1)));
  }, []);
  const gestureNextWeek = useCallback(() => { setWeekStart((w) => addWeeks(w, 1)); }, []);
  const gesturePrevWeek = useCallback(() => { setWeekStart((w) => subWeeks(w, 1)); }, []);

  // Refs so PanResponder closures always call the latest
  const gestureNextDayRef = useRef(gestureNextDay); gestureNextDayRef.current = gestureNextDay;
  const gesturePrevDayRef = useRef(gesturePrevDay); gesturePrevDayRef.current = gesturePrevDay;
  const gestureNextWeekRef = useRef(gestureNextWeek); gestureNextWeekRef.current = gestureNextWeek;
  const gesturePrevWeekRef = useRef(gesturePrevWeek); gesturePrevWeekRef.current = gesturePrevWeek;

  // ── Smooth gesture completion helper ─────────────────────────────────────
  const completeSwipe = useCallback((
    goNext: boolean,
    anim: Animated.Value,
    onNext: () => void,
    onPrev: () => void,
  ) => {
    const outX = goNext ? -SCREEN_WIDTH : SCREEN_WIDTH;
    const inX  = goNext ?  SCREEN_WIDTH * 0.25 : -SCREEN_WIDTH * 0.25;
    Animated.timing(anim, { toValue: outX, duration: 120, useNativeDriver: false }).start(() => {
      if (goNext) onNext(); else onPrev();
      anim.setValue(inX);
      Animated.spring(anim, { toValue: 0, useNativeDriver: false, stiffness: 260, damping: 28 }).start();
    });
  }, []);

  const bounceBack = useCallback((anim: Animated.Value) => {
    Animated.spring(anim, { toValue: 0, useNativeDriver: false, stiffness: 300, damping: 30 }).start();
  }, []);

  // ── PanResponders ─────────────────────────────────────────────────────────
  const daySwipe = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (evt, gs) =>
        evt.nativeEvent.pageX > 60 &&
        Math.abs(gs.dx) > 10 &&
        Math.abs(gs.dx) > Math.abs(gs.dy) * 1.2,
      onPanResponderMove: (_, gs) => { dayDragX.setValue(gs.dx); },
      onPanResponderRelease: (_, gs) => {
        if (Math.abs(gs.dx) > 50 || Math.abs(gs.vx) > 0.3) {
          completeSwipe(
            gs.dx < 0, dayDragX,
            gestureNextDayRef.current, gesturePrevDayRef.current,
          );
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
          completeSwipe(
            gs.dx < 0, weekDragX,
            gestureNextWeekRef.current, gesturePrevWeekRef.current,
          );
        } else {
          bounceBack(weekDragX);
        }
      },
      onPanResponderTerminate: () => { bounceBack(weekDragX); },
    }),
  ).current;

  // ── Data fetching ─────────────────────────────────────────────────────────
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

  // ── Prefetch adjacent days to eliminate swipe stutter ────────────────────
  const prevDate = toApiDate(subDays(parseISO(selectedDate), 1));
  const nextDate = toApiDate(addDays(parseISO(selectedDate), 1));
  useOrgSchedule(id ?? '', { start: prevDate, end: prevDate });
  useOrgSchedule(id ?? '', { start: nextDate, end: nextDate });

  // ── Derived data ──────────────────────────────────────────────────────────
  const weeklySections = buildSections(weeklyEvents ?? []);
  const orgColor = org?.color ?? colors.tint;
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
    const num = org.whatsapp.replace(/\D/g, '');
    WebBrowser.openBrowserAsync(`https://wa.me/${num}`);
  };

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: '',
          headerBackTitle: 'Back',
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

          {/* Top row: name + action buttons */}
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

          {/* Nusach chip */}
          {org?.nusachDisplay ? (
            <View style={styles.orgMeta}>
              <View style={[styles.chip, { backgroundColor: colors.background, borderColor: colors.border }]}>
                <Text style={[styles.chipText, { color: colors.textSecondary }]}>{org.nusachDisplay}</Text>
              </View>
            </View>
          ) : null}

          {/* Full-width address row: tapping address opens directions */}
          {org?.address ? (
            <View style={styles.addressRow}>
              <TouchableOpacity
                style={styles.addressTextWrap}
                onPress={() => openDirections(org.address!)}>
                <SymbolView name="location.fill" tintColor={colors.textSecondary} size={12} />
                <Text style={[styles.orgAddress, { color: colors.textSecondary }]} numberOfLines={1}>
                  {org.address}
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.dirBtn, { backgroundColor: colors.tint }]}
                onPress={() => openDirections(org.address!)}>
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

            {/*
              Outer Animated.View: real-time drag tracking (follows finger).
              Inner Reanimated.View: arrow-button slide animation.
            */}
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
                        onRefresh={refetchDaily}
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
                        onRefresh={refetchWeekly}
                        tintColor={orgColor}
                      />
                    }
                  />
                )}
              </Reanimated.View>
            </Animated.View>
          </View>
        </ScrollView>

        {/* ── Custom bottom tab bar ── */}
        <CustomTabBar activeTab={sourceTab} colors={colors} scheme={scheme} />
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },

  // ── Org card (column layout so address row is full-width) ──────────────────
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
  orgCardTop: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  orgCardInfo: { flex: 1, marginRight: 12 },
  siteNameSmall: {
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 2,
  },
  orgName: { fontSize: 20, fontWeight: '800', letterSpacing: -0.3 },
  orgMeta: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginTop: 6 },
  chip: { borderRadius: 6, borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3 },
  chipText: { fontSize: 11, fontWeight: '600' },

  // Address row — spans full card width
  addressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    gap: 8,
  },
  addressTextWrap: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  orgAddress: { fontSize: 13, fontWeight: '500', flex: 1 },
  dirBtn: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 5, flexShrink: 0 },
  dirBtnText: { fontSize: 12, fontWeight: '700', color: '#fff' },

  // Action buttons (website, whatsapp) — stacked column on right of top row
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

  // ── Daily/Weekly tab toggle ────────────────────────────────────────────────
  tabBar: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: 1,
  },
  tabTrack: {
    flexDirection: 'row',
    borderRadius: 10,
    padding: 3,
  },
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

  // ── Day navigator ─────────────────────────────────────────────────────────
  dayNav: {
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    paddingVertical: 10,
  },
  navBtn: { paddingHorizontal: 16, minWidth: 50, alignItems: 'center' },
  navArrow: { fontSize: 30, fontWeight: '300', lineHeight: 34 },
  navCenter: { flex: 1, alignItems: 'center' },
  navDate: { fontSize: 15, fontWeight: '700' },
  todayHint: { fontSize: 11, marginTop: 2, fontWeight: '500' },

  // ── List ──────────────────────────────────────────────────────────────────
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

  // ── Custom bottom tab bar ─────────────────────────────────────────────────
  customTabBarWrap: {
    overflow: 'hidden',
    // height is set dynamically via inline style (TAB_BAR_HEIGHT + insets.bottom)
  },
  customTabBarBorder: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  customTabBarRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  customTabItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
    paddingVertical: 6,
  },
  customTabLabel: {
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 0.2,
  },
});
