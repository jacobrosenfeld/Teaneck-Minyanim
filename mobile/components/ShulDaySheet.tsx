import React, { useCallback, useEffect, useRef } from 'react';
import {
  Animated,
  Linking,
  Modal,
  PanResponder,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SymbolView } from 'expo-symbols';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import MinyanCard from '@/components/MinyanCard';
import { useOrgSchedule, useOrganization } from '@/api/hooks';
import { toApiDate } from '@/api/client';
import type { ScheduleEvent } from '@/api/types';
import { format, parseISO } from 'date-fns';

function openDirections(address: string) {
  const encoded = encodeURIComponent(address);
  const url = Platform.select({
    ios: `maps://?q=${encoded}`,
    android: `geo:0,0?q=${encoded}`,
    default: `https://maps.google.com/maps?q=${encoded}`,
  });
  Linking.openURL(url!);
}

interface Props {
  event: ScheduleEvent | null;  // The tapped event (determines org + date + highlight)
  date: string;                 // 'YYYY-MM-DD' — the day being viewed
  onClose: () => void;
}

export default function ShulDaySheet({ event, date, onClose }: Props) {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  const orgSlug = event?.organization?.slug ?? event?.organization?.id ?? '';
  const orgColor = event?.organization?.color ?? colors.tint;
  const orgName = event?.organization?.name ?? '';

  const { data: events } = useOrgSchedule(orgSlug, { date });
  const { data: org } = useOrganization(orgSlug);

  // Sort events by start time
  const sorted = events
    ? [...events].sort((a, b) => a.startTime.localeCompare(b.startTime))
    : [];

  // Scroll to the selected event after render
  const scrollRef = useRef<ScrollView>(null);
  const selectedYRef = useRef<number | null>(null);

  const scrollToSelected = useCallback(() => {
    if (selectedYRef.current != null) {
      scrollRef.current?.scrollTo({ y: Math.max(0, selectedYRef.current - 40), animated: true });
    }
  }, []);

  useEffect(() => {
    if (event && sorted.length > 0) {
      // Give the list a frame to render before scrolling
      const t = setTimeout(scrollToSelected, 120);
      return () => clearTimeout(t);
    }
  }, [event?.id, sorted.length]); // eslint-disable-line react-hooks/exhaustive-deps

  // Slide-up + dismiss animation
  const translateY = useRef(new Animated.Value(600)).current;

  useEffect(() => {
    if (event) {
      Animated.spring(translateY, {
        toValue: 0,
        useNativeDriver: true,
        stiffness: 280,
        damping: 28,
      }).start();
    } else {
      translateY.setValue(600);
    }
  }, [!!event]); // eslint-disable-line react-hooks/exhaustive-deps

  const dismiss = useCallback(() => {
    Animated.timing(translateY, {
      toValue: 600,
      duration: 220,
      useNativeDriver: true,
    }).start(onClose);
  }, [onClose, translateY]);

  const dismissPan = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onPanResponderMove: (_, gs) => {
        if (gs.dy > 0) translateY.setValue(gs.dy);
      },
      onPanResponderRelease: (_, gs) => {
        if (gs.dy > 80 || gs.vy > 0.5) {
          dismiss();
        } else {
          Animated.spring(translateY, {
            toValue: 0,
            useNativeDriver: true,
            stiffness: 300,
            damping: 30,
          }).start();
        }
      },
    }),
  ).current;

  const parsedDate = event ? parseISO(date) : null;

  return (
    <Modal
      visible={!!event}
      animationType="none"
      transparent
      onRequestClose={dismiss}>
      <View style={styles.overlay}>
        <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={dismiss} />

        <Animated.View
          style={[
            styles.sheet,
            { backgroundColor: colors.card, transform: [{ translateY }] },
          ]}>
          {/* Drag zone: handle + color bar + org name/date — no tappable children so pan responder works cleanly */}
          <View {...dismissPan.panHandlers}>
            <View style={styles.handleArea}>
              <View style={[styles.handle, { backgroundColor: colors.border }]} />
            </View>

            <View style={[styles.headerBar, { backgroundColor: orgColor }]} />
            <View style={[styles.header, { borderBottomColor: colors.border }]}>
              <View style={styles.headerContent}>
                <Text style={[styles.orgName, { color: colors.text }]} numberOfLines={1}>
                  {orgName}
                </Text>
                {parsedDate ? (
                  <Text style={[styles.dateLabel, { color: colors.textSecondary }]}>
                    {format(parsedDate, 'EEEE, MMMM d')}
                  </Text>
                ) : null}
              </View>
              <TouchableOpacity onPress={dismiss} hitSlop={10} style={styles.closeBtn}>
                <Text style={[styles.closeText, { color: colors.textTertiary }]}>✕</Text>
              </TouchableOpacity>
            </View>
          </View>

          {/* Address + directions — outside drag zone to avoid gesture conflicts */}
          {org?.address ? (
            <View style={[styles.addressRow, { borderBottomColor: colors.border }]}>
              <TouchableOpacity style={styles.addressTextWrap} onPress={() => openDirections(org.address!)}>
                <SymbolView name="location.fill" tintColor={colors.textSecondary} size={11} />
                <Text style={[styles.addressText, { color: colors.textSecondary }]} numberOfLines={1}>
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

          {/* Events list */}
          <ScrollView
            ref={scrollRef}
            style={styles.list}
            contentContainerStyle={styles.listContent}
            showsVerticalScrollIndicator={false}>
            {sorted.length === 0 ? (
              <Text style={[styles.empty, { color: colors.textSecondary }]}>
                No minyanim scheduled.
              </Text>
            ) : (
              sorted.map((e) => (
                <View
                  key={e.id}
                  onLayout={(ev) => {
                    if (e.id === event?.id) {
                      selectedYRef.current = ev.nativeEvent.layout.y;
                    }
                  }}>
                  <MinyanCard
                    event={e}
                    showOrg={false}
                    isNext={false}
                    isHighlighted={e.id === event?.id}
                  />
                </View>
              ))
            )}
          </ScrollView>
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  sheet: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: '80%',
    overflow: 'hidden',
  },
  handleArea: {
    alignItems: 'center',
    paddingTop: 10,
    paddingBottom: 6,
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
  },
  headerBar: {
    height: 4,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  headerContent: { flex: 1 },
  orgName: { fontSize: 17, fontWeight: '800', letterSpacing: -0.3 },
  dateLabel: { fontSize: 13, marginTop: 2 },
  addressRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 10, borderBottomWidth: 1, gap: 8 },
  addressTextWrap: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 4 },
  addressText: { fontSize: 12, fontWeight: '500', flex: 1 },
  dirBtn: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 5, flexShrink: 0 },
  dirBtnText: { fontSize: 12, fontWeight: '700', color: '#fff' },
  closeBtn: { padding: 4, alignSelf: 'flex-start' },
  closeText: { fontSize: 16 },
  list: { flexGrow: 1 },
  listContent: { paddingTop: 8, paddingBottom: 40 },
  empty: { textAlign: 'center', paddingVertical: 32, fontSize: 14, fontStyle: 'italic' },
});
