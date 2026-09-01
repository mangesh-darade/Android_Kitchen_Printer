import React, {useEffect, useState} from 'react';
import {View, Text, TouchableOpacity, ScrollView, StyleSheet, Alert, Linking} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {
  buildConfigFromDraft,
  draftFromConfig,
  loadConfig,
  resetApplication,
  resetPrinter,
  saveConfig,
  testPrinter,
  type SetupDraft,
} from '../native/posConnect';
import {AppConfig, type ConnectionType, type PrintEngine, type PrinterWidthClass} from '../core/config/models';
import {POWERED_BY, ELINTOM_URL} from '../core/app-identity';
import {PRINT_ENGINES} from '../printer/enginePolicy';
import {
  printerSdkSettingsFields,
  resolveActiveSdkTechName,
  VENDOR_SDK_CATALOG,
} from '../printer/vendorSdkCatalog';
import {Field, RowChoice, DropdownChoice, ToggleRow} from '../components/FormControls';
import {colors, layout} from '../theme/styles';
import {RootStackParamList} from '../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;

export function SettingsScreen({navigation}: Props) {
  const [config, setConfig] = useState<AppConfig | null>(null);

  useEffect(() => {
    loadConfig().then(setConfig);
  }, []);

  async function persist(next: AppConfig) {
    await saveConfig(next);
    setConfig(next);
  }

  async function updateDraft(patch: Partial<SetupDraft>) {
    if (!config) return;
    await persist(buildConfigFromDraft({...draftFromConfig(config), ...patch}));
  }

  if (!config) {
    return <View style={styles.container} />;
  }

  const sdk = printerSdkSettingsFields(
    config.printer.brand,
    config.printer.printEngine,
    config.printer.connection,
  );

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Settings</Text>
      <Text style={styles.line}>URL: {config.division.url}</Text>
      <Text style={styles.line}>
        {config.printer.brand} · {config.printer.printEngine} · {config.printer.width}
      </Text>
      <Text style={styles.sdkLine}>
        SDK: {sdk.sdkTechName}
      </Text>
      <Text style={[styles.sdkMeta, sdk.sdkIntegrated ? styles.sdkIntegrated : styles.sdkFallback]}>
        {sdk.sdkIntegrated ? 'Official SDK integrated' : 'ESC/POS fallback — SDK not bundled'}
      </Text>
      <Text style={styles.sdkMeta}>Print path: {sdk.sdkPrintPath}</Text>
      {!sdk.sdkIntegrated && sdk.sdkDownloadUrl ? (
        <Text style={styles.sdkMeta}>Download: {sdk.sdkDownloadUrl}</Text>
      ) : null}
      <Text style={styles.line}>
        Star id: {config.printer.starIdentifier || config.printer.ip || config.printer.macAddress || '—'}
      </Text>

      <Text style={styles.section}>Print engine</Text>
      <Text style={styles.sdkLine}>
        Active SDK: {resolveActiveSdkTechName(
          config.printer.brand,
          config.printer.printEngine,
          config.printer.connection,
        )}
      </Text>
      <DropdownChoice
        label="Engine"
        options={PRINT_ENGINES.map(id => ({id, title: id.replace(/_/g, ' ')}))}
        selected={config.printer.printEngine}
        onSelect={v => updateDraft({printEngine: v as PrintEngine})}
      />
      <DropdownChoice
        label="Paper width"
        options={[
          {id: '3inch', title: '3 inch'},
          {id: '4inch', title: '4 inch'},
        ]}
        selected={config.printer.width}
        onSelect={v => updateDraft({width: v as PrinterWidthClass})}
      />
      <DropdownChoice
        label="Interface"
        options={[
          {id: 'LAN', title: 'LAN'},
          {id: 'BLUETOOTH', title: 'Bluetooth'},
          {id: 'BLE', title: 'BLE'},
          {id: 'USB', title: 'USB'},
        ]}
        selected={config.printer.connection}
        onSelect={v => updateDraft({connection: v as ConnectionType})}
      />
      <Field
        label="Printer IP (ESC/POS LAN)"
        value={config.printer.ip}
        onChangeText={v => updateDraft({printerIp: v})}
        autoCapitalize="none"
      />
      <Field
        label="Star identifier (MAC or IP)"
        value={config.printer.starIdentifier}
        onChangeText={v => updateDraft({starIdentifier: v})}
        autoCapitalize="none"
      />
      <Field
        label="ESC/POS port"
        value={String(config.printer.port)}
        onChangeText={v => updateDraft({printerPort: parseInt(v, 10) || 9100})}
        keyboardType="numeric"
      />
      <Field
        label="MAC / Bluetooth"
        value={config.printer.macAddress}
        onChangeText={v => updateDraft({macAddress: v})}
        autoCapitalize="none"
      />


      {config.printer.connection === 'USB' && (
        <>
          <Field
            label="USB vendor ID"
            value={String(config.printer.usbVendorId || '')}
            onChangeText={v => updateDraft({usbVendorId: parseInt(v, 10) || 0})}
            keyboardType="numeric"
          />
          <Field
            label="USB product ID"
            value={String(config.printer.usbProductId || '')}
            onChangeText={v => updateDraft({usbProductId: parseInt(v, 10) || 0})}
            keyboardType="numeric"
          />
          <Field
            label="USB device name"
            value={config.printer.deviceName}
            onChangeText={v => updateDraft({printerDeviceName: v})}
          />
        </>
      )}
      <Field
        label="Printer name"
        value={config.printer.name}
        onChangeText={v => updateDraft({printerName: v})}
      />

      <ToggleRow
        label="Printer enabled"
        value={config.printer.enabled}
        onToggle={v => updateDraft({printerEnabled: v})}
      />
      <ToggleRow
        label="Auto reconnect"
        value={config.printer.autoReconnect}
        onToggle={v => updateDraft({autoReconnect: v})}
      />
      <Field
        label="Retry count"
        value={String(config.printer.retryCount)}
        onChangeText={v => updateDraft({retryCount: Math.max(0, parseInt(v, 10) || 0)})}
        keyboardType="numeric"
      />

      <Text style={styles.section}>Print behaviour</Text>
      <ToggleRow
        label="Show print dialog"
        value={config.printer.showPrintDialog}
        onToggle={v => updateDraft({showPrintDialog: v})}
      />
      <ToggleRow
        label="Auto cut after print"
        value={config.printer.autoCut}
        onToggle={v => updateDraft({autoCut: v})}
      />

      <DropdownChoice
        label="Cut type"
        options={[
          {id: 'partial', title: 'Partial cut'},
          {id: 'full', title: 'Full cut'},
        ]}
        selected={config.printer.cutMode}
        onSelect={v => updateDraft({cutMode: v as 'partial' | 'full'})}
      />

      <DropdownChoice
        label="White space above (Top margin)"
        options={[
          {id: '0', title: '0 lines (No extra top space)'},
          {id: '1', title: '1 line'},
          {id: '2', title: '2 lines'},
          {id: '3', title: '3 lines'},
        ]}
        selected={String(config.printer.feedLinesTop ?? 0)}
        onSelect={v => updateDraft({feedLinesTop: parseInt(v, 10) || 0})}
      />

      <DropdownChoice
        label="White space below (Bottom margin before cut)"
        options={[
          {id: '1', title: '1 line (Compact cut)'},
          {id: '2', title: '2 lines (Standard)'},
          {id: '3', title: '3 lines'},
          {id: '4', title: '4 lines'},
        ]}
        selected={String(config.printer.feedLinesBottom ?? 2)}
        onSelect={v => updateDraft({feedLinesBottom: parseInt(v, 10) || 2})}
      />

      <TouchableOpacity
        style={styles.primaryBtn}
        onPress={async () => {
          const res = await testPrinter(config);
          Alert.alert('Test print', res.success ? 'Sent to printer' : res.message || 'Failed');
        }}>
        <Text style={styles.primaryBtnText}>Test printer</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.secondaryBtn} onPress={() => navigation.navigate('PrinterTest')}>
        <Text style={styles.secondaryBtnText}>Star SDK tools (discover / status / config)</Text>
      </TouchableOpacity>

      <Text style={styles.section}>Vendor SDK catalog</Text>
      {VENDOR_SDK_CATALOG.map(entry => (
        <Text key={entry.brand} style={styles.catalogLine}>
          {entry.brand}: {entry.sdkTechName} {entry.version}
          {entry.supply === 'manual_required' ? ' (manual download)' : ''}
        </Text>
      ))}

      <TouchableOpacity style={styles.secondaryBtn} onPress={() => navigation.navigate('Setup')}>
        <Text style={styles.secondaryBtnText}>Re-run setup wizard</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={styles.secondaryBtn}
        onPress={async () => {
          await resetPrinter();
          Alert.alert('Printer reset', 'Printer settings cleared.');
          loadConfig().then(setConfig);
        }}>
        <Text style={styles.secondaryBtnText}>Reset printer only</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={styles.dangerBtn}
        onPress={async () => {
          await resetApplication();
          navigation.replace('Welcome');
        }}>
        <Text style={styles.dangerBtnText}>Reset entire app</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.linkBtn} onPress={() => navigation.goBack()}>
        <Text style={styles.linkBtnText}>Back to POS</Text>
      </TouchableOpacity>

      <TouchableOpacity
        activeOpacity={0.7}
        onPress={() => Linking.openURL(ELINTOM_URL)}>
        <Text style={styles.poweredBy}>
          Powered by <Text style={styles.brandLink}>ElintOm</Text>
        </Text>
      </TouchableOpacity>
    </ScrollView>
  );
}



