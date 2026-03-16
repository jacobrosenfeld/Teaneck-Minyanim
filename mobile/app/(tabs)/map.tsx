import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import MapView, { Callout, Marker, Region } from 'react-native-maps';
import * as Location from 'expo-location';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { SymbolView } from 'expo-symbols';

import Colors from '@/constants/Colors';
import { useColorScheme } from '@/components/useColorScheme';
import { useOrganizations } from '@/api/hooks';
import type { Organization } from '@/api/types';

// Teaneck, NJ — default map center
const TEANECK: Region = {
  latitude: 40.8918,
  longitude: -74.014,
  latitudeDelta: 0.04,
  longitudeDelta: 0.04,
};

export default function MapScreen() {
  const scheme = useColorScheme() ?? 'light';
  const colors = Colors[scheme];
  const mapRef = useRef<MapView>(null);

  const { data: orgs, isLoading } = useOrganizations();
  const [userLocation, setUserLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const [locationGranted, setLocationGranted] = useState<boolean | null>(null);

  // Orgs with known coordinates
  const mappable = (orgs ?? []).filter(
    (o): o is Organization & { latitude: number; longitude: number } =>
      o.latitude != null && o.longitude != null,
  );

  useEffect(() => {
    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      setLocationGranted(status === 'granted');
      if (status === 'granted') {
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        setUserLocation({ latitude: loc.coords.latitude, longitude: loc.coords.longitude });
        mapRef.current?.animateToRegion(
          { latitude: loc.coords.latitude, longitude: loc.coords.longitude, latitudeDelta: 0.02, longitudeDelta: 0.02 },
          600,
        );
      }
    })();
  }, []);

  const zoomToUser = useCallback(async () => {
    if (userLocation) {
      mapRef.current?.animateToRegion(
        { ...userLocation, latitudeDelta: 0.02, longitudeDelta: 0.02 },
        400,
      );
      return;
    }
    if (locationGranted === false) return;
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status === 'granted') {
      const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setUserLocation({ latitude: loc.coords.latitude, longitude: loc.coords.longitude });
      mapRef.current?.animateToRegion(
        { latitude: loc.coords.latitude, longitude: loc.coords.longitude, latitudeDelta: 0.02, longitudeDelta: 0.02 },
        400,
      );
    }
  }, [userLocation, locationGranted]);

  const fitAllPins = useCallback(() => {
    if (mappable.length === 0) {
      mapRef.current?.animateToRegion(TEANECK, 400);
      return;
    }
    mapRef.current?.fitToCoordinates(
      mappable.map((o) => ({ latitude: o.latitude, longitude: o.longitude })),
      { edgePadding: { top: 80, right: 50, bottom: 120, left: 50 }, animated: true },
    );
  }, [mappable]);

  return (
    <View style={styles.root}>
      {/* Map */}
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        initialRegion={TEANECK}
        showsUserLocation={locationGranted === true}
        showsMyLocationButton={false}
        mapType="standard"
        userInterfaceStyle={scheme}>
        {mappable.map((org) => (
          <Marker
            key={org.id}
            coordinate={{ latitude: org.latitude, longitude: org.longitude }}
            pinColor={org.color ?? colors.tint}>
            <Callout
              tooltip
              onPress={() =>
                router.push({ pathname: '/shuls/[id]', params: { id: org.slug ?? org.id } } as never)
              }>
              <View style={[styles.callout, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <View style={[styles.calloutBanner, { backgroundColor: org.color ?? colors.tint }]} />
                <View style={styles.calloutBody}>
                  <Text style={[styles.calloutName, { color: colors.text }]} numberOfLines={2}>
                    {org.name}
                  </Text>
                  {org.address ? (
                    <Text style={[styles.calloutAddress, { color: colors.textSecondary }]} numberOfLines={1}>
                      {org.address}
                    </Text>
                  ) : null}
                  <Text style={[styles.calloutCta, { color: colors.tint }]}>View schedule →</Text>
                </View>
              </View>
            </Callout>
          </Marker>
        ))}
      </MapView>

      {/* Header overlay */}
      <SafeAreaView edges={['top']} pointerEvents="box-none">
        <View style={[styles.header, { backgroundColor: colors.card + 'F0', borderColor: colors.border }]}>
          <View>
            <Text style={[styles.siteName, { color: colors.tint }]}>Teaneck Minyanim</Text>
            <Text style={[styles.title, { color: colors.text }]}>Map</Text>
          </View>
          {isLoading ? (
            <ActivityIndicator color={colors.tint} size="small" />
          ) : (
            <Text style={[styles.count, { color: colors.textSecondary }]}>
              {mappable.length} shul{mappable.length !== 1 ? 's' : ''}
            </Text>
          )}
        </View>
      </SafeAreaView>

      {/* Controls — bottom right */}
      <View style={styles.controls} pointerEvents="box-none">
        {/* Locate me */}
        {locationGranted !== false && (
          <TouchableOpacity
            style={[styles.controlBtn, { backgroundColor: colors.card, borderColor: colors.border, shadowColor: colors.shadowStrong }]}
            onPress={zoomToUser}>
            <SymbolView name="location.fill" tintColor={colors.tint} size={20} />
          </TouchableOpacity>
        )}
        {/* Fit all pins */}
        <TouchableOpacity
          style={[styles.controlBtn, { backgroundColor: colors.card, borderColor: colors.border, shadowColor: colors.shadowStrong }]}
          onPress={fitAllPins}>
          <SymbolView name="scope" tintColor={colors.tint} size={20} />
        </TouchableOpacity>
      </View>

      {/* No coordinates notice */}
      {!isLoading && mappable.length === 0 && (
        <View style={styles.noCoords} pointerEvents="none">
          <Text style={[styles.noCoordsText, { color: colors.textSecondary }]}>
            No shul locations available yet.{'\n'}Run "Geocode All" in the admin panel.
          </Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },

  header: {
    marginHorizontal: 12,
    marginTop: 8,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.1, shadowRadius: 8 },
      android: { elevation: 4 },
    }),
  },
  siteName: { fontSize: 9, fontWeight: '800', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 1 },
  title: { fontSize: 20, fontWeight: '800', letterSpacing: -0.3 },
  count: { fontSize: 13, fontWeight: '500' },

  controls: {
    position: 'absolute',
    bottom: 110,
    right: 16,
    gap: 10,
  },
  controlBtn: {
    width: 44,
    height: 44,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.15, shadowRadius: 6 },
      android: { elevation: 4 },
    }),
  },

  callout: {
    width: 220,
    borderRadius: 12,
    borderWidth: 1,
    overflow: 'hidden',
    ...Platform.select({
      ios: { shadowOffset: { width: 0, height: 3 }, shadowOpacity: 0.15, shadowRadius: 8 },
      android: { elevation: 4 },
    }),
  },
  calloutBanner: { height: 6 },
  calloutBody: { paddingHorizontal: 14, paddingVertical: 10 },
  calloutName: { fontSize: 15, fontWeight: '700', marginBottom: 3 },
  calloutAddress: { fontSize: 12, marginBottom: 6 },
  calloutCta: { fontSize: 12, fontWeight: '600' },

  noCoords: {
    position: 'absolute',
    bottom: 130,
    left: 16,
    right: 70,
  },
  noCoordsText: {
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 20,
  },
});
