// Web stub — react-native-maps does not support web (codegenNativeComponent crash during SSR)
import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';

export default function MapScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]} edges={['top']}>
      <View style={styles.center}>
        <Text style={[styles.text, { color: colors.textSecondary }]}>
          Map is available in the mobile app.
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  text: { fontSize: 15, textAlign: 'center' },
});
