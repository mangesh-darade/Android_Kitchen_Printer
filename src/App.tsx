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
    return null;
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
