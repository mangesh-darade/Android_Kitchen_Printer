import React, {useCallback, useEffect, useState} from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
  Linking,
  StatusBar,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useFocusEffect} from '@react-navigation/native';
import {POSWebViewNative} from '../components/POSWebViewNative';
import {loadConfig, resetApplication, saveConfig, draftFromConfig, buildConfigFromDraft, startStarPrintListener} from '../native/posConnect';
import {AppConfig} from '../core/config/models';
import {APP_NAME, POWERED_BY, ELINTOM_URL} from '../core/app-identity';
import {colors, layout} from '../theme/styles';
import {RootStackParamList} from '../App';

type Props = NativeStackScreenProps<RootStackParamList, 'PosSession'>;

function extractSiteName(url?: string): string {
  if (!url) return '';
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.replace(/^www\./i, '');
    const parts = host.split('.');
    if (parts.length >= 2) {
      const sub = parts[0];
      if (!['app', 'pos', 'admin', 'panel', 'login'].includes(sub.toLowerCase())) {
        return sub.split(/[-_]/).map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ');
      }
    }
    return host;
  } catch {
    return url.replace(/^https?:\/\//i, '').split('/')[0];
  }
}

export function PosSessionScreen({navigation}: Props) {
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [error, setError] = useState('');
  const [progress, setProgress] = useState(0);
  const [webTitle, setWebTitle] = useState('');

  const refresh = useCallback(async () => {
    const cfg = await loadConfig();
    setConfig(cfg);
    if (!cfg.setupCompleted || !cfg.division.url) {
      navigation.replace('Welcome');
    }
  }, [navigation]);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  useEffect(() => {
    const unsub = startStarPrintListener();
    return unsub;
  }, []);

  async function togglePrintDialog() {
    if (!config) return;
    const draft = draftFromConfig(config);
    draft.showPrintDialog = !draft.showPrintDialog;
    const next = buildConfigFromDraft(draft);
    await saveConfig(next);
    setConfig(next);
  }

  if (!config) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingTitle}>{APP_NAME}</Text>
        <TouchableOpacity
          style={styles.poweredByTouch}
          activeOpacity={0.7}
          onPress={() => Linking.openURL(ELINTOM_URL)}>
          <Text style={styles.poweredBy}>
            Powered by <Text style={styles.brandLink}>ElintOm</Text>
          </Text>
        </TouchableOpacity>
      </View>
    );
  }

  const autoPrint = !config.printer.showPrintDialog;
  const siteName =
    (webTitle && !webTitle.includes('http') && !webTitle.includes('404') && webTitle) ||
    (config.division.name && config.division.name !== 'Default Division' && config.division.name) ||
    extractSiteName(config.division.url) ||
    'ElintOm POS';

  const printerName =
    config.printer.name ||
    config.printer.deviceName ||
    (config.printer.connection === 'USB' ? 'USB Thermal' : config.printer.ip) ||
    'Thermal Printer';

  return (
    <View style={styles.container}>
      <StatusBar barStyle="dark-content" backgroundColor="#FFFFFF" translucent={false} />
      <SafeAreaView edges={['top']} style={styles.headerSafeArea}>
        <View style={styles.toolbar}>
          {/* Left: Site Brand & Status */}
          <View style={styles.siteInfo}>
            <View style={styles.avatarBadge}>
              <Text style={styles.avatarText}>{siteName.charAt(0).toUpperCase()}</Text>
            </View>
            <View style={styles.textContainer}>
              <Text style={styles.siteTitle} numberOfLines={1} ellipsizeMode="tail">
                {siteName}
              </Text>
              <View style={styles.statusRow}>
                <View style={[styles.statusDot, config.printer.enabled ? styles.dotOnline : styles.dotOffline]} />
                <Text style={styles.printerStatusText} numberOfLines={1}>
                  {printerName}
                </Text>
              </View>
            </View>
          </View>

          {/* Right: Mobile Pill Buttons */}
          <View style={styles.actionGroup}>
            <TouchableOpacity
              activeOpacity={0.7}
              style={[styles.pillBtn, autoPrint ? styles.pillAuto : styles.pillDialog]}
              onPress={togglePrintDialog}>
              <Text style={[styles.pillText, autoPrint ? styles.pillTextAuto : styles.pillTextDialog]}>
                {autoPrint ? '⚡ Auto' : '📋 Dialog'}
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              activeOpacity={0.7}
              style={styles.iconBtn}
              onPress={() => navigation.navigate('Settings')}>
              <Text style={styles.iconBtnText}>⚙️</Text>
            </TouchableOpacity>
          </View>
        </View>
      </SafeAreaView>

      {progress > 0 && progress < 100 && (
        <View style={styles.progressBar}>
          <View style={[styles.progressFill, {width: `${progress}%` as any}]} />
        </View>
      )}

      {!!error && (
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>{error}</Text>
          <TouchableOpacity onPress={refresh} style={styles.retryBtn}>
            <Text style={styles.retry}>Retry</Text>
          </TouchableOpacity>
        </View>
      )}

      <POSWebViewNative
        url={config.division.url}
        onError={setError}
        onLoadProgress={setProgress}
        onTitleReceived={t => {
          if (t && t.trim().length > 1) {
            setWebTitle(t.trim());
          }
        }}
      />

      <SafeAreaView edges={['bottom']} style={styles.footerSafeArea}>
        <TouchableOpacity
          style={styles.footer}
          activeOpacity={0.7}
          onPress={() => Linking.openURL(ELINTOM_URL)}>
          <Text style={styles.footerText}>
            Powered by <Text style={styles.brandLink}>ElintOm</Text> ↗
          </Text>
        </TouchableOpacity>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#FFFFFF'},
  headerSafeArea: {
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E2E8F0',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.05,
    shadowRadius: 2,
  },
  loading: {flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#F8FAFC'},
  loadingTitle: {fontSize: 22, fontWeight: '700', color: colors.text, marginTop: 14},
  poweredByTouch: {position: 'absolute', bottom: 24},
  poweredBy: {fontSize: 13, color: colors.muted, fontWeight: '500'},
  brandLink: {fontWeight: '700', textDecorationLine: 'underline', color: colors.primary},
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: '#FFFFFF',
  },
  siteInfo: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingRight: 8,
  },
  avatarBadge: {
    width: 34,
    height: 34,
    borderRadius: 10,
    backgroundColor: '#EFF6FF',
    borderWidth: 1,
    borderColor: '#BFDBFE',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 8,
  },
  avatarText: {
    color: colors.primary,
    fontSize: 16,
    fontWeight: '700',
  },
  textContainer: {
    flex: 1,
  },
  siteTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#0F172A',
    letterSpacing: -0.2,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 2,
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 5,
  },
  dotOnline: {
    backgroundColor: '#10B981',
  },
  dotOffline: {
    backgroundColor: '#EF4444',
  },
  printerStatusText: {
    fontSize: 11,
    color: '#64748B',
    fontWeight: '500',
  },
  actionGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  pillBtn: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
  },
  pillAuto: {
    backgroundColor: '#ECFDF5',
    borderColor: '#A7F3D0',
  },
  pillDialog: {
    backgroundColor: '#FFFBEB',
    borderColor: '#FDE68A',
  },
  pillText: {
    fontSize: 12,
    fontWeight: '600',
  },
  pillTextAuto: {
    color: '#065F46',
  },
  pillTextDialog: {
    color: '#92400E',
  },
  iconBtn: {
    width: 34,
    height: 34,
    borderRadius: 10,
    backgroundColor: '#F1F5F9',
    borderWidth: 1,
    borderColor: '#E2E8F0',
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconBtnText: {
    fontSize: 16,
  },
  progressBar: {
    height: 2.5,
    backgroundColor: '#E2E8F0',
  },
  progressFill: {
    height: 2.5,
    backgroundColor: colors.primary,
  },
  errorBox: {
    backgroundColor: '#FEF2F2',
    padding: layout.pad,
    borderBottomWidth: 1,
    borderBottomColor: '#FECACA',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  errorText: {color: colors.danger, flex: 1, fontSize: 13},
  retryBtn: {paddingLeft: 8},
  retry: {color: colors.primary, fontWeight: '600', fontSize: 13},
  footerSafeArea: {
    backgroundColor: '#F8FAFC',
    borderTopWidth: 1,
    borderTopColor: '#E2E8F0',
  },
  footer: {
    paddingVertical: 4,
    alignItems: 'center',
    justifyContent: 'center',
  },
  footerText: {fontSize: 11, color: colors.muted, fontWeight: '500'},
});
