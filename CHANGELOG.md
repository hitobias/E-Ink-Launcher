# Changelog

All notable changes to this fork.

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
