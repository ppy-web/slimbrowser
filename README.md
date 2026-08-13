# SlimBrowser

SlimBrowser is a minimal single-site Android browser built with Kotlin, XML Views, ViewBinding, Android System WebView, AndroidX WebKit, Preferences DataStore, and a small MVP layer.

## Toolchain

- Package/application ID: `com.example.slimbrowser`
- Minimum SDK: 26
- Compile/target SDK: 37
- Android Gradle Plugin: 9.3.0 with built-in Kotlin (the project intentionally does not apply `org.jetbrains.kotlin.android`)
- Gradle wrapper target: 9.5.0
- JDK: 17 or newer

## Environment installation

1. Install Android Studio with a bundled JDK 17+ or install a standalone JDK 17+ and set `JAVA_HOME`.
2. In Android Studio SDK Manager, install Android SDK Platform 37, SDK Build-Tools, Platform-Tools, and Command-line Tools.
3. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the SDK directory. On Windows it is commonly `%LOCALAPPDATA%\Android\Sdk`.
4. If this source tree does not contain `gradle/wrapper/gradle-wrapper.jar`, generate it from a trusted Gradle 9.5.0 installation with `gradle wrapper --gradle-version 9.5.0`, or let Android Studio repair/regenerate the wrapper. The wrapper scripts and properties are already present.
5. Open `E:\personal\SlimBrowser` in Android Studio and allow Gradle synchronization.

Command-line verification after the prerequisites are installed:

```powershell
cd E:\personal\SlimBrowser
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## Behavior

- The first launch reads Preferences DataStore. If no home URL exists, a non-cancelable settings dialog is shown.
- URLs without a scheme are completed with `https://`; non-HTTPS schemes, embedded credentials, invalid hosts, and invalid ports are rejected.
- The settings switch and dedicated fullscreen button both update and persist the same `fullscreen_enabled` preference. A separate settings button opens the configuration dialog.
- Immersive mode uses `WindowInsetsControllerCompat` and transient system bars by swipe.
- Android back navigates WebView history before finishing the activity.
- WebView state/history is saved in `onSaveInstanceState` and restored after recreation.
- Main-frame network, HTTP, TLS, and Safe Browsing failures show a native error overlay with retry.
- A terminated WebView renderer is removed, destroyed, recreated, and replaced with a retryable native error state.

## Architecture

- `ui/browser/MainActivity.kt`: Android View implementation, WebView lifecycle, settings dialog, insets, and native overlay.
- `ui/browser/BrowserContract.kt`: MVP contracts.
- `ui/browser/BrowserPresenter.kt`: initial settings flow, navigation decisions, fullscreen synchronization, and persistence commands.
- `data/BrowserPreferences.kt`: Preferences DataStore repository.
- `domain/UrlPolicy.kt`: deterministic, JVM-testable HTTPS normalization and validation.

The presenter deliberately receives a lifecycle-owned `CoroutineScope`; it contains no Activity reference after `detach()`.

## Security constraints

The app is a narrow browser shell, not a general-purpose trusted browser. It applies these controls:

- Manifest-level cleartext denial with `android:usesCleartextTraffic="false"`.
- Main-frame navigation accepts HTTPS only.
- TLS errors are canceled; the app never calls `SslErrorHandler.proceed()`.
- Safe Browsing is enabled when supported by the installed WebView.
- Mixed content is denied.
- File/content access, file-URL cross-origin access, geolocation, multiple windows, automatic JavaScript windows, WebView debugging, and web permission requests are disabled/denied.
- Third-party cookies are disabled.
- No JavaScript bridge is exposed.
- Renderer termination is explicitly handled.

JavaScript and DOM storage remain enabled because modern target sites commonly require them. A compromised allowed site can still execute JavaScript inside its WebView origin, so URL allowlisting, certificate pinning, downloads, uploads, external intents, authentication, and remote content policy should be designed before production use.

## Tests

`UrlPolicyTest` is a local JVM test covering scheme completion, host/scheme normalization, IDN conversion, HTTPS enforcement, port and credential rejection, malformed input, and control characters.

Recommended manual tests:

1. Fresh install: mandatory settings dialog appears; Cancel is unavailable.
2. Enter `example.com`: it persists and loads as `https://example.com/`.
3. Enter `http://example.com`, a `file:`/`javascript:` URL, embedded credentials, or an invalid port: validation fails.
4. Toggle fullscreen in settings, then with the FAB; relaunch and confirm the same persisted state.
5. Navigate through several links and press Back; WebView history is consumed before the activity exits.
6. Rotate/recreate the activity and confirm page/history restoration.
7. Load a nonexistent host, HTTP 404/500 page, or bad TLS endpoint and verify the native overlay and Retry action.
8. Use Android Studio's WebView renderer termination/debug facilities or kill the sandboxed renderer and verify recovery without an app crash.

## Acceptance checklist

- [ ] Gradle sync succeeds with AGP 9.3.0 / Gradle 9.5.0 / JDK 17+.
- [ ] `testDebugUnitTest` passes.
- [ ] `assembleDebug` produces an APK.
- [ ] First-run, HTTPS-only, persistence, fullscreen, error overlay, back history, state restoration, and renderer recovery manual tests pass on API 26 and API 37 devices/emulators.

See `docs/implementation-plan.md` for the implementation map and extended acceptance plan.
