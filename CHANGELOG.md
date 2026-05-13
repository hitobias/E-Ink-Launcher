# Changelog

All notable changes to this fork.

## [0.2.8] — 2026-05-13

### Removed (safety rollback — affects Supernote and likely other EMR devices)

The following features introduced in v0.2.4–v0.2.7 are removed because
they break stylus/touch input on Supernote (Chauvet OS):

- **Floating Home Button** (v0.2.4) — `SYSTEM_ALERT_WINDOW` overlay.
- **Right-Slider Redirect via AccessibilityService** (v0.2.5–v0.2.7).
  Binding any AccessibilityService on Supernote re-routes the touch
  pipeline and causes EMR handwriting to fail. Recovery on affected
  devices required boot-to-safe-mode + uninstall.
- **Redirect diagnostics** (v0.2.7) and the associated long-press surface.
- `SYSTEM_ALERT_WINDOW` permission entirely (was only used by Floating
  Home).
- All four locales' strings + the `launcher_redirect_accessibility.xml`
  service config.

Config migration (schema v1 → v2) wipes stale `launcherFloatingHome*`
and `launcherRedirectEnabled` prefs so upgrades land clean.

### Kept

- v0.2.2 P0 crash fix (EpdRefresh `ConcurrentHashMap.put(k, null)` NPE).
- Quick-Launch Dock.
- Version + build display in About.
- Everything from v0.2.0–v0.2.3.

If you need to return to E-Ink Launcher from a screen with no Home gesture
on Supernote, enable Settings → *Persistent return-to-launcher
notification* — pull down the notification shade and tap *Home*. This was
present from v0.2.0 onward and does not touch the input pipeline.

## [0.2.7] — 2026-05-13

### Added
- **Right-Slider Redirect diagnostics**: long-press the *Redirect Supernote
  Right-Slider* setting row to open a dialog showing live status —
  SupernoteLauncher install state, accessibility-service grant state, and
  the last 30 window-state events observed by the accessibility service.
  Lets users see whether the right-slider triggers any package at all on
  their specific device, and whether the package matches the watched name.

### Changed
- Accessibility service no longer restricts events to a single package
  name. It still only redirects for `com.ratta.supernote.launcher` matches,
  but now records all window-state changes into a 30-event ring buffer so
  the diagnostics dialog can reveal the actual package fired by the OEM
  gesture (which may differ between Supernote firmware versions).

## [0.2.6] — 2026-05-13

### Changed
- Right-Slider Redirect setup dialog now offers a one-tap *Open Supernote
  app* button that jumps directly to SupernoteLauncher's App Info page.
  Chauvet OS hides the standard Settings → Apps screen, so finding the
  Enable toggle by hand is non-obvious. Falls back to the full app list
  when the OEM doesn't allow opening details for disabled packages.

## [0.2.5] — 2026-05-13

### Added
- **Right-Slider Redirect (Supernote)**: an opt-in accessibility service
  that detects when the Supernote stock launcher comes to the foreground
  (via the hardwired right-slider home gesture) and immediately switches
  back to E-Ink Launcher. With this, the right-slider behaves as if it
  were rebindable to a custom HOME launcher.
  - Privacy-minded config: `canRetrieveWindowContent="false"`,
    `packageNames` pinned to `com.ratta.supernote.launcher`,
    `eventTypes` pinned to `typeWindowStateChanged`.
  - 300ms debounce prevents redirect loops.
  - Settings → *Redirect Supernote Right-Slider* with a setup dialog
    that walks the user through re-enabling the stock launcher and
    granting accessibility access.

## [0.2.4] — 2026-05-13

### Added
- **Floating Home Button**: optional system-wide overlay button that returns
  the user to the launcher from any screen. Built for devices like Supernote
  where the OEM hardcodes the home gesture to a non-replaceable component,
  leaving no way back when the original launcher is disabled.
  Settings → *Floating Home Button*. Drag to reposition, tap to go home.
  Requires the system *Display over other apps* permission.

## [0.2.3] — 2026-05-13

### Added
- Version + build number line in the About dialog (e.g. `v0.2.3-supernote (33)`).
  Lets users quote the exact build when reporting issues.

## [0.2.2] — 2026-05-12

### Fixed
- **Crash on launch (P0)**: `EpdRefresh` cached negative method lookups
  with `ConcurrentHashMap.put(key, null)`, which the JDK rejects. The first
  failed vendor SDK lookup (e.g. Onyx classes on Supernote) crashed the
  launcher during `setAdapter()`. Fix: store misses in a separate
  `ConcurrentHashMap.newKeySet()` so neither cache writes a null value.
- Regression test (`EpdRefreshTest`) covers the negative-cache path on
  vendor-less JVMs.

### Added
- **Quick-Launch Dock**: optional bottom row of up to 5 pinned apps,
  always visible across all pages. Long-press any app → *Pin to Dock*.
- Settings → *Show Quick-Launch Dock* toggle.
- `contentDescription` on the settings icon for TalkBack users.

## [0.2.1] — 2026-05-13

### Added
- Per-app rename via long-press menu; persists per package.
- Settings → Gesture Cheatsheet dialog listing every click / long-press / swipe.
- Bottom-bar page indicator dots (filled = current page).

## [0.2.0] — 2026-05-13

### Added
- Multi-flavor build: **generic** (any Android e-ink) and **supernote**
  (adds Chauvet OS internal-package filter).
- App search (long-press ⚙).
- App folders (single-cell composite icons, JSON persistence).
- Hidden Apps Manager dedicated screen.
- Settings export / import (SAF, JSON).
- Manifest app shortcuts: Lock / Search / Settings.
- Bluetooth quick-connect with last-used timestamps.
- Custom TTF font via SAF picker.
- Force language override (independent of system locale).
- Notification badges (NotificationListenerService).
- Auto periodic screen refresh.
- Pin recently-used apps to top.
- Return-to-launcher persistent notification toggle.
- E-ink vendor fast-refresh reflection (Onyx / Supernote / MIUI).
- Diagnostic info dialog (long-press About title).
- 36 Robolectric unit tests + GitHub Actions CI for both flavors.

### Changed
- AndroidX FragmentActivity migration; minSdk 14 → 21; Java 17 source.
- `Launcher.java` split into BatteryController / ClockController /
  DeviceAdminController / ShortcutDispatcher.
- IconCache uses LRU + async loader with recycle-safe guard.
- AppListCache for cold-start warm path.
- Adaptive icon + monochrome layer.
- Splash screen API; predictive back gesture.
- APK size: 573KB → 324KB (-44%).

### Removed
- FTP server feature (Apache ftpserver dep deleted; was 280KB).

### Fixed
- Hide-app from app-info dialog didn't persist.
- `Config.getHideApps` returned a mutable Set (now unmodifiable view).
- Race window in `Config.getFtpPassword` (FTP gone; pattern applied elsewhere).
- Foreground service type declared for Android 14+ compatibility.
- HomeEntranceService self-restart loop.

## [0.1.8.6]

Upstream release from Modificator before this fork started.
