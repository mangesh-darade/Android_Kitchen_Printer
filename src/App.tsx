import React, {useEffect, useState} from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {WelcomeScreen} from './screens/WelcomeScreen';
import {SetupScreen} from './screens/SetupScreen';
import {PosSessionScreen} from './screens/PosSessionScreen';
import {SettingsScreen} from './screens/SettingsScreen';
import {PrinterTestScreen} from './screens/PrinterTestScreen';
import {loadConfig, startStarPrintListener} from './native/posConnect';

import {View, Text, ActivityIndicator, TouchableOpacity, Linking} from 'react-native';
import {APP_NAME, ELINTOM_URL} from './core/app-identity';

export type RootStackParamList = {
  Welcome: undefined;
  Setup: undefined;
  PosSession: undefined;
  Settings: undefined;
  PrinterTest: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

function AppNavigator() {
  const [initialRoute, setInitialRoute] = useState<keyof RootStackParamList | null>(null);

  useEffect(() => {
    loadConfig().then(cfg => {
      setInitialRoute(cfg.setupCompleted && cfg.division.url ? 'PosSession' : 'Welcome');
    });
    return startStarPrintListener();
  }, []);

  if (!initialRoute) {
    return (
      <View style={{flex: 1, backgroundColor: '#0F172A', justifyContent: 'center', alignItems: 'center'}}>
        <ActivityIndicator size="large" color="#3B82F6" />
        <Text style={{color: '#FFFFFF', fontSize: 28, fontWeight: '700', marginTop: 16}}>{APP_NAME}</Text>
        <TouchableOpacity
          activeOpacity={0.7}
          style={{position: 'absolute', bottom: 32}}
          onPress={() => Linking.openURL(ELINTOM_URL)}>
          <Text style={{color: '#94A3B8', fontSize: 13}}>
            Powered by <Text style={{fontWeight: '700', textDecorationLine: 'underline', color: '#60A5FA'}}>ElintOm</Text>
          </Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <Stack.Navigator initialRouteName={initialRoute} screenOptions={{headerShown: false}}>
      <Stack.Screen name="Welcome" component={WelcomeScreen} />
      <Stack.Screen name="Setup" component={SetupScreen} />
      <Stack.Screen name="PosSession" component={PosSessionScreen} />
      <Stack.Screen name="Settings" component={SettingsScreen} options={{headerShown: true, title: 'Settings'}} />
      <Stack.Screen name="PrinterTest" component={PrinterTestScreen} options={{headerShown: true, title: 'Star SDK'}} />
    </Stack.Navigator>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <NavigationContainer>
        <AppNavigator />
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
