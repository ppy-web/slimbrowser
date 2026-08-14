# SlimBrowser

SlimBrowser 是一款简洁的单站点 Android 浏览器，使用 Kotlin、XML Views、ViewBinding、Android System WebView、AndroidX WebKit、Preferences DataStore 和轻量 MVP 结构构建。

## 技术环境

- 包名 / Application ID：`com.example.slimbrowser`
- 最低 SDK：26
- Compile SDK / Target SDK：37
- Android Gradle Plugin：9.3.0，使用 AGP 内置 Kotlin 支持
- Gradle Wrapper：9.5.0
- JDK：17 或更高版本

## 安装开发环境

1. 安装 Android Studio，并使用内置 JDK 17+，或单独安装 JDK 17+ 并设置 `JAVA_HOME`。
2. 在 SDK Manager 中安装 Android SDK Platform 37、Build-Tools、Platform-Tools 和 Command-line Tools。
3. 设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`。
4. 如果项目没有 `gradle/wrapper/gradle-wrapper.jar`，使用可信的 Gradle 9.5.0 安装重新生成 Wrapper。
5. 在 Android Studio 中打开项目并等待 Gradle 同步完成。

环境准备完成后，可以执行：

```text
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`，可直接传输到 Android 设备安装；连接设备后也可以执行：

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 功能特性

- 首次启动读取 Preferences DataStore；首页网址可以留空，留空时显示简约搜索入口，不再强制弹出设置对话框。
- 未填写协议的网址会自动补充 `https://`；留空时搜索入口使用百度。非 HTTPS 协议、嵌入式账号密码、非法主机名和非法端口会被拒绝。
- 设置页可以修改首页网址、全屏状态和亮暗主题，所有设置都会持久化。
- 设置页支持选择自定义背景图片、恢复主题背景和清除 Cookie、缓存及历史数据。
- 设置页支持收藏当前页面、打开收藏列表，并可逐项打开或删除收藏。
- 所有应用内界面文字默认使用中文，不提供语言切换选项。
- 设置和全屏悬浮按钮无操作约 3 秒后自动隐藏，触摸网页后重新显示。
- 支持下拉刷新和刷新悬浮按钮，页面加载时显示进度状态。
- 支持同站点及子域名导航，外部电话、短信、邮件和 Intent 链接交给系统处理。
- 支持网页文件上传和同站点 HTTPS 文件下载。
- 支持沉浸式全屏，边缘滑动可以临时显示系统栏。
- 返回键优先回退 WebView 浏览历史，没有历史时退出 Activity。
- Activity 重建时保存并恢复 WebView 页面状态和历史记录。
- 网络、HTTP、TLS 和 Safe Browsing 错误使用原生错误覆盖层显示，并提供重试按钮。
- WebView 渲染进程终止后会被销毁并重新创建，用户可以重试恢复页面。

## 项目结构

- `ui/browser/MainActivity.kt`：Android View、WebView 生命周期、设置对话框、系统栏和错误覆盖层。
- `ui/browser/BrowserContract.kt`：MVP 接口定义。
- `ui/browser/BrowserPresenter.kt`：启动设置读取、导航决策、全屏和主题同步、持久化操作。
- `data/BrowserPreferences.kt`：Preferences DataStore 仓库。
- `domain/UrlPolicy.kt`：可进行 JVM 单元测试的 HTTPS URL 规范化和校验逻辑。

## 安全策略

- Manifest 中设置 `android:usesCleartextTraffic="false"`，禁止明文网络流量。
- 主页面导航只接受 HTTPS。
- TLS 证书错误始终取消，不调用 `SslErrorHandler.proceed()`。
- 在系统 WebView 支持时启用 Safe Browsing。
- 禁止 Mixed Content、文件访问、Content URI 访问、文件 URL 跨域访问、地理位置、多窗口和自动 JavaScript 弹窗。
- 拒绝网页的相机、麦克风等 Web 权限请求。
- 禁用第三方 Cookie，不暴露 JavaScript Bridge。
- 显式处理 WebView 渲染进程终止。

由于现代网站通常需要 JavaScript 和 DOM Storage，应用仍然启用了这两项能力。正式生产部署前，应根据场景增加域名白名单、下载/上传策略、外部 Intent 策略、身份认证和远程内容策略。

## 测试

`UrlPolicyTest` 是 JVM 本地测试，覆盖协议补充、主机名规范化、国际化域名、HTTPS 强制校验、端口和凭据校验、格式错误及控制字符。

建议手工测试：

1. 全新安装后确认显示简约搜索入口；在设置页保存一个 HTTPS 网址后确认可正常打开。
2. 输入 `example.com`，确认保存并加载 `https://example.com/`。
3. 输入 HTTP、`file:`、`javascript:`、带账号密码或非法端口的网址，确认校验失败。
4. 在中文系统和英文系统上分别启动，确认应用内界面均使用中文且设置页没有语言切换按钮；切换亮色/暗色主题并重启，确认主题状态保持。
5. 停止操作约 3 秒，确认设置和全屏按钮隐藏；触摸网页后确认重新显示。
6. 在空白首页点击搜索图标，输入关键词后确认跳转百度；在网页设置中收藏当前页面，再从收藏列表打开和删除。
6. 打开多个站内页面并按返回键，确认优先消耗 WebView 历史。
7. 旋转屏幕或重建 Activity，确认页面和历史记录恢复。
8. 访问不存在的域名、HTTP 404/500 页面或错误 TLS 地址，确认错误覆盖层和重试功能正常。

## 验收清单

- [ ] 使用 AGP 9.3.0、Gradle 9.5.0 和 JDK 17+ 成功完成 Gradle 同步。
- [ ] `testDebugUnitTest` 通过。
- [ ] `assembleDebug` 成功生成 APK。
- [ ] 在 API 26 和 API 37 设备或模拟器上完成首次启动、HTTPS 限制、配置持久化、全屏、主题、错误页、返回历史、状态恢复和渲染进程恢复测试。

详细实现方案请参阅 [`docs/implementation-plan.md`](docs/implementation-plan.md)。
