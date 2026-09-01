import React, {forwardRef, useImperativeHandle, useRef} from 'react';
import {
  requireNativeComponent,
  NativeSyntheticEvent,
  ViewStyle,
  StyleSheet,
  UIManager,
  findNodeHandle,
} from 'react-native';

export interface POSWebViewRef {
  reload: () => void;
  goBack: () => void;
}

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

export const POSWebViewNative = forwardRef<POSWebViewRef, Props>(
  ({url, onError, onLoadProgress, onTitleReceived}, ref) => {
    const nativeRef = useRef<any>(null);

    useImperativeHandle(ref, () => ({
      reload() {
        const handle = findNodeHandle(nativeRef.current);
        if (handle) {
          UIManager.dispatchViewManagerCommand(handle, 1, []);
        }
      },
      goBack() {
        const handle = findNodeHandle(nativeRef.current);
        if (handle) {
          UIManager.dispatchViewManagerCommand(handle, 2, []);
        }
      },
    }));

    return (
      <NativePOSWebView
        ref={nativeRef}
        style={styles.webview}
        url={url}
        onLoadProgress={e => onLoadProgress?.(e.nativeEvent.progress)}
        onLoadEnd={() => {}}
        onError={e => onError?.(e.nativeEvent.message || 'Load failed')}
        onTitleReceived={e => onTitleReceived?.(e.nativeEvent.message)}
      />
    );
  }
);

const styles = StyleSheet.create({
  webview: {
    flex: 1,
  },
});
