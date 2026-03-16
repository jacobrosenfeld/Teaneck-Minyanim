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

// Labels matching teaneckminyanim.com exactly (transliterated Hebrew)
const ZMANIM_ROWS: { label: string; key: keyof ZmanimTimes; section?: string; highlight?: boolean }[] = [
  { label: 'Alos HaShachar', key: 'alotHashachar', section: 'Morning' },
  { label: 'Misheyakir', key: 'misheyakir' },
  { label: 'Netz', key: 'netz', highlight: true },
  { label: 'Sof Zman Krias Shema (GRA)', key: 'sofZmanShmaGra' },
  { label: 'Sof Zman Krias Shema (MGA)', key: 'sofZmanShmaMga' },
  { label: 'Sof Zman Tefilla (GRA)', key: 'sofZmanTfilaGra' },
  { label: 'Sof Zman Tefilla (MGA)', key: 'sofZmanTfilaMga' },
  { label: 'Chatzos', key: 'chatzos', section: 'Afternoon', highlight: true },
  { label: 'Mincha Gedola', key: 'minchaGedola' },
  { label: 'Mincha Ketana', key: 'minchaKetana' },
  { label: 'Plag HaMincha', key: 'plagHamincha', highlight: true },
  { label: 'Shekiya', key: 'shekiya', section: 'Evening', highlight: true },
  { label: 'Tzes HaKochavim', key: 'tzeis', highlight: true },
  { label: 'Earliest Shema', key: 'earliestShema' },
  { label: 'Chatzos Laila', key: 'chatzosLaila' },
];

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

  // Find which rows form section headers
  let currentSection = '';

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
          {ZMANIM_ROWS.map((row) => {
            const raw = zmanim?.times?.[row.key];
            const time = raw ? formatTime(raw) : null;
            const isNewSection = !!row.section && row.section !== currentSection;
            if (row.section) currentSection = row.section;

            return (
              <React.Fragment key={row.key}>
                {isNewSection && (
                  <View style={[styles.sectionHeader, { backgroundColor: colors.background }]}>
                    <View style={[styles.sectionLine, { backgroundColor: colors.border }]} />
                    <Text style={[styles.sectionLabel, { color: colors.textSecondary }]}>
                      {row.section!.toUpperCase()}
                    </Text>
                    <View style={[styles.sectionLine, { backgroundColor: colors.border }]} />
                  </View>
                )}
                <View
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

  list: { paddingBottom: 32 },

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
});
