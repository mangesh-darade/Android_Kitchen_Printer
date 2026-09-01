import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { validatePosUrl, type PrintEngine, type PrinterBrand } from '../core/config/models';
import {
  buildConfigFromDraft,
  checkUrlReachable,
  connectPrinter,
  defaultPrintEngine,
  discoverPrinters,
  emptyDraft,
  saveConfig,
  SetupDraft,
  testPrinter,
} from '../native/posConnect';
import { PRINT_ENGINES, PRINTER_BRANDS, usesExistingEscPosStack } from '../printer/enginePolicy';
import { resolveActiveSdkTechName, resolveSdkPrintPath } from '../printer/vendorSdkCatalog';
import { Field, DropdownChoice } from '../components/FormControls';
import { colors, layout } from '../theme/styles';
import { RootStackParamList } from '../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Setup'>;

const STEPS = ['Division', 'Printer', 'Review'] as const;

export function SetupScreen({ navigation }: Props) {
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<SetupDraft>(emptyDraft());
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState('');

  function patch(partial: Partial<SetupDraft>) {
    setDraft(prev => ({ ...prev, ...partial }));
  }

  async function finishSetup() {
    setBusy(true);
    setStatus('Saving configuration...');
    try {
      const config = buildConfigFromDraft(draft);
      const save = await saveConfig(config);
      if (!save.success) {
        throw new Error(save.message || 'Save failed');
      }
      setStatus('Connecting printer...');
      await connectPrinter(JSON.stringify(config.printer));
      const test = await testPrinter(config);
      if (!test.success) {
        Alert.alert(
          'Printer test',
          test.message || 'Test print failed. You can fix printer settings later.',
        );
      }
      navigation.replace('PosSession');
    } catch (e) {
      Alert.alert('Setup failed', e instanceof Error ? e.message : 'Unknown error');
    } finally {
      setBusy(false);
      setStatus('');
    }
  }

  function nextStep() {
    setError('');
    if (step === 0) {
      const checked = validatePosUrl(draft.divisionUrl);
      if (checked.ok) {
        patch({ divisionUrl: checked.url });
        void checkUrlReachable(checked.url)
          .then(ok => {
            if (!ok) {
              Alert.alert(
                'URL check',
                'Could not reach URL now. You can continue if the network is limited.',
              );
            }
          })
          .catch(() => undefined);
      }
      setStep(1);
      return;
    }
    if (step === 1) {

      if (
        draft.printEngine === 'STAR_IO10' &&
        !draft.starIdentifier.trim() &&
        !draft.printerIp.trim() &&
        !draft.macAddress.trim()
      ) {
        setError('Star identifier (IP or MAC) is required for StarIO10.');
        return;
      }
      if (usesExistingEscPosStack(draft.printEngine)) {
        if (draft.connection === 'LAN' && !draft.printerIp.trim()) {
          setError('Printer IP is required for LAN ESC/POS.');
          return;
        }
        if (
          (draft.connection === 'BLUETOOTH' || draft.connection === 'BLE') &&
          !draft.macAddress.trim()
        ) {
          setError('Bluetooth MAC address is required for ESC/POS.');
          return;
        }
        // For USB, if VID/PID are not manually set, native UsbTransport auto-detects any attached USB printer.
      }
      setStep(2);
      return;
    }
    if (step === 2) {
      void finishSetup();
    }
  }

  async function scanPrinters() {
    setBusy(true);
    setStatus(
      usesExistingEscPosStack(draft.printEngine) ? 'Scanning ESC/POS printers...' : 'Scanning Star printers...',
    );
    try {
      const probe = buildConfigFromDraft(draft);
      const found = await discoverPrinters(draft.connection, probe);
      if (found.length > 0) {
        const first = found[0];
        const id = first.identifier.includes(':') && first.connectionType === 'LAN'
          ? first.identifier.split(':')[0]
          : first.identifier;

        const vidMatch = first.identifier.match(/VID:(\d+)/i);
        const pidMatch = first.identifier.match(/PID:(\d+)/i);

        patch({
          printerIp: first.connectionType === 'LAN' ? id : draft.printerIp,
          macAddress: first.identifier,
          starIdentifier: first.identifier,
          model: first.model || draft.model,
          usbVendorId: vidMatch ? parseInt(vidMatch[1], 10) : draft.usbVendorId,
          usbProductId: pidMatch ? parseInt(pidMatch[1], 10) : draft.usbProductId,
          printerDeviceName: first.name || draft.printerDeviceName,
        });
        setStatus(`Found: ${first.name} (${first.identifier})`);
      } else {
        setStatus('No printers found.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Setup — {STEPS[step]}</Text>
      <View style={styles.steps}>
        {STEPS.map((label, i) => (
          <Text key={label} style={[styles.stepDot, i <= step && styles.stepActive]}>
            {label}
          </Text>
        ))}
      </View>

      {step === 0 && (
        <>

          <Field
            label="Division URL"
            value={draft.divisionUrl}
            onChangeText={v => patch({ divisionUrl: v })}
            autoCapitalize="none"
          />
        </>
      )}

      {step === 1 && (
        <>
          <DropdownChoice
            label="Printer brand"
            options={PRINTER_BRANDS.map(id => ({
              id,
              title: id === 'STAR' ? 'Star Micronics' : id.replace(/_/g, ' '),
            }))}
            selected={draft.brand}
            onSelect={v => {
              const brand = v as PrinterBrand;
              patch({ brand, printEngine: defaultPrintEngine(brand) });
            }}
          />
          <Text style={styles.sdkLine}>
            SDK: {resolveActiveSdkTechName(draft.brand, draft.printEngine, draft.connection)}
          </Text>
          <Text style={styles.sdkPathLine}>
            Print path: {resolveSdkPrintPath(draft.brand, draft.printEngine, draft.connection)}
          </Text>
          <DropdownChoice
            label="Print engine (Star SDKs + existing ESC/POS LAN)"
            options={PRINT_ENGINES.map(id => ({ id, title: id.replace(/_/g, ' ') }))}
            selected={draft.printEngine}
            onSelect={v => patch({ printEngine: v as PrintEngine })}
          />
          <DropdownChoice
            label="Interface"
            options={[
              { id: 'LAN', title: 'LAN / Wi-Fi' },
              { id: 'BLUETOOTH', title: 'Bluetooth' },
              { id: 'BLE', title: 'BLE' },
              { id: 'USB', title: 'USB' },
            ]}
            selected={draft.connection}
            onSelect={v => patch({ connection: v as SetupDraft['connection'] })}
          />
          <DropdownChoice
            label="Paper width"
            options={[
              { id: '3inch', title: '3 inch (48 cols / 576 dots)' },
              { id: '4inch', title: '4 inch (64 cols / 832 dots)' },
            ]}
            selected={draft.width}
            onSelect={v => patch({ width: v as SetupDraft['width'] })}
          />
          <DropdownChoice
            label="Print mode"
            options={[
              { id: 'off', title: 'Auto print (kitchen)' },
              { id: 'on', title: 'Show confirm dialog' },
            ]}
            selected={draft.showPrintDialog ? 'on' : 'off'}
            onSelect={v => patch({ showPrintDialog: v === 'on' })}
          />
          <DropdownChoice
            label="Auto cut"
            options={[
              { id: 'yes', title: 'Auto cut' },
              { id: 'no', title: 'No cut' },
            ]}
            selected={draft.autoCut ? 'yes' : 'no'}
            onSelect={v => patch({ autoCut: v === 'yes' })}
          />
          {draft.autoCut && (
            <DropdownChoice
              label="Cut type"
              options={[
                { id: 'partial', title: 'Partial cut' },
                { id: 'full', title: 'Full cut' },
              ]}
              selected={draft.cutMode}
              onSelect={v => patch({ cutMode: v as SetupDraft['cutMode'] })}
            />
          )}

          <Field
            label="Star identifier (MAC or IP)"
            value={draft.starIdentifier}
            onChangeText={v => patch({ starIdentifier: v, printerIp: draft.printerIp || v })}
            autoCapitalize="none"
          />
          <Field label="Printer IP (ESC/POS LAN port 9100)" value={draft.printerIp} onChangeText={v => patch({ printerIp: v })} autoCapitalize="none" />
          <Field
            label="Port (ESC/POS only)"
            value={String(draft.printerPort)}
            onChangeText={v => patch({ printerPort: parseInt(v, 10) || 9100 })}
            keyboardType="numeric"
          />
          <Field label="MAC / Bluetooth address" value={draft.macAddress} onChangeText={v => patch({ macAddress: v })} autoCapitalize="none" />
          <Field label="Model" value={draft.model} onChangeText={v => patch({ model: v })} />
          <Field label="Printer name" value={draft.printerName} onChangeText={v => patch({ printerName: v })} />

          <DropdownChoice
            label="Printer enabled"
            options={[
              { id: 'yes', title: 'Enabled' },
              { id: 'no', title: 'Disabled' },
            ]}
            selected={draft.printerEnabled ? 'yes' : 'no'}
            onSelect={v => patch({ printerEnabled: v === 'yes' })}
          />
          <DropdownChoice
            label="Auto reconnect"
            options={[
              { id: 'yes', title: 'Reconnect' },
              { id: 'no', title: 'Manual' },
            ]}
            selected={draft.autoReconnect ? 'yes' : 'no'}
            onSelect={v => patch({ autoReconnect: v === 'yes' })}
          />
          <Field
            label="Retry count"
            value={String(draft.retryCount)}
            onChangeText={v => patch({ retryCount: Math.max(0, parseInt(v, 10) || 0) })}
            keyboardType="numeric"
          />
          {draft.connection === 'USB' && (
            <>
              <Field
                label="USB vendor ID"
                value={String(draft.usbVendorId || '')}
                onChangeText={v => patch({ usbVendorId: parseInt(v, 10) || 0 })}
                keyboardType="numeric"
              />
              <Field
                label="USB product ID"
                value={String(draft.usbProductId || '')}
                onChangeText={v => patch({ usbProductId: parseInt(v, 10) || 0 })}
                keyboardType="numeric"
              />
              <Field
                label="USB device name"
                value={draft.printerDeviceName}
                onChangeText={v => patch({ printerDeviceName: v })}
              />
            </>
          )}

          <TouchableOpacity style={styles.secondaryBtn} onPress={scanPrinters}>
            <Text style={styles.secondaryBtnText}>
              {usesExistingEscPosStack(draft.printEngine) ? 'Scan ESC/POS printers' : 'Discover Star printers'}
            </Text>
          </TouchableOpacity>
        </>
      )}

      {step === 2 && (
        <View style={styles.review}>

          <Text style={styles.reviewLine}>URL: {draft.divisionUrl}</Text>
          <Text style={styles.reviewLine}>
            Printer: {draft.brand} · {draft.printEngine} · {draft.width}
          </Text>
          <Text style={styles.reviewSdk}>
            SDK: {resolveActiveSdkTechName(draft.brand, draft.printEngine, draft.connection)}
          </Text>
          <Text style={styles.reviewLine}>
            {draft.printEngine === 'STAR_IO10'
              ? `Star id: ${draft.starIdentifier || draft.printerIp || draft.macAddress}`
              : `LAN: ${draft.printerIp}:${draft.printerPort}`}
          </Text>
          <Text style={styles.reviewLine}>
            Print: {draft.showPrintDialog ? 'Dialog' : 'Auto'} ·{' '}
            {draft.autoCut ? draft.cutMode + ' cut' : 'No cut'}
          </Text>
        </View>
      )}

      {!!error && <Text style={styles.error}>{error}</Text>}
      {!!status && <Text style={styles.status}>{status}</Text>}
      {busy && <ActivityIndicator color={colors.primary} style={{ marginVertical: 12 }} />}

      <View style={styles.actions}>
        {step > 0 && (
          <TouchableOpacity style={styles.secondaryBtn} onPress={() => setStep(s => s - 1)}>
            <Text style={styles.secondaryBtnText}>Back</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity style={styles.primaryBtn} onPress={nextStep} disabled={busy}>
          <Text style={styles.primaryBtnText}>
            {step === STEPS.length - 1 ? 'Complete Setup' : 'Next'}
          </Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: layout.pad, backgroundColor: colors.card, flexGrow: 1 },
  heading: { fontSize: 24, fontWeight: '700', color: colors.text, marginBottom: 8 },
  steps: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 16 },
  stepDot: { fontSize: 12, color: colors.muted, backgroundColor: colors.border, padding: 6, borderRadius: 8 },
  stepActive: { color: colors.primary, fontWeight: '700' },
  field: { marginBottom: 14 },
  label: { fontSize: 14, color: colors.muted, marginBottom: 6 },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: layout.radius,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 16,
    color: colors.text,
  },
  choiceRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  choice: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: layout.radius,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  choiceSelected: { borderColor: colors.primary, backgroundColor: '#EFF6FF' },
  choiceText: { color: colors.text, fontSize: 14 },
  choiceTextSelected: { color: colors.primary, fontWeight: '600' },
  review: { backgroundColor: '#F8FAFC', padding: 16, borderRadius: layout.radius, gap: 8 },
  reviewLine: { fontSize: 15, color: colors.text },
  reviewSdk: { fontSize: 14, color: colors.primary, fontWeight: '600' },
  sdkLine: { fontSize: 14, color: colors.primary, fontWeight: '600', marginBottom: 4 },
  sdkPathLine: { fontSize: 13, color: colors.muted, marginBottom: 12 },
  error: { color: colors.danger, marginTop: 8 },
  status: { color: colors.muted, marginTop: 8 },
  actions: { flexDirection: 'row', gap: 12, marginTop: 20 },
  primaryBtn: {
    flex: 1,
    backgroundColor: colors.primary,
    paddingVertical: 14,
    borderRadius: layout.radius,
    alignItems: 'center',
  },
  primaryBtnText: { color: '#FFF', fontWeight: '600', fontSize: 16 },
  secondaryBtn: {
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderRadius: layout.radius,
    alignItems: 'center',
  },
  secondaryBtnText: { color: colors.text, fontWeight: '600' },
});
