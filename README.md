# NitronBox

Premium connection onboarding rebuilt from scratch with React Native and TypeScript.

## Stack

- Expo / React Native
- TypeScript
- React Native Reanimated
- Lucide React Native
- Supabase-ready realtime source abstraction

## Run

```bash
npm install
npx expo start
```

## Android

```bash
npm ci
npx expo prebuild --clean --platform android --no-install
cd android
./gradlew assembleRelease
```

The standalone APK is written to:

```text
android/app/build/outputs/apk/release/app-release.apk
```

This release variant embeds `assets/index.android.bundle` and all Metro assets. It does not need
Metro, USB debugging, `adb reverse`, Wi-Fi access to the development machine, or a background
terminal process.

To build and install the same release variant directly through Expo tooling:

```bash
npx expo run:android --variant release
```
