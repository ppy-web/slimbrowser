# SlimBrowser implementation plan

## 1. Scope and fixed decisions

The application is a single-activity, single-WebView shell for a user-configured HTTPS home URL. It uses Kotlin source, XML layouts, ViewBinding, Android System WebView through AndroidX WebKit compatibility APIs, Preferences DataStore, and MVP separation. The fixed build matrix is minSdk 26, compileSdk/targetSdk 37, AGP 9.3.0 built-in Kotlin, Gradle 9.5.0, and Java 17 bytecode compatibility.

No `org.jetbrains.kotlin.android` plugin is applied. AGP supplies built-in Kotlin. `compileOptions` sets Java 17 and built-in Kotlin inherits that JVM target.

## 2. Project structure

- Root Gradle files select AGP 9.3.0, repositories, AndroidX flags, and Gradle 9.5.0 distribution.
- `app/src/main/AndroidManifest.xml` declares only Internet access, rejects cleartext traffic, disables backup, and exports only the launcher activity.
- `activity_main.xml` holds a replaceable WebView container, native error overlay, and persistent floating action button.
- `dialog_settings.xml` binds the home URL, fullscreen, theme, and custom background preferences.
- `BrowserContract` separates platform rendering from decisions.
- `BrowserPresenter` owns initialization, settings changes, fullscreen state, refresh, retry, and back-navigation decisions.
- `BrowserPreferences` is the single source of truth for `home_url`, `fullscreen_enabled`, `dark_theme_enabled`, and `background_uri`.
- `UrlPolicy` is pure Kotlin/JVM code and defines the URL trust boundary.

## 3. Startup sequence

1. Inflate ViewBinding and install the WebView security configuration before loading content.
2. Construct the presenter with the application-context DataStore repository and Activity lifecycle scope.
3. Read the first DataStore snapshot.
4. Apply the persisted fullscreen state through `WindowInsetsControllerCompat` and synchronize the fullscreen button representation.
5. Restore the WebView bundle when available.
6. If restoration is unavailable and no home URL is stored, present a non-cancelable settings dialog.
7. Otherwise load the normalized persisted HTTPS URL.

## 4. URL policy

Input is trimmed, rejected if blank or containing control characters, and receives an `https://` scheme when absent. Parsing uses `java.net.URI`. A URL is accepted only when:

- Scheme is HTTPS, case-insensitively.
- Host is present and syntactically valid; Unicode host names are converted with IDN STD3 rules.
- User-info/embedded credentials are absent.
- An explicit port is either omitted or 443.
- Localhost and single-label DNS names are rejected. Valid IPv4/IPv6 literals remain usable for HTTPS development/test endpoints.

The policy is applied to saved settings and every main-frame WebView navigation. Non-main-frame resources are still subject to WebView mixed-content and cleartext controls.

## 5. WebView hardening

The WebView configuration:

- Enables JavaScript and DOM storage for modern web applications.
- Disables file access, content access, database storage, geolocation, multiple windows, automatic JavaScript-created windows, and WebView debugging.
- Denies mixed content and third-party cookies.
- Requires a user gesture for media playback.
- Enables Safe Browsing through AndroidX WebKit feature detection.
- Denies all Web permission requests.
- Cancels every TLS error.
- Does not expose `addJavascriptInterface`.

This does not turn arbitrary web content into trusted application code. Production deployments should add an explicit hostname allowlist if the application is intended for one controlled origin.

## 6. Fullscreen interaction

`fullscreen_enabled` is stored in Preferences DataStore. The settings `MaterialSwitch` and dedicated fullscreen button both write the same key through the presenter/repository. The fullscreen button remains available even in immersive mode to guarantee an exit path, while a separate settings button opens the dialog. Immersive mode hides status/navigation bars and allows transient reveal by edge swipe.

The settings dialog also supports a persisted custom background image, restoring the generated theme background, and clearing cookies, cache, history, and form data after confirmation.

## 6.2 Navigation, refresh, downloads, and uploads

Main-frame HTTPS navigation is limited to the configured home host and its subdomains on the same effective port. HTTP, file, data, JavaScript, and other unsupported schemes are blocked. `mailto:`, `tel:`, and `sms:` links use external applications; `intent:` links are parsed only for safe view/send/dial actions. Redirects are checked again in `onPageStarted`.

The app supports pull-to-refresh and a refresh floating button. WebView progress is shown as a progress bar and an accessible status label. Web downloads are handed to Android `DownloadManager` only for the configured HTTPS site. HTML file uploads use the system document picker and do not grant camera or microphone access.

## 6.1 Interface language, theme, and controls

