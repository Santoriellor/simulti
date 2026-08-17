// Minimal config so CI has a launcher that works as root. The Angular builder
// supplies everything else; only the browser definition is needed here.
module.exports = function (config) {
  config.set({
    browsers: ['ChromeHeadlessNoSandbox'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        // CI runners execute as root, where Chrome's sandbox refuses to start.
        flags: ['--no-sandbox', '--disable-gpu'],
      },
    },
  });
};
