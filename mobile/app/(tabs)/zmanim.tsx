import React, { useCallback, useRef } from 'react';
import {
  ActivityIndicator,
  Linking,
  PanResponder,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  runOnJS,
} from 'react-native-reanimated';
import Reanimated from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';
import { format, addDays, subDays, parseISO } from 'date-fns';
import Constants from 'expo-constants';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import ErrorState from '@/components/ErrorState';
import { useZmanim } from '@/api/hooks';
import { toApiDate } from '@/api/client';
import { formatTime } from '@/utils/time';
import type { ZmanimTimes } from '@/api/types';

type ZmanimRow = {
  label: string;
  key: keyof ZmanimTimes;
  highlight?: boolean;
  optional?: boolean;
  order: number;
};

const ZMANIM_SECTIONS: { title: string; rows: ZmanimRow[] }[] = [
  {
    title: 'Morning',
    rows: [
      { label: 'Alos HaShachar', key: 'alotHashachar', order: 10 },
      { label: 'Misheyakir', key: 'misheyakir', order: 20 },
      { label: 'Netz', key: 'netz', highlight: true, order: 30 },
      { label: 'Sof Zman Krias Shema (MGA)', key: 'sofZmanShmaMga', order: 40 },
      { label: 'Sof Zman Krias Shema (GRA)', key: 'sofZmanShmaGra', order: 50 },
      { label: 'Sof Zman Tefilla (MGA)', key: 'sofZmanTfilaMga', order: 60 },
      { label: 'Sof Zman Tefilla (GRA)', key: 'sofZmanTfilaGra', order: 70 },
    ],
  },
  {
    title: 'Afternoon',
    rows: [
      { label: 'Chatzos', key: 'chatzos', highlight: true, order: 10 },
      { label: 'Mincha Gedola', key: 'minchaGedola', order: 20 },
      { label: 'Mincha Ketana', key: 'minchaKetana', order: 30 },
      { label: 'Plag HaMincha', key: 'plagHamincha', highlight: true, order: 40 },
    ],
  },
  {
    title: 'Evening',
    rows: [
      { label: 'Candle Lighting', key: 'candleLighting', optional: true, highlight: true, order: 10 },
      { label: 'Shekiya', key: 'shekiya', highlight: true, order: 20 },
      { label: 'Earliest Shema', key: 'earliestShema', order: 30 },
      { label: 'Tzes HaKochavim', key: 'tzeis', highlight: true, order: 40 },
      { label: 'Havdala', key: 'havdala', optional: true, highlight: true, order: 50 },
      { label: 'Chatzos Laila', key: 'chatzosLaila', order: 60 },
    ],
  },
];

function eveningMinutes(time: string | null | undefined): number | null {
  if (!time) return null;
  const [hour, minute] = time.split(':').map(Number);
  if (Number.isNaN(hour) || Number.isNaN(minute)) return null;
  const minutes = hour * 60 + minute;
  return minutes < 12 * 60 ? minutes + 24 * 60 : minutes;
}

function rowsForSection(
  section: { title: string; rows: ZmanimRow[] },
  times: ZmanimTimes | undefined,
): ZmanimRow[] {
  const rows = section.rows.filter((row) => !row.optional || !!times?.[row.key]);
  if (section.title !== 'Evening') return rows;

  return [...rows].sort((a, b) => {
    const aMinutes = eveningMinutes(times?.[a.key]);
    const bMinutes = eveningMinutes(times?.[b.key]);
    if (aMinutes !== null && bMinutes !== null && aMinutes !== bMinutes) {
      return aMinutes - bMinutes;
    }
    if (aMinutes !== null && bMinutes === null) return -1;
    if (aMinutes === null && bMinutes !== null) return 1;
    return a.order - b.order;
  });
}

const APP_VERSION = Constants.expoConfig?.version ?? '1.0.0';
const SUPPORT_EMAIL = 'info@teaneckminyanim.com';

