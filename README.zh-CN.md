# SlimBrowser

中文说明

SlimBrowser 是一款面向个人使用的简洁、快速 Android 浏览器，围绕首页、浏览页和设置页三个场景，强调全屏、无干扰和隐私可控体验。项目使用 Kotlin、XML Views、ViewBinding、Android System WebView、AndroidX WebKit、Preferences DataStore 和轻量 MVP 结构构建。详细开发计划见 [`docs/development-plan.md`](docs/development-plan.md)，实时待办状态见 [`docs/TODO.md`](docs/TODO.md)。

## 技术环境

- 包名 / Application ID：`com.example.slimbrowser`
- 最低 SDK：26（Android 8.0）
- Compile SDK / Target SDK：37
- Android Gradle Plugin：9.3.0，使用 AGP 内置 Kotlin 支持
- Gradle Wrapper：9.5.0
- JDK：17 或更高版本

## 安装开发环境

1. 安装 Android Studio，并使用其内置的 JDK 17+，或单独安装 JDK 17+ 并设置 `JAVA_HOME`。
2. 在 Android Studio 的 SDK Manager 中安装 Android SDK Platform 37、SDK Build-Tools、Platform-Tools 和 Command-line Tools。
3. 设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`。Windows 常见路径为 `%LOCALAPPDATA%\Android\Sdk`。
4. 如果项目中没有 `gradle/wrapper/gradle-wrapper.jar`，请使用可信的 Gradle 9.5.0 安装执行 `gradle wrapper --gradle-version 9.5.0`，或让 Android Studio 重新生成 Wrapper。项目中的 Wrapper 脚本和配置已经准备好。
5. 在 Android Studio 中打开 `E:\personal\SlimBrowser`，等待 Gradle 同步完成。

安装好环境后，可以执行：

```powershell
cd E:\personal\SlimBrowser
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Debug APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`，可直接传输到 Android 设备安装；连接设备后也可以执行：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 功能特性

- 首次启动读取 Preferences DataStore；首页网址可以留空，留空时在透明背景上以四列圆形按钮宫格显示收藏的网站，不再强制弹出设置对话框。
- 用户可以输入不带协议的网址，应用会自动补充 `https://`；留空时显示收藏页，底部搜索入口使用百度。
- 用户显式输入 HTTP、私网、`file:`、`content:`、`data:`、`blob:`、带凭据或自定义协议的 URI 时，应用会优先尝试 WebView；不支持时交由系统已安装应用继续处理。
- 设置页开关和独立的全屏按钮共享并持久化同一个 `fullscreen_enabled` 配置。
- 支持沉浸式全屏，边缘滑动可以临时显示系统栏。
- 设置页支持选择自定义背景图片、恢复主题背景和清除 Cookie、缓存及历史数据。
- 设置页支持收藏当前页面、打开收藏列表，并可逐项打开或删除收藏。
- 支持下拉刷新和刷新悬浮按钮，页面加载时显示进度状态。
- 支持任意站点导航，外部电话、短信、邮件、地图、商店、Intent 与未知协议在用户输入或点击后尝试交给系统处理。
- 支持网页文件上传和 HTTP/HTTPS 文件下载。
- 底部操作图标默认展示并保持可用；留空首页的搜索图标与其他操作图标一起位于底部中央。
- 所有应用内界面文字默认使用中文，不提供语言切换选项。
- 设置页支持亮色/暗色主题切换，并使用对应的主题壁纸。
- 返回键优先回退 WebView 浏览历史，没有历史时退出 Activity。
- Activity 重建时保存并恢复 WebView 页面状态和历史记录。
- 网络、HTTP、TLS 和 Safe Browsing 错误使用原生错误覆盖层显示，并提供重试按钮。
- WebView 渲染进程终止后会被销毁并重新创建，用户可以重试恢复页面。

## 项目结构

