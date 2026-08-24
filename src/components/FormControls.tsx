import React, { useState } from 'react';
import {View, Text, TextInput, TouchableOpacity, StyleSheet, Modal, FlatList, TouchableWithoutFeedback} from 'react-native';
import {colors, layout} from '../theme/styles';

export function Field({
  label,
  value,
  onChangeText,
  autoCapitalize,
  keyboardType,
}: {
  label: string;
  value: string;
  onChangeText: (v: string) => void;
  autoCapitalize?: 'none' | 'sentences';
  keyboardType?: 'default' | 'numeric' | 'url';
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={styles.input}
        value={value}
        onChangeText={onChangeText}
        autoCapitalize={autoCapitalize}
        keyboardType={keyboardType === 'url' ? 'default' : keyboardType}
      />
    </View>
  );
}

export function RowChoice({
  label,
  options,
  selected,
  onSelect,
}: {
  label: string;
  options: {id: string; title: string}[];
  selected: string;
  onSelect: (id: string) => void;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.choiceRow}>
        {options.map(opt => (
          <TouchableOpacity
            key={opt.id}
            style={[styles.choice, selected === opt.id && styles.choiceSelected]}
            onPress={() => onSelect(opt.id)}>
            <Text style={[styles.choiceText, selected === opt.id && styles.choiceTextSelected]}>
              {opt.title}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
}
export function DropdownChoice({
  label,
  options,
  selected,
  onSelect,
}: {
  label: string;
  options: {id: string; title: string}[];
  selected: string;
  onSelect: (id: string) => void;
}) {
  const [modalVisible, setModalVisible] = useState(false);
  const selectedOption = options.find(o => o.id === selected) || options[0];

  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TouchableOpacity style={styles.dropdownButton} onPress={() => setModalVisible(true)}>
        <Text style={styles.dropdownButtonText}>{selectedOption?.title || 'Select...'}</Text>
        <Text style={styles.dropdownIcon}>▼</Text>
      </TouchableOpacity>

      <Modal visible={modalVisible} transparent animationType="fade" onRequestClose={() => setModalVisible(false)}>
        <TouchableWithoutFeedback onPress={() => setModalVisible(false)}>
          <View style={styles.modalOverlay}>
            <TouchableWithoutFeedback>
              <View style={styles.modalContent}>
                <Text style={styles.modalTitle}>{label}</Text>
                <FlatList
                  data={options}
                  keyExtractor={item => item.id}
                  renderItem={({item}) => (
                    <TouchableOpacity
                      style={[styles.modalItem, selected === item.id && styles.modalItemSelected]}
                      onPress={() => {
                        onSelect(item.id);
                        setModalVisible(false);
                      }}>
                      <Text style={[styles.modalItemText, selected === item.id && styles.modalItemTextSelected]}>
                        {item.title}
                      </Text>
                    </TouchableOpacity>
                  )}
                />
              </View>
            </TouchableWithoutFeedback>
          </View>
        </TouchableWithoutFeedback>
      </Modal>
    </View>
  );
}


export function ToggleRow({
  label,
  value,
  onToggle,
}: {
  label: string;
  value: boolean;
  onToggle: (v: boolean) => void;
}) {
  return (
    <TouchableOpacity style={styles.toggleRow} onPress={() => onToggle(!value)}>
      <Text style={styles.line}>{label}</Text>
      <Text style={styles.toggleValue}>{value ? 'ON' : 'OFF'}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  field: {marginBottom: 14},
  label: {fontSize: 14, color: colors.muted, marginBottom: 6},
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: layout.radius,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 16,
    color: colors.text,
  },
  choiceRow: {flexDirection: 'row', flexWrap: 'wrap', gap: 8},
  choice: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: layout.radius,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  choiceSelected: {borderColor: colors.primary, backgroundColor: '#EFF6FF'},
  choiceText: {color: colors.text, fontSize: 14},
  choiceTextSelected: {color: colors.primary, fontWeight: '600'},
  toggleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  line: {fontSize: 15, color: colors.text, marginBottom: 6},
  toggleValue: {fontWeight: '700', color: colors.primary},
  dropdownButton: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: layout.radius,
    paddingHorizontal: 12,
    paddingVertical: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
  dropdownButtonText: {fontSize: 16, color: colors.text},
  dropdownIcon: {fontSize: 12, color: colors.muted},
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'center',
    padding: 20,
  },
  modalContent: {
    backgroundColor: '#fff',
    borderRadius: layout.radius,
    maxHeight: '80%',
    padding: 16,
  },
  modalTitle: {fontSize: 18, fontWeight: '600', color: colors.text, marginBottom: 16},
  modalItem: {
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  modalItemSelected: {
    backgroundColor: '#eff6ff',
    borderRadius: 8,
    paddingHorizontal: 8,
  },
  modalItemText: {fontSize: 16, color: colors.text},
  modalItemTextSelected: {color: colors.primary, fontWeight: '600'},
});