export default function ZmanimScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  const [selectedDate, setSelectedDate] = React.useState(toApiDate(new Date()));
  const { data: zmanim, isLoading, isError, refetch } = useZmanim(selectedDate);

  const parsedDate = parseISO(selectedDate);
  const isToday = selectedDate === toApiDate(new Date());

  // Slide animation — mirrors shul detail page
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

  const parsedDateRef = useRef(parsedDate);
  parsedDateRef.current = parsedDate;

  const prev = useCallback(() =>
    animateTransition(-1, () => setSelectedDate(toApiDate(subDays(parsedDateRef.current, 1)))),
    [animateTransition]);
  const next = useCallback(() =>
    animateTransition(1, () => setSelectedDate(toApiDate(addDays(parsedDateRef.current, 1)))),
    [animateTransition]);
  const goToday = useCallback(() =>
    animateTransition(1, () => setSelectedDate(toApiDate(new Date()))),
    [animateTransition]);

  const prevRef = useRef(prev); prevRef.current = prev;
  const nextRef = useRef(next); nextRef.current = next;

  const swipe = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (evt, gs) =>
        evt.nativeEvent.pageX > 30 &&
        Math.abs(gs.dx) > 15 && Math.abs(gs.dx) > Math.abs(gs.dy) * 1.5,
      onPanResponderRelease: (_, gs) => {
        if (gs.dx < -50) nextRef.current();
        else if (gs.dx > 50) prevRef.current();
      },
    }),
  ).current;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]} edges={['top']}>
      {/* Header */}
      <View style={[styles.header, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
        <Text style={[styles.siteName, { color: colors.tint }]}>Teaneck Minyanim</Text>
        <Text style={[styles.title, { color: colors.text }]}>Zmanim for Teaneck, NJ</Text>
      </View>

      {/* Date navigator */}
      <View style={[styles.dateNav, { backgroundColor: colors.card, borderBottomColor: colors.border }]}>
        <TouchableOpacity onPress={prev} style={styles.navBtn} hitSlop={8}>
          <Text style={[styles.navArrow, { color: colors.tint }]}>‹</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.navCenter} onPress={isToday ? undefined : goToday}>
          <Text style={[styles.navDate, { color: colors.text }]}>
            {format(parsedDate, 'EEEE, MMMM d')}
          </Text>
          {zmanim?.hebrewDate ? (
            <Text style={[styles.navHebrew, { color: colors.textSecondary }]}>
              {zmanim.hebrewDate}
            </Text>
          ) : null}
          {!isToday && (
            <Text style={[styles.todayHint, { color: colors.tint }]}>↩ Back to today</Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity onPress={next} style={styles.navBtn} hitSlop={8}>
          <Text style={[styles.navArrow, { color: colors.tint }]}>›</Text>
        </TouchableOpacity>
      </View>

      <Reanimated.View style={[{ flex: 1 }, animatedContentStyle]} {...swipe.panHandlers}>
      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator color={colors.tint} size="large" />
        </View>
      ) : isError ? (
        <ErrorState message="Could not load zmanim." onRetry={refetch} />
      ) : (
        <ScrollView contentContainerStyle={styles.list}>
          {/* Zmanim rows */}
          {ZMANIM_SECTIONS.map((section) => {
            const rows = rowsForSection(section, zmanim?.times);
            return (
              <React.Fragment key={section.title}>
                <View style={[styles.sectionHeader, { backgroundColor: colors.background }]}>
                  <View style={[styles.sectionLine, { backgroundColor: colors.border }]} />
                  <Text style={[styles.sectionLabel, { color: colors.textSecondary }]}>
                    {section.title.toUpperCase()}
                  </Text>
                  <View style={[styles.sectionLine, { backgroundColor: colors.border }]} />
                </View>
                {rows.map((row) => {
                  const raw = zmanim?.times?.[row.key];
                  const time = raw ? formatTime(raw) : null;

                  return (
                    <View
                      key={row.key}
                      style={[
                        styles.zmanRow,
                        { backgroundColor: colors.card, borderBottomColor: colors.border },
                      ]}>
                      <Text
                        style={[
                          styles.zmanLabel,
                          { color: colors.text },
                        ]}>
                        {row.label}
                      </Text>
                      <Text
                        style={[
                          styles.zmanTime,
                          { color: time ? colors.text : colors.border },
                        ]}>
                        {time ?? '—'}
                      </Text>
                    </View>
                  );
                })}
              </React.Fragment>
            );
          })}

          {/* Footer */}
          <View style={[styles.footer, { borderTopColor: colors.border }]}>
            <Text style={[styles.footerBrand, { color: colors.tint }]}>Teaneck Minyanim</Text>
            <TouchableOpacity onPress={() => Linking.openURL(`mailto:${SUPPORT_EMAIL}`)}>
              <Text style={[styles.footerEmail, { color: colors.textSecondary }]}>
                {SUPPORT_EMAIL}
              </Text>
            </TouchableOpacity>
            <Text style={[styles.footerVersion, { color: colors.textTertiary }]}>
              v{APP_VERSION}
            </Text>
            <Text style={[styles.footerCopyright, { color: colors.textTertiary }]}>
              © {new Date().getFullYear()} Teaneck Minyanim · TB Dev
            </Text>
          </View>
        </ScrollView>
      )}
      </Reanimated.View>
    </SafeAreaView>
  );
}

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
  siteName: { fontSize: 11, fontWeight: '800', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 2 },
  title: { fontSize: 20, fontWeight: '800', letterSpacing: -0.3 },

  dateNav: {
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    paddingVertical: 10,
  },
  navBtn: { paddingHorizontal: 16, minWidth: 50, alignItems: 'center' },
  navArrow: { fontSize: 30, fontWeight: '300', lineHeight: 34 },
  navCenter: { flex: 1, alignItems: 'center' },
  navDate: { fontSize: 16, fontWeight: '700' },
  navHebrew: { fontSize: 13, marginTop: 1 },
  todayHint: { fontSize: 11, marginTop: 2, fontWeight: '500' },

  list: { paddingBottom: 100 }, // clears tab bar + home indicator (tab clips screen so insets.bottom = 0)

  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 6,
    gap: 10,
  },
  sectionLine: { flex: 1, height: 1 },
  sectionLabel: { fontSize: 10, fontWeight: '700', letterSpacing: 1 },

  zmanRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 13,
    borderBottomWidth: 1,
  },
  zmanLabel: { fontSize: 14, flex: 1, paddingRight: 16 },
  zmanTime: { fontSize: 15, fontWeight: '700', textAlign: 'right' },

  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },

  footer: {
    marginTop: 24,
    paddingTop: 20,
    paddingBottom: 8,
    borderTopWidth: 1,
    alignItems: 'center',
    gap: 5,
  },
  footerBrand: { fontSize: 14, fontWeight: '800', letterSpacing: 0.3 },
  footerEmail: { fontSize: 13 },
  footerVersion: { fontSize: 11, marginTop: 2 },
  footerCopyright: { fontSize: 11, marginTop: 2 },
});
