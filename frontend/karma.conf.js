// CI runners execute as root, where Chrome refuses to start without
// --no-sandbox, so this config exists to define a launcher that works there.
//
// frameworks/plugins are NOT optional here. Supplying a karmaConfig to the
// @angular/build:karma builder REPLACES the builder's own defaults rather than
// merging with them, so omitting these leaves Jasmine unloaded and every spec
// dies with "describe is not defined" - verified on the deployment host.
module.exports = function (config) {
  config.set({
    frameworks: ['jasmine'],
    // karma-coverage is registered because the builder appends a 'coverage'
    // reporter when --code-coverage is passed; without the plugin that flag
    // fails instead of producing a report.
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-coverage'),
    ],
    reporters: ['progress'],
    browsers: ['ChromeHeadlessNoSandbox'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        // --disable-dev-shm-usage matches the builder's own built-in launcher:
        // containers default to a 64 MB /dev/shm and Chrome crashes once the
        // suite is large enough to exhaust it.
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
  });
};
