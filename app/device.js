/* Android TV adapter. Talks to the launcher shell through the synchronous window.CleanFrameAndroid bridge. */
(function () {
  'use strict';
  /* Brand order is the default Home order. Each brand lists the Android packages that open it, best first. */
  var BRANDS = [
    { id: 'primevideo', name: 'Prime Video', packages: ['com.amazon.amazonvideo.livingroom', 'com.amazon.avod.thirdpartyclient'] },
    { id: 'youtube', name: 'YouTube', packages: ['org.smarttube.stable', 'org.smarttube.beta', 'com.teamsmart.videomanager.tv', 'com.google.android.youtube.tv'] },
    { id: 'netflix', name: 'Netflix', packages: ['com.netflix.ninja'] },
    { id: 'disneyplus', name: 'Disney+', packages: ['com.disney.disneyplus'] },
    { id: 'jellyfin', name: 'Jellyfin', packages: ['org.jellyfin.androidtv', 'com.github.damontecres.wholphin'] },
    { id: 'twitch', name: 'Twitch', packages: ['com.s0und.s0undtv', 'tv.twitch.android.app'] },
    { id: 'appletv', name: 'Apple TV', packages: ['com.apple.atve.androidtv.appletv'] },
    { id: 'stremio', name: 'Stremio', packages: ['com.stremio.one'] },
    { id: 'spotify', name: 'Spotify', packages: ['com.spotify.tv.android'] },
    { id: 'hbomax', name: 'HBO Max', packages: ['com.wbd.stream', 'com.hbo.hbonow'] },
    { id: 'tubi', name: 'Tubi', packages: ['com.tubitv'] },
    { id: 'nintendoswitch', name: 'Nintendo Switch', input: /switch/i }
  ];
  var PREVIEW = 'Preview: opening apps needs the TV.';
  var MOCK_APPS = [
    { appId: 'com.amazon.amazonvideo.livingroom', name: 'Prime Video', kind: 'app' },
    { appId: 'org.smarttube.stable', name: 'SmartTube', kind: 'app' },
    { appId: 'com.netflix.ninja', name: 'Netflix', kind: 'app' },
    { appId: 'org.jellyfin.androidtv', name: 'Jellyfin', kind: 'app' },
    { appId: 'com.s0und.s0undtv', name: 's0undTV', kind: 'app' },
    { appId: 'it.rainet.androidtv', name: 'RaiPlay', kind: 'app' },
    { appId: 'it.mediaset.infinitytv', name: 'Mediaset Infinity', kind: 'app' },
    { appId: 'com.android.tv.settings', name: 'Settings', kind: 'app' },
    { appId: 'input.hw5', name: 'Nintendo Switch', kind: 'input' },
    { appId: 'input.hw6', name: 'PC', kind: 'input' }
  ];
  var MOCK_PHOTOS = [
    { id: 'bing-mock-1', file: 'summit', base: 'assets/', name: 'Mock Bing photo', caption: 'Preview photo, © Example', tone: 'dark', ut: { tr: 'mist', br: 'light' }, frostVeil: 0.12 },
    { id: 'bing-mock-2', file: 'cove', base: 'assets/', name: 'Second mock photo', caption: 'Preview photo 2, © Example', tone: 'light', ut: { tr: 'light', br: 'mist' }, frostVeil: 0.12 }
  ];

  function bridge() { return window.CleanFrameAndroid || null; }
  function parse(text, fallback) {
    if (typeof text !== 'string' || !text) return fallback;
    try { return JSON.parse(text); } catch (e) { return fallback; }
  }
  function installed() {
    var b = bridge(), list = b ? parse(b.appsJson(), []) : MOCK_APPS;
    return Array.isArray(list) ? list.filter(function (a) { return a && typeof a.appId === 'string' && typeof a.name === 'string'; }) : [];
  }
  /* brandId -> chosen appId, choosing the first listed package (or input name match) that is installed. */
  function brandChoices(list) {
    var byId = {}, chosen = {};
    list.forEach(function (a) { byId[a.appId] = a; });
    BRANDS.forEach(function (brand) {
      var i;
      if (brand.input) {
        for (i = 0; i < list.length; i++) if (list[i].kind === 'input' && brand.input.test(list[i].name)) { chosen[brand.id] = list[i].appId; return; }
        return;
      }
      for (i = 0; i < brand.packages.length; i++) if (byId[brand.packages[i]]) { chosen[brand.id] = brand.packages[i]; return; }
    });
    return chosen;
  }
  function result(error, done) { if (done) done(error ? { message: String(error) } : null); }
  function call(method, done) {
    var b = bridge();
    if (!b) { result(PREVIEW, done); return; }
    try { result(b[method](), done); } catch (e) { result('The TV did not respond. Please try again.', done); }
  }

  function primaryApps() {
    var list = installed(), chosen = brandChoices(list);
    return BRANDS.filter(function (brand) { return chosen[brand.id]; }).map(function (brand) {
      return { id: brand.id, name: brand.name, mark: true, appId: chosen[brand.id] };
    });
  }
  function apps(done) {
    var list = installed(), chosen = brandChoices(list), brandOf = {};
    Object.keys(chosen).forEach(function (id) { brandOf[chosen[id]] = id; });
    done(null, list.map(function (a) {
      return { appId: a.appId, name: a.name, brand: brandOf[a.appId] || '', icon: typeof a.icon === 'string' ? a.icon : '', kind: a.kind === 'input' ? 'input' : 'app' };
    }));
  }
  function launchApp(id, done) {
    var b = bridge();
    if (!b) { result(PREVIEW, done); return; }
    try { result(b.launch(String(id)), done); } catch (e) { result('The TV did not respond. Please try again.', done); }
  }
  function launch(brand, done) {
    var chosen = brandChoices(installed());
    if (!chosen[brand]) { result('This app is not installed.', done); return; }
    launchApp(chosen[brand], done);
  }
  function settings(done) { call('openSettings', done); }
  function previousLauncher(done) { call('openPreviousLauncher', done); }
  function is24Hour() { var b = bridge(); try { return b ? !!b.is24Hour() : true; } catch (e) { return true; } }
  function photos() {
    var b = bridge(), list = b ? parse(b.photosJson(), []) : MOCK_PHOTOS;
    return Array.isArray(list) ? list.filter(function (p) { return p && typeof p.id === 'string' && typeof p.base === 'string'; }) : [];
  }
  function refreshPhotos() { var b = bridge(); if (b) try { b.refreshPhotos(); } catch (e) {} }
  function takeConfig() {
    var b = bridge(), value;
    if (!b) return null;
    try { value = parse(b.takeConfig(), null); } catch (e) { value = null; }
    return value && typeof value === 'object' && !Array.isArray(value) ? value : null;
  }

  window.FrameDevice = {
    primaryApps: primaryApps, apps: apps, launch: launch, launchApp: launchApp, settings: settings,
    previousLauncher: previousLauncher, is24Hour: is24Hour, photos: photos, refreshPhotos: refreshPhotos,
    takeConfig: takeConfig, android: !!bridge()
  };
})();
