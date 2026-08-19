import React, {useState} from 'react';
import {Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {getStarInfo, getStarStatus, loadConfig, testPrinter} from '../native/posConnect';
import {
  discoverStarPrinters,
  setStarIo10Logging,
  starCutPaper,
  starGetConfiguration,
  starGetDefaultConfiguration,
  starOpenDrawer,
  starSetConfiguration,
} from '../printer/starSdk';
import {STAR_SDK_CATALOG} from '../printer/starSdkCatalog';
import {STAR_WEB_SDK} from '../printer/webSdk';
import {colors, layout} from '../theme/styles';
import {RootStackParamList} from '../App';
import type {AppConfig, DiscoveredPrinter} from '../core/config/models';

type Props = NativeStackScreenProps<RootStackParamList, 'PrinterTest'>;

export function PrinterTestScreen({navigation}: Props) {
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);
  const [found, setFound] = useState<DiscoveredPrinter[]>([]);
  const [logging, setLogging] = useState(false);
  const [configXml, setConfigXml] = useState('');

  async function withConfig(work: (config: AppConfig) => Promise<void>) {
    setBusy(true);
    try {
      const config = await loadConfig();
      await work(config);
    } catch (error) {
      Alert.alert('Star SDK', error instanceof Error ? error.message : 'Failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Star SDK tools</Text>
      <Text style={styles.hint}>
        Android + iOS StarIO10 share react-native-star-io10. Existing ESC/POS LAN is unchanged.
      </Text>

      <Action
        title="Discover Star (LAN + BT + BLE + USB)"
        disabled={busy}
        onPress={() =>
          withConfig(async () => {
            const printers = await discoverStarPrinters();
            setFound(printers);
            setStatus(printers.length ? `Found ${printers.length}` : 'No Star printers found');
          })
        }
      />
      {found.map(item => (
        <Text key={`${item.connectionType}-${item.identifier}`} style={styles.line}>
          {item.name} · {item.connectionType} · {item.identifier}
        </Text>
      ))}

      <Action
        title="Test print (active engine)"
        disabled={busy}
        onPress={() =>
          withConfig(async config => {
            const res = await testPrinter(config);
            setStatus(res.message || (res.success ? 'OK' : 'Failed'));
            Alert.alert('Test print', res.message || (res.success ? 'Sent' : 'Failed'));
          })
        }
      />
      <Action
        title="Open cash drawer"
        disabled={busy}
        onPress={() =>
          withConfig(async config => {
            const res = await starOpenDrawer(config.printer);
            setStatus(res.message || '');
          })
        }
      />
      <Action
        title="Cut paper"
        disabled={busy}
        onPress={() =>
          withConfig(async config => {
            const res = await starCutPaper(config.printer);
            setStatus(res.message || '');
          })
        }
      />
      <Action
        title="Device Manager: get status"
        disabled={busy}
        onPress={() =>
          withConfig(async config => {
            const res = await getStarStatus(config.printer);
            setStatus(
              `ready=${res.ready} paperOut=${res.paperOut} cover=${res.coverOpen} ${res.error || ''}`,
            );
          })
        }
      />
      <Action
        title="Device Manager: printer information"
        disabled={busy}
        onPress={() =>
          withConfig(async config => {
            const res = await getStarInfo(config.printer);
            setStatus(JSON.stringify(res.data || res.message || res));
          })
        }
      />
      <Action
        title="Star Configuration Format (get)"
        disabled={busy}
        onPress={() =>
          withConfig(async config => {
            const res = await starGetConfiguration(config.printer);
            const xml = typeof res.data === 'string' ? res.data : '';
            setConfigXml(xml);
            setStatus(xml ? xml.slice(0, 500) : res.message || '');
          })
        }
      />
      <Action
        title="Star Configuration Format (get default)"
        disabled={busy}
        onPress={() =>
          withConfig(async config => {
            const res = await starGetDefaultConfiguration(config.printer);
            const xml = typeof res.data === 'string' ? res.data : '';
            setConfigXml(xml);
            setStatus(xml ? xml.slice(0, 500) : res.message || '');
          })
        }
      />
      <Action
        title="Star Configuration Format (apply last get)"
        disabled={busy || !configXml}
        onPress={() =>
          withConfig(async config => {
            const res = await starSetConfiguration(config.printer, configXml);
            setStatus(res.message || (res.success ? 'Applied' : 'Failed'));
            Alert.alert('Star Configuration', res.message || (res.success ? 'Applied' : 'Failed'));
          })
        }
      />
      <Action
        title={logging ? 'Stop StarIO10 logger' : 'Start StarIO10 logger'}
        disabled={busy}
        onPress={async () => {
          const next = !logging;
          await setStarIo10Logging(next);
          setLogging(next);
          setStatus(next ? 'StarIO10 logging on' : 'StarIO10 logging off');
        }}
      />

      {!!status && <Text style={styles.status}>{status}</Text>}

      <Text style={styles.section}>SDK coverage</Text>
      {STAR_SDK_CATALOG.map(item => (
        <Text key={item.id} style={styles.catalog}>
          {item.inApp ? 'IN APP' : 'DOCUMENTED'} · {item.title}
        </Text>
      ))}
      <Text style={styles.catalog}>
        WEB SDK · {STAR_WEB_SDK.name} — {STAR_WEB_SDK.reason}
      </Text>

      <TouchableOpacity style={styles.linkBtn} onPress={() => navigation.goBack()}>
        <Text style={styles.linkBtnText}>Back</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

function Action({
  title,
  onPress,
  disabled,
}: {
  title: string;
  onPress: () => void;
  disabled: boolean;
}) {
  return (
    <TouchableOpacity style={styles.btn} onPress={onPress} disabled={disabled}>
      <Text style={styles.btnText}>{title}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: {padding: layout.pad, backgroundColor: colors.card, flexGrow: 1},
  heading: {fontSize: 22, fontWeight: '700', color: colors.text, marginBottom: 8},
  hint: {color: colors.muted, marginBottom: 16, lineHeight: 20},
  section: {marginTop: 20, fontWeight: '700', color: colors.text},
  line: {fontSize: 13, color: colors.text, marginBottom: 4},
  catalog: {fontSize: 12, color: colors.muted, marginTop: 6},
  status: {marginTop: 16, color: colors.text},
  btn: {
    marginTop: 10,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: layout.radius,
    padding: 14,
  },
  btnText: {color: colors.text, fontWeight: '600'},
  linkBtn: {marginTop: 24, alignItems: 'center'},
  linkBtnText: {color: colors.primary, fontWeight: '600'},
});
