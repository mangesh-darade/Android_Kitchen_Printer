import React from 'react';
import {
  requireNativeComponent,
  NativeSyntheticEvent,
  ViewStyle,
  StyleSheet,
} from 'react-native';

type ProgressEvent = NativeSyntheticEvent<{progress: number}>;
type MessageEvent = NativeSyntheticEvent<{message: string}>;

type NativeProps = {
  url: string;
  style?: ViewStyle;
  onLoadProgress?: (event: ProgressEvent) => void;
  onLoadEnd?: (event: MessageEvent) => void;
  onError?: (event: MessageEvent) => void;
  onTitleReceived?: (event: MessageEvent) => void;
};

const NativePOSWebView =
  requireNativeComponent<NativeProps>('POSWebView');

type Props = {
  url: string;
  onError?: (message: string) => void;
  onLoadProgress?: (progress: number) => void;
  onTitleReceived?: (title: string) => void;
};

export function POSWebViewNative({url, onError, onLoadProgress, onTitleReceived}: Props) {
  return (
    <NativePOSWebView
      style={styles.webview}
      url={url}
      onLoadProgress={e => onLoadProgress?.(e.nativeEvent.progress)}
      onLoadEnd={() => {}}
      onError={e => onError?.(e.nativeEvent.message || 'Load failed')}
      onTitleReceived={e => onTitleReceived?.(e.nativeEvent.message)}
    />
  );
}

const styles = StyleSheet.create({
  webview: {
    flex: 1,
  },
});