All app interface text uses Chinese by default, including on devices whose system locale is English, and the settings dialog does not expose a language switch. A global light/dark theme switch persists through Preferences DataStore and applies the corresponding Material night mode and generated wallpaper. The bottom-centered action row is shown by default and remains available.

## 6.3 Favorites and blank home search

Favorites are persisted in Preferences DataStore as title/URL records. The settings dialog can bookmark the current page, open the bookmark list, open a saved page, or delete it. If the home URL is empty, the app shows saved sites as a four-column grid of circular shortcuts on a transparent background, without headings or helper text; submitted search queries are URL-encoded and opened through Baidu search.

## 7. Error and renderer recovery

Main-frame `onReceivedError`, HTTP status failures, TLS failures, and Safe Browsing hits display a native overlay above the WebView. Retry reloads the current page or the configured home URL. Subresource failures do not replace the whole screen.

When `onRenderProcessGone` fires, the dead WebView is removed and destroyed, a new WebView is inserted into the same container and hardened again, and the native overlay offers retry. Returning `true` prevents the Activity from crashing due to the dead renderer object.

## 8. Navigation and state

The Android back dispatcher first checks `WebView.canGoBack()` and traverses page history. Only an empty WebView history finishes the Activity. `WebView.saveState` is placed in an Activity state bundle and `restoreState` is attempted before loading the home URL, preserving current page and history across Activity recreation.

The manifest declares relevant configuration changes so ordinary rotations do not force unnecessary WebView destruction; state saving remains implemented for process death and other recreation paths.

## 9. Test strategy

### JVM tests

`UrlPolicyTest` validates normalization and rejection without an Android runtime. Cases include omitted scheme, mixed case, path/query/fragment preservation, IDN, explicit 443, insecure/dangerous schemes, credentials, malformed hosts, disallowed ports, blank strings, and control characters.

### Build checks

Run:

```text
gradlew.bat testDebugUnitTest
gradlew.bat lintDebug
gradlew.bat assembleDebug
```

Treat compilation warnings from deprecated platform WebSettings properties separately from errors; the minimum API requires several legacy setters, while the security posture is intentional.

### Device matrix

Test at minimum on API 26 and API 37, using current Android System WebView/Chrome releases. Include phone portrait/landscape and a device with gesture navigation/display cutout.

### Manual scenarios

- Fresh install and mandatory setup.
- Valid/invalid URL matrix.
- Persistence across force-stop/relaunch.
- Fullscreen transition from dialog and fullscreen button, including escape from immersive mode.
- Internal link navigation and back history.
- Activity/process recreation state recovery.
- DNS, offline, HTTP 4xx/5xx, invalid certificate, Safe Browsing, and retry behavior.
- Renderer termination and replacement.
- Confirmation that camera/microphone/geolocation prompts are denied and non-HTTPS navigation is blocked.
- Default-visible, bottom-centered action row.
- Chinese interface and light/dark theme persistence, including wallpaper changes.
- Same-site navigation, external link handling, download, upload, refresh, and loading-state behavior.
- Custom background persistence, data clearing, and process/configuration state recovery.
- TalkBack labels/live regions and system-locale resource selection without an in-app language switch.

## 10. Acceptance criteria

1. Project syncs and builds with the fixed toolchain and no external Kotlin Android plugin.
2. ViewBinding-generated classes are used; there is no synthetic view access or Compose.
3. First launch may use an empty home URL, which shows saved sites and the bottom search action; non-empty home URLs must be valid HTTPS URLs.
4. The URL and fullscreen setting survive app restarts.
5. Settings and fullscreen button remain synchronized through `fullscreen_enabled`.
6. Cleartext and non-HTTPS main-frame navigations are rejected.
7. TLS errors never proceed, mixed content is denied, and no privileged JavaScript bridge exists.
8. Native error UI covers main-frame failures and retries.
9. Back navigates browser history before exiting.
10. WebView state restores after recreation.
11. Renderer death does not crash the app and a fresh WebView can retry.
12. JVM policy tests, lint, and debug assembly pass on a configured machine.
13. All app interface text uses Chinese, and no language-switch control is shown.
14. Light/dark theme selection persists and applies the matching wallpaper.
15. Settings and fullscreen controls hide after inactivity and reappear on page interaction.
16. Navigation, download, upload, refresh, custom background, data clearing, favorites, blank-home search, accessibility, and system-language resource scenarios pass on supported devices.

## 11. Local verification

The project has been verified locally with the Android Studio JDK 17 runtime. `testDebugUnitTest`, `lintDebug`, and `assembleDebug` all pass; the resulting debug APK was installed on an API 37 emulator for smoke testing of the blank home, search dialog, settings, and favorites entry points.
