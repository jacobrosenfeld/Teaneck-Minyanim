import React from 'react';
import {
  ActivityIndicator,
  Linking,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';

interface Props {
  visible: boolean;
  loading: boolean;
  onAccept: () => void;
  onDecline: () => void;
}

export default function TrackingConsentModal({
  visible,
  loading,
  onAccept,
  onDecline,
}: Props) {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={() => {}}>
      <View style={styles.overlay}>
        <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Text style={[styles.title, { color: colors.text }]}>Privacy and Analytics</Text>

          <Text style={[styles.body, { color: colors.textSecondary }]}>
            We use analytics to understand feature usage and improve the app. If you
            accept, iOS will also show Apple&apos;s tracking permission prompt.
          </Text>

          <Text style={[styles.body, { color: colors.textSecondary }]}>
            The app works fully even if you decline.
          </Text>

          <Pressable onPress={() => Linking.openURL('https://teaneckminyanim.com/privacy')}>
            <Text style={[styles.link, { color: colors.tint }]}>Read Privacy Policy</Text>
          </Pressable>

          <View style={styles.actions}>
            <Pressable
              style={[styles.button, styles.secondaryButton, { borderColor: colors.border }]}
              onPress={onDecline}
              disabled={loading}>
              <Text style={[styles.secondaryLabel, { color: colors.textSecondary }]}>Decline</Text>
            </Pressable>

            <Pressable
              style={[styles.button, styles.primaryButton, { backgroundColor: colors.tint }]}
              onPress={onAccept}
              disabled={loading}>
              {loading ? (
                <ActivityIndicator color="#fff" size="small" />
              ) : (
                <Text style={styles.primaryLabel}>Accept</Text>
              )}
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  card: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 18,
    paddingVertical: 18,
    gap: 10,
  },
  title: {
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: -0.3,
  },
  body: {
    fontSize: 14,
    lineHeight: 20,
  },
  link: {
    fontSize: 13,
    fontWeight: '700',
  },
  actions: {
    marginTop: 8,
    flexDirection: 'row',
    gap: 10,
  },
  button: {
    flex: 1,
    borderRadius: 10,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryButton: {
    borderWidth: 1,
  },
  primaryButton: {
    borderWidth: 0,
  },
  secondaryLabel: {
    fontSize: 14,
    fontWeight: '700',
  },
  primaryLabel: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '800',
  },
});