const styles = StyleSheet.create({
  container: {padding: layout.pad, backgroundColor: colors.card, flexGrow: 1, paddingBottom: 24},
  heading: {fontSize: 24, fontWeight: '700', marginBottom: 12, color: colors.text},
  poweredBy: {textAlign: 'center', color: colors.muted, fontSize: 13, marginTop: 24, fontWeight: '500'},
  brandLink: {fontWeight: '700', textDecorationLine: 'underline', color: colors.primary},
  section: {marginTop: 20, marginBottom: 8, fontSize: 16, fontWeight: '600', color: colors.text},
  line: {fontSize: 15, color: colors.text, marginBottom: 6},
  sdkLine: {fontSize: 14, color: colors.primary, marginBottom: 4, fontWeight: '600'},
  sdkMeta: {fontSize: 13, marginBottom: 6},
  sdkIntegrated: {color: '#166534'},
  sdkFallback: {color: '#B45309'},
  catalogLine: {fontSize: 12, color: colors.muted, marginBottom: 4},

  primaryBtn: {
    marginTop: 20,
    backgroundColor: colors.primary,
    padding: 14,
    borderRadius: layout.radius,
    alignItems: 'center',
  },
  primaryBtnText: {color: '#FFF', fontWeight: '600'},
  secondaryBtn: {
    marginTop: 10,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    borderRadius: layout.radius,
    alignItems: 'center',
  },
  secondaryBtnText: {color: colors.text, fontWeight: '600'},
  dangerBtn: {
    marginTop: 10,
    backgroundColor: '#FEF2F2',
    padding: 14,
    borderRadius: layout.radius,
    alignItems: 'center',
  },
  dangerBtnText: {color: colors.danger, fontWeight: '600'},
  linkBtn: {marginTop: 16, alignItems: 'center'},
  linkBtnText: {color: colors.primary, fontWeight: '600'},
});
