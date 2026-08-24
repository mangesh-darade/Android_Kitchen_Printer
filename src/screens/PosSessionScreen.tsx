import React, {useCallback, useEffect, useState} from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useFocusEffect} from '@react-navigation/native';
import {POSWebViewNative} from '../components/POSWebViewNative';
import {loadConfig, resetApplication, saveConfig, draftFromConfig, buildConfigFromDraft, startStarPrintListener} from '../native/posConnect';
import {AppConfig} from '../core/config/models';
import {colors, layout} from '../theme/styles';
import {RootStackParamList} from '../App';

type Props = NativeStackScreenProps<RootStackParamList, 'PosSession'>;

export function PosSessionScreen({navigation}: Props) {
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [error, setError] = useState('');
  const [progress, setProgress] = useState(0);

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

  async function handleReset() {
    Alert.alert('Reset app?', 'Clear all settings and return to welcome screen?', [
      {text: 'Cancel', style: 'cancel'},
      {
        text: 'Reset',
        style: 'destructive',
        onPress: async () => {
          await resetApplication();
          navigation.replace('Welcome');
        },
      },
    ]);
  }

  if (!config) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  const autoPrint = !config.printer.showPrintDialog;

  return (
    <View style={styles.container}>
      <View style={styles.toolbar}>
        <View style={styles.toolbarText}>
          <Text style={styles.title} numberOfLines={1}>
            POS Session
          </Text>
          <Text style={styles.subtitle} numberOfLines={1}>
            {config.printer.name || config.printer.ip || 'No printer'} ·{' '}
            {autoPrint ? 'Auto-print' : 'Print dialog'}
          </Text>
        </View>
        <TouchableOpacity
          style={[styles.toolBtn, styles.toolBtnSecondary]}
          onPress={togglePrintDialog}>
          <Text style={styles.toolBtnText}>{autoPrint ? 'Dialog OFF' : 'Dialog ON'}</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.toolBtn, {marginLeft: 6}]}
          onPress={() => navigation.navigate('Settings')}>
          <Text style={styles.toolBtnText}>Settings</Text>
        </TouchableOpacity>
      </View>
      {progress > 0 && progress < 100 && (
        <View style={styles.progressBar}>
          <View style={[styles.progressFill, {width: `${progress}%` as any}]} />
        </View>
      )}
      {!!error && (
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>{error}</Text>
          <TouchableOpacity onPress={refresh}>
            <Text style={styles.retry}>Retry</Text>
          </TouchableOpacity>
        </View>
      )}
      <POSWebViewNative
        url={config.division.url}
        onError={setError}
        onLoadProgress={setProgress}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#FFF'},
  loading: {flex: 1, alignItems: 'center', justifyContent: 'center'},
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: layout.pad,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: '#F8FAFC',
  },
  toolbarText: {flex: 1, paddingRight: 8},
  title: {fontSize: 15, fontWeight: '700', color: colors.text},
  subtitle: {fontSize: 11, color: colors.muted, marginTop: 2},
  toolBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: 8,
  },
  toolBtnSecondary: {
    backgroundColor: '#64748B',
    marginLeft: 0,
  },
  toolBtnText: {color: '#FFF', fontWeight: '600', fontSize: 12},
  progressBar: {
    height: 3,
    backgroundColor: '#E2E8F0',
  },
  progressFill: {
    height: 3,
    backgroundColor: colors.primary,
  },
  errorBox: {
    backgroundColor: '#FEF2F2',
    padding: layout.pad,
    borderBottomWidth: 1,
    borderBottomColor: '#FECACA',
  },
  errorText: {color: colors.danger},
  retry: {color: colors.primary, marginTop: 8, fontWeight: '600'},
});
