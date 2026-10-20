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
  var CATALOG_CACHE = 'cleanframe.catalog.v1';
  function valid(list) {
    return Array.isArray(list) ? list.filter(function (a) { return a && typeof a.appId === 'string' && typeof a.name === 'string'; }) : null;
  }
  /* The installed apps and inputs. Throws when the bridge fails; a good answer is kept for cachedInstalled(). */
  function installed() {
    var b = bridge(), list;
    if (!b) return valid(MOCK_APPS);
    list = valid(parse(b.appsJson(), null));
    if (!list) throw new Error('Unreadable app catalogue');
    try { window.localStorage.setItem(CATALOG_CACHE, JSON.stringify(list)); } catch (e) {}
    return list;
  }
  /* Startup must not fail: fall back to the last catalogue that loaded, so Home keeps its apps. */
  function cachedInstalled() {
    try { return installed(); } catch (e) {
      try { return valid(parse(window.localStorage.getItem(CATALOG_CACHE), null)) || []; } catch (e2) { return []; }
    }
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
    var list = cachedInstalled(), chosen = brandChoices(list), out;
    out = BRANDS.filter(function (brand) { return chosen[brand.id]; }).map(function (brand) {
      return { id: brand.id, name: brand.name, mark: true, appId: chosen[brand.id] };
    });
    /* No known brand installed: start Home with the first few installed apps instead of an empty list. */
    if (!out.length) out = list.filter(function (a) { return a.kind !== 'input'; }).slice(0, 5).map(function (a) {
      return { id: a.appId, name: a.name, mark: false, appId: a.appId, icon: typeof a.icon === 'string' ? a.icon : '' };
    });
    return out;
  }
  function brandIds() { return BRANDS.map(function (brand) { return brand.id; }); }
  function apps(done) {
    var list, chosen, brandOf = {};
    /* A Java exception in the bridge arrives as a thrown Error; report it so All apps offers a retry. */
    try { list = installed(); } catch (e) { done({ message: 'Could not read installed apps.' }); return; }
    chosen = brandChoices(list);
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
  /* Ends a backgrounded app's processes; the TV keeps HDMI inputs as they are. */
  function closeApp(id, done) {
    var b = bridge();
    if (!b || !b.closeApp) { result('Preview: closing apps needs the TV.', done); return; }
    try { result(b.closeApp(String(id)), done); } catch (e) { result('The TV did not respond. Please try again.', done); }
  }
  function launch(brand, done) {
    var chosen = brandChoices(cachedInstalled());
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
  /* Tells Android which photo source is in use (only changes are sent); it contacts Bing only for 'bing'. */
  var sentSource = null;
  function photoSource(source) {
    var b = bridge();
    if (!b || !b.setPhotoSource || source === sentSource) return;
    try { b.setPhotoSource(String(source)); sentSource = source; } catch (e) {}
  }
  function refreshPhotos() { var b = bridge(); if (b) try { b.refreshPhotos(); } catch (e) {} }
  function takeConfig() {
    var b = bridge(), value;
    if (!b) return null;
    try { value = parse(b.takeConfig(), null); } catch (e) { value = null; }
    return value && typeof value === 'object' && !Array.isArray(value) ? value : null;
  }

  window.FrameDevice = {
    primaryApps: primaryApps, brandIds: brandIds, apps: apps, launch: launch, launchApp: launchApp, closeApp: closeApp, settings: settings,
    previousLauncher: previousLauncher, is24Hour: is24Hour, photos: photos, refreshPhotos: refreshPhotos, photoSource: photoSource,
    takeConfig: takeConfig, android: !!bridge()
  };
})();
