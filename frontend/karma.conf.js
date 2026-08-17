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
    plugins: [require('karma-jasmine'), require('karma-chrome-launcher')],
    reporters: ['progress'],
    browsers: ['ChromeHeadlessNoSandbox'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu'],
      },
    },
  });
};