- `ui/browser/MainActivity.kt`：Android View、WebView 生命周期、设置对话框、WindowInsets 和错误覆盖层。
- `ui/browser/BrowserContract.kt`：MVP 接口定义。
- `ui/browser/BrowserPresenter.kt`：启动设置读取、导航决策、全屏同步和持久化操作。
- `data/BrowserPreferences.kt`：Preferences DataStore 仓库。
- `domain/UrlPolicy.kt`：可进行 JVM 单元测试的严格 HTTPS 与宽松 URI 导航规范化逻辑。

Presenter 接收由 Activity 生命周期管理的 `CoroutineScope`，在 `detach()` 后不会持有 Activity 引用。

## 安全策略

SlimBrowser 是一个范围受限的浏览器容器，不是面向任意网站的完整可信浏览器。当前包含以下安全措施：

- Manifest 允许明文网络流量，以支持用户主动打开 HTTP 地址。
- 主页面优先尝试 WebView；未知协议在用户输入/点击后委派给系统处理器。
- TLS 证书错误始终取消，不调用 `SslErrorHandler.proceed()`。
- 在系统 WebView 支持时启用 Safe Browsing。
- 使用兼容性 Mixed Content。
- 允许文件访问和 Content URI；仍禁止 file URL 跨域访问、地理位置、多窗口和自动 JavaScript 弹窗。
- 拒绝网页的相机、麦克风等 Web 权限请求。
- 禁用第三方 Cookie。
- 不暴露 JavaScript Bridge。
- 显式处理 WebView 渲染进程终止。

由于现代网站通常需要 JavaScript 和 DOM Storage，应用仍然启用了这两项能力。若允许的网站遭到入侵，其 JavaScript 仍可在对应 WebView Origin 内运行。正式生产部署前，应根据场景增加域名白名单、下载/上传策略、外部 Intent 策略、身份认证和远程内容策略。

## 测试

`UrlPolicyTest` 是 JVM 本地测试，覆盖以下场景：

- 自动补充 HTTPS 协议
- 协议和主机名大小写规范化
- 国际化域名转换
- 宽松 URI 导航与协议分流
- 端口和嵌入式凭据校验
- 格式错误、控制字符、显式特殊协议和系统处理器回退

建议进行以下手工测试：

1. 全新安装后，确认显示收藏页；在设置页保存一个 HTTPS 网址后确认可正常打开。
2. 输入 `example.com`，确认保存并加载 `https://example.com/`。
3. 输入 `http://example.com`、`file:`、`data:`、`javascript:`、包含账号密码或自定义协议的网址，确认浏览器或系统处理器会尽力打开。
4. 在设置页切换全屏，再使用全屏按钮切换；重新启动应用后确认状态保持一致。
5. 停止操作约 3 秒，确认设置和全屏按钮隐藏；触摸页面后确认重新显示。
6. 在收藏首页点击已收藏的网站，或点击底部搜索图标输入关键词后确认跳转百度；在网页设置中收藏当前页面，再从收藏列表打开和删除。
6. 在中文系统和英文系统上分别启动，确认应用内界面均使用中文且设置页没有语言切换按钮；切换亮色/暗色主题，确认界面与壁纸同步变化并在重启后保持。
7. 连续打开多个站内页面并按返回键，确认优先消耗 WebView 历史。
8. 旋转屏幕或重建 Activity，确认页面和历史记录恢复。
9. 访问不存在的域名、HTTP 404/500 页面或错误 TLS 地址，确认错误覆盖层和重试功能正常。
10. 使用 Android Studio 的 WebView 渲染进程调试能力，确认渲染进程退出后应用不会崩溃并可以恢复。

## 验收清单

- [ ] 使用 AGP 9.3.0、Gradle 9.5.0 和 JDK 17+ 成功完成 Gradle 同步。
- [ ] `testDebugUnitTest` 通过。
- [ ] `assembleDebug` 成功生成 APK。
- [ ] 在 API 26 和 API 37 模拟器或真实设备上完成首次启动、协议兼容、配置持久化、全屏、错误页、返回历史、状态恢复和渲染进程恢复测试。

详细实现方案请参阅 [`docs/implementation-plan.md`](docs/implementation-plan.md)。
