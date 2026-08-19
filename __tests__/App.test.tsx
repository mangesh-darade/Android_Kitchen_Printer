/**
 * @format
 */

import React from 'react';
import {it} from '@jest/globals';
import renderer from 'react-test-renderer';

jest.mock('../src/native/posConnect', () => ({
  loadConfig: async () => ({
    setupCompleted: false,
    division: {url: ''},
  }),
  startStarPrintListener: () => () => undefined,
}));

jest.mock('@react-navigation/native', () => ({
  NavigationContainer: ({children}: {children: React.ReactNode}) => children,
}));

jest.mock('@react-navigation/native-stack', () => ({
  createNativeStackNavigator: () => ({
    Navigator: ({children}: {children: React.ReactNode}) => children,
    Screen: () => null,
  }),
}));

jest.mock('../src/screens/PrinterTestScreen', () => ({
  PrinterTestScreen: () => null,
}));

import App from '../src/App';

it('renders correctly', () => {
  renderer.create(<App />);
});
