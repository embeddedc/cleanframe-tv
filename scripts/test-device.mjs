// Unit checks for app/device.js (the Android adapter) without a TV.
import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

// Values created inside the vm realm have foreign prototypes; compare them as plain JSON.
const plain = (x) => JSON.parse(JSON.stringify(x));
const source = fs.readFileSync(new URL('../app/device.js', import.meta.url), 'utf8');

function load(bridge) {
  const window = { location: { hostname: 'appassets.androidplatform.net' } };
  if (bridge) window.CleanFrameAndroid = bridge;
  vm.runInNewContext(source, { window, JSON, Object, Array, String, RegExp, setTimeout, clearTimeout });
  return window.FrameDevice;
}

function fakeBridge(apps, extra = {}) {
  const calls = [];
  return Object.assign({
    calls,
    appsJson: () => JSON.stringify(apps),
    launch: (id) => { calls.push(['launch', id]); return id === 'broken.app' ? 'Not installed' : ''; },
    closeApp: (id) => { calls.push(['close', id]); return id.indexOf('input.') === 0 ? 'Inputs can’t be closed.' : ''; },
    openSettings: () => { calls.push(['settings']); return ''; },
    openPreviousLauncher: () => { calls.push(['previous']); return ''; },
    is24Hour: () => true,
    photosJson: () => '[]',
    refreshPhotos: () => { calls.push(['refresh']); },
    takeConfig: () => ''
  }, extra);
}

const installed = [
  { appId: 'com.google.android.youtube.tv', name: 'YouTube', kind: 'app', icon: '/icons/com.google.android.youtube.tv.png' },
  { appId: 'org.smarttube.stable', name: 'SmartTube', kind: 'app', icon: '/icons/org.smarttube.stable.png' },
  { appId: 'com.amazon.amazonvideo.livingroom', name: 'Prime Video', kind: 'app', icon: '/icons/a.png' },
  { appId: 'it.rainet.androidtv', name: 'RaiPlay', kind: 'app', icon: '/icons/it.rainet.androidtv.png' },
  { appId: 'input.hw5', name: 'Nintendo Switch', kind: 'input' },
  { appId: 'input.hw6', name: 'PC', kind: 'input' }
];

// Brands follow BRANDS order and pick the first matching package (SmartTube before YouTube).
let dev = load(fakeBridge(installed));
const primary = dev.primaryApps();
assert.deepEqual(plain(primary.map((a) => a.id)), ['primevideo', 'youtube', 'nintendoswitch']);
assert.equal(primary.find((a) => a.id === 'youtube').appId, 'org.smarttube.stable');
assert.equal(primary.find((a) => a.id === 'youtube').name, 'YouTube');
assert.equal(primary.find((a) => a.id === 'nintendoswitch').appId, 'input.hw5');
assert.ok(primary.every((a) => a.mark === true));

// apps(): every entry, brand only on the chosen package, native icons kept for unbranded apps.
let listed;
dev.apps((err, list) => { assert.equal(err, null); listed = list; });
assert.equal(listed.length, 6);
assert.equal(listed.find((a) => a.appId === 'org.smarttube.stable').brand, 'youtube');
assert.equal(listed.find((a) => a.appId === 'com.google.android.youtube.tv').brand, '');
assert.equal(listed.find((a) => a.appId === 'it.rainet.androidtv').icon, '/icons/it.rainet.androidtv.png');
assert.equal(listed.find((a) => a.appId === 'input.hw6').brand, '');

// Launch results become errors with a message.
const bridge = fakeBridge(installed);
dev = load(bridge);
let result = 'unset';
dev.launchApp('org.smarttube.stable', (err) => { result = err; });
assert.equal(result, null);
dev.launchApp('broken.app', (err) => { result = err; });
assert.equal(result.message, 'Not installed');
dev.settings((err) => { result = err; });
assert.equal(result, null);
dev.closeApp('com.amazon.amazonvideo.livingroom', (err) => { result = err; });
assert.equal(result, null);
dev.closeApp('input.hw5', (err) => { result = err; });
assert.match(result.message, /Inputs/);
assert.deepEqual(plain(bridge.calls), [['launch', 'org.smarttube.stable'], ['launch', 'broken.app'], ['settings'], ['close', 'com.amazon.amazonvideo.livingroom'], ['close', 'input.hw5']]);
assert.equal(dev.is24Hour(), true);

// Config is handed over once as an object; malformed config is ignored.
dev = load(fakeBridge(installed, { takeConfig: () => '{"home":["primevideo"],"clock":"24"}' }));
assert.deepEqual(plain(dev.takeConfig()), { home: ['primevideo'], clock: '24' });
dev = load(fakeBridge(installed, { takeConfig: () => '{oops' }));
assert.equal(dev.takeConfig(), null);

// Browser preview: no bridge -> deterministic mock catalogue and two mock Bing photos.
dev = load(null);
dev.apps((err, list) => { assert.equal(err, null); listed = list; });
assert.equal(listed.length, 10);
assert.ok(listed.some((a) => a.appId.indexOf('input.') === 0));
assert.equal(dev.photos().length, 2);
dev.launchApp('org.smarttube.stable', (err) => { result = err; });
assert.match(result.message, /TV/);
dev.closeApp('org.smarttube.stable', (err) => { result = err; });
assert.match(result.message, /Preview/);

console.log('device.js adapter checks passed.');

// No known brand installed: Home starts with the first installed apps (not inputs), unmarked.
dev = load(fakeBridge([
  { appId: 'it.rainet.androidtv', name: 'RaiPlay', kind: 'app', icon: '/icons/r.png' },
  { appId: 'input.hw4', name: 'HDMI 1', kind: 'input' }
]));
assert.deepEqual(plain(dev.primaryApps()), [{ id: 'it.rainet.androidtv', name: 'RaiPlay', mark: false, appId: 'it.rainet.androidtv', icon: '/icons/r.png' }]);
assert.ok(plain(dev.brandIds()).indexOf('jellyfin') >= 0);
console.log('device.js fallback checks passed.');
