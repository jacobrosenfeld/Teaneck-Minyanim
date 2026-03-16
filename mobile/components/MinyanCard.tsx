import React, { useCallback, useEffect, useState } from 'react';
import { Linking, Platform, Pressable, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SymbolView } from 'expo-symbols';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import { formatTime } from '@/utils/time';
import { scheduleReminder, cancelReminder, isReminderSet } from '@/utils/notifications';
import type { ScheduleEvent } from '@/api/types';

const TYPE_COLORS: Record<string, { text: string; darkText: string }> = {
  SHACHARIS:     { text: '#1A4FAD', darkText: '#93B8FF' },
  MINCHA:        { text: '#92650A', darkText: '#FCD34D' },
  MAARIV:        { text: '#5B21B6', darkText: '#C4B5FD' },
  MINCHA_MAARIV: { text: '#92650A', darkText: '#FCD34D' },
  SELICHOS:      { text: '#9B1C1C', darkText: '#FCA5A5' },
  default:       { text: '#374151', darkText: '#D1D5DB' },
};

interface Props {
  event: ScheduleEvent;
  showOrg?: boolean;
  isNext?: boolean;
  isHighlighted?: boolean;
  onPress?: () => void;
}

export default function MinyanCard({
  event,
  showOrg = true,
  isNext = false,
  isHighlighted = false,
  onPress,
}: Props) {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];
  const orgColor = event.organization?.color ?? colors.tint;

  const typeColor = (TYPE_COLORS[event.minyanType] ?? TYPE_COLORS.default)[
    scheme === 'dark' ? 'darkText' : 'text'
  ];

  const whatsappNum = event.whatsapp ?? event.organization?.whatsapp;

  const openWhatsApp = () => {
    if (!whatsappNum) return;
    const num = whatsappNum.replace(/\D/g, '');
    Linking.openURL(`https://wa.me/${num}`);
  };

  // ── Reminder bell ──────────────────────────────────────────────────────────
  const [reminderSet, setReminderSet] = useState(false);

  useEffect(() => {
    let cancelled = false;
    isReminderSet(event.id).then((v) => { if (!cancelled) setReminderSet(v); });
    return () => { cancelled = true; };
  }, [event.id]);

  const toggleReminder = useCallback(async () => {
    if (reminderSet) {
      await cancelReminder(event.id);
      setReminderSet(false);
    } else {
      const id = await scheduleReminder({
        eventId: event.id,
        orgName: event.organization?.name ?? '',
        minyanType: event.minyanTypeDisplay,
        startTime: event.startTime,
        date: event.date,
        minutesBefore: 10,
      });
      setReminderSet(id != null);
    }
  }, [reminderSet, event]);
  // ──────────────────────────────────────────────────────────────────────────

  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        {
          backgroundColor: colors.card,
          shadowColor: colors.shadowStrong,
          borderColor: isHighlighted ? orgColor : colors.border,
          borderWidth: isHighlighted ? 2 : 1,
          opacity: pressed && onPress ? 0.85 : 1,
          transform: [{ scale: pressed && onPress ? 0.98 : 1 }],
        },
      ]}>
      {/* Full-width org color banner */}
      {showOrg && event.organization?.name ? (
        <View style={[styles.orgBanner, { backgroundColor: orgColor }]}>
          <Text style={styles.orgBannerText} numberOfLines={1}>
            {event.organization.name.toUpperCase()}
          </Text>
        </View>
      ) : null}

      <View style={styles.inner}>
        {/* Type + Time row */}
        <View style={styles.typeTimeRow}>
          <Text style={[styles.type, { color: typeColor }]} numberOfLines={1}>
            {event.minyanTypeDisplay}
          </Text>
          <View style={styles.timeRow}>
            <Text style={[styles.time, { color: colors.text }]}>
              {formatTime(event.startTime)}
            </Text>
            {/* Reminder bell */}
            <TouchableOpacity onPress={toggleReminder} hitSlop={10} style={styles.bellBtn}>
              <SymbolView
                name={reminderSet ? 'bell.fill' : 'bell'}
                tintColor={reminderSet ? orgColor : colors.textTertiary}
                size={16}
              />
            </TouchableOpacity>
          </View>
        </View>

        {/* Nusach */}
        {event.nusachDisplay ? (
          <Text style={[styles.nusach, { color: colors.textSecondary }]}>
            {event.nusachDisplay}
          </Text>
        ) : null}

        {/* Location */}
        {event.locationName ? (
          <Text style={[styles.meta, { color: colors.textSecondary }]} numberOfLines={1}>
            {event.locationName}
          </Text>
        ) : null}

        {/* Notes / dynamic time */}
        {(event.notes || event.dynamicTimeString) ? (
          <Text style={[styles.notes, { color: colors.textTertiary }]} numberOfLines={2}>
            {[event.notes, event.dynamicTimeString].filter(Boolean).join(' · ')}
          </Text>
        ) : null}

        {/* WhatsApp badge */}
        {whatsappNum ? (
          <TouchableOpacity onPress={openWhatsApp} hitSlop={8} style={styles.whatsappRow}>
            <View style={styles.whatsappBadge}>
              <Text style={styles.whatsappText}>💬 WhatsApp</Text>
            </View>
          </TouchableOpacity>
        ) : null}
      </View>

      {/* "Next" indicator dot */}
      {isNext && (
        <View style={[styles.nextDot, { backgroundColor: orgColor }]} />
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 12,
    marginHorizontal: 16,
    marginVertical: 5,
    overflow: 'hidden',
    ...Platform.select({
      ios: {
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 1,
        shadowRadius: 6,
      },
      android: { elevation: 3 },
    }),
  },
  orgBanner: {
    paddingHorizontal: 14,
    paddingVertical: 7,
  },
  orgBannerText: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.8,
    color: '#fff',
  },
  inner: {
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  typeTimeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    marginBottom: 3,
    gap: 8,
  },
  type: {
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: -0.3,
    flex: 1,
  },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flexShrink: 0,
  },
  time: {
    fontSize: 18,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  bellBtn: {
    padding: 2,
  },
  nusach: {
    fontSize: 12,
    fontStyle: 'italic',
    marginBottom: 2,
  },
  meta: {
    fontSize: 12,
    fontWeight: '500',
    marginTop: 1,
  },
  notes: {
    fontSize: 12,
    marginTop: 2,
    fontStyle: 'italic',
    lineHeight: 16,
  },
  whatsappRow: {
    marginTop: 8,
    alignSelf: 'flex-start',
  },
  whatsappBadge: {
    backgroundColor: '#E8FAF0',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderWidth: 1,
    borderColor: '#25D366',
  },
  whatsappText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#1A9E4A',
  },
  nextDot: {
    position: 'absolute',
    top: 10,
    right: 10,
    width: 8,
    height: 8,
    borderRadius: 4,
  },
});
