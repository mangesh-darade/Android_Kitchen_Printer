import React, {useEffect, useState} from 'react';
import {View, Text, TouchableOpacity, StyleSheet, Linking} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {APP_NAME, ELINTOM_URL} from '../core/app-identity';
import {loadConfig} from '../native/posConnect';
import {colors, layout} from '../theme/styles';
import {RootStackParamList} from '../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Welcome'>;

function formatSiteName(rawName?: string, rawUrl?: string): string {
  if (rawName && rawName.trim() && rawName !== 'Default Division') {
    return rawName.trim();
  }
  if (rawUrl) {
    try {
      const parsed = new URL(rawUrl);
      const host = parsed.hostname.replace(/^www\./i, '');
      const parts = host.split('.');
      if (parts.length >= 2 && !['app', 'pos', 'admin', 'panel'].includes(parts[0].toLowerCase())) {
        return parts[0].split(/[-_]/).map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ');
      }
      return host;
    } catch {
      return rawUrl.replace(/^https?:\/\//i, '').split('/')[0];
    }
  }
  return APP_NAME;
}

export function WelcomeScreen({navigation}: Props) {
  const [siteName, setSiteName] = useState(APP_NAME);

  useEffect(() => {
    loadConfig().then(cfg => {
      const detected = formatSiteName(cfg.division?.name, cfg.division?.url);
      if (detected) setSiteName(detected);
    });
  }, []);

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        {/* Centered Site Logo */}
        <View style={styles.logoWrapper}>
          <View style={styles.logoBadge}>
            <Text style={styles.logoSymbol}>⚡</Text>
          </View>
        </View>

        <Text style={styles.title}>{siteName}</Text>
        <Text style={styles.subtitle}>
          Smart POS & Kitchen Thermal Printing System.
        </Text>

        <TouchableOpacity
          style={styles.primaryBtn}
          activeOpacity={0.8}
          onPress={() => navigation.navigate('Setup')}>
          <Text style={styles.primaryBtnText}>Start Setup</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity
        activeOpacity={0.7}
        onPress={() => Linking.openURL(ELINTOM_URL)}>
        <Text style={styles.poweredBy}>
          Powered by <Text style={styles.brandLink}>ElintOm</Text> ↗
        </Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.bg,
    justifyContent: 'space-between',
    padding: layout.pad,
    paddingBottom: 24,
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  logoWrapper: {
    marginBottom: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoBadge: {
    width: 80,
    height: 80,
    borderRadius: 24,
    backgroundColor: '#1E293B',
    borderWidth: 2,
    borderColor: '#3B82F6',
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 8,
    shadowColor: '#3B82F6',
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.35,
    shadowRadius: 8,
  },
  logoSymbol: {
    fontSize: 38,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 32,
    fontWeight: '700',
    marginBottom: 12,
    textAlign: 'center',
    letterSpacing: -0.5,
  },
  subtitle: {
    color: '#CBD5E1',
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 36,
    textAlign: 'center',
    maxWidth: 280,
  },
  primaryBtn: {
    backgroundColor: colors.primary,
    paddingVertical: 15,
    paddingHorizontal: 48,
    borderRadius: 14,
    alignItems: 'center',
    width: '100%',
    maxWidth: 300,
    elevation: 4,
    shadowColor: colors.primary,
    shadowOffset: {width: 0, height: 3},
    shadowOpacity: 0.4,
    shadowRadius: 6,
  },
  primaryBtnText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '600',
  },
  poweredBy: {
    color: '#94A3B8',
    fontSize: 13,
    textAlign: 'center',
    fontWeight: '500',
  },
  brandLink: {
    fontWeight: '700',
    textDecorationLine: 'underline',
    color: '#60A5FA',
  },
});
