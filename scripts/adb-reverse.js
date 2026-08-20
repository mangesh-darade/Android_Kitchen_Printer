/**
 * Forward host Metro (8081) to emulator/device so debug builds can load JS.
 * Reads ANDROID_HOME or android/local.properties for adb path.
 */
const {execSync} = require('child_process');
const fs = require('fs');
const path = require('path');

function getAdbPath() {
  const envSdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  if (envSdk) {
    const adb = path.join(
      envSdk,
      'platform-tools',
      process.platform === 'win32' ? 'adb.exe' : 'adb',
    );
    if (fs.existsSync(adb)) {
      return adb;
    }
  }

  const localProps = path.join(__dirname, '..', 'android', 'local.properties');
  if (fs.existsSync(localProps)) {
    const match = fs.readFileSync(localProps, 'utf8').match(/sdk\.dir=(.+)/);
    if (match) {
      const sdk = match[1].trim().replace(/\\/g, path.sep);
      const adb = path.join(
        sdk,
        'platform-tools',
        process.platform === 'win32' ? 'adb.exe' : 'adb',
      );
      if (fs.existsSync(adb)) {
        return adb;
      }
    }
  }

  return process.platform === 'win32' ? 'adb.exe' : 'adb';
}

try {
  const adb = getAdbPath();
  execSync(`"${adb}" reverse tcp:8081 tcp:8081`, {stdio: 'inherit'});
  console.log('Metro port 8081 forwarded to emulator/device.');
} catch (error) {
  console.warn(
    'adb reverse skipped — start emulator or connect phone, then run: npm run adb:reverse',
  );
  if (process.env.ADB_REVERSE_STRICT === '1') {
    process.exit(1);
  }
}
