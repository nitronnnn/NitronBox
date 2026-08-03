const { AndroidConfig, withAndroidManifest, withDangerousMod } = require('expo/config-plugins');
const fs = require('fs');
const path = require('path');

const vector = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#000000" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#72D5F4" android:pathData="M17,78 L45,22 Q49,14 55,25 L64,42 L46,78 L23,88 Q12,93 17,78 Z" />
    <path android:fillColor="#DDF8FF" android:pathData="M91,30 L63,86 Q59,94 53,83 L44,66 L62,30 L85,20 Q96,15 91,30 Z" />
</vector>`;

module.exports = function withNitronIcon(config) {
  config = withDangerousMod(config, [
    'android',
    async (config) => {
      const drawable = path.join(
        config.modRequest.platformProjectRoot,
        'app/src/main/res/drawable',
      );
      fs.mkdirSync(drawable, { recursive: true });
      fs.writeFileSync(path.join(drawable, 'ic_nitronbox.xml'), vector);
      return config;
    },
  ]);

  return withAndroidManifest(config, (config) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(config.modResults);
    app.$['android:icon'] = '@drawable/ic_nitronbox';
    app.$['android:roundIcon'] = '@drawable/ic_nitronbox';
    return config;
  });
};
