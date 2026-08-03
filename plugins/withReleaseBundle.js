const { withAppBuildGradle } = require('expo/config-plugins');

/**
 * Keeps release bundling explicit across `expo prebuild --clean` runs.
 * React Native 0.81 bundles every variant not listed in debuggableVariants.
 */
module.exports = function withReleaseBundle(config) {
  return withAppBuildGradle(config, (config) => {
    if (config.modResults.language !== 'groovy') {
      throw new Error('NitronBox release bundle plugin expects Groovy build.gradle');
    }

    const marker = 'react {';
    const releaseConfig = [
      marker,
      '    // Only debug may use Metro. Release always embeds index.android.bundle.',
      '    debuggableVariants = ["debug"]',
      '    bundleAssetName = "index.android.bundle"',
    ].join('\n');

    if (!config.modResults.contents.includes('bundleAssetName = "index.android.bundle"')) {
      config.modResults.contents = config.modResults.contents.replace(marker, releaseConfig);
    }
    return config;
  });
};
