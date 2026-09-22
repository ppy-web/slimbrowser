# SlimBrowser

SlimBrowser 是一款面向个人使用的简洁、快速 Android 浏览器，围绕首页、浏览页和设置页三个场景，强调全屏、无干扰和隐私可控体验。项目使用 Kotlin、XML Views、ViewBinding、Android System WebView、AndroidX WebKit、Preferences DataStore 和轻量 MVP 结构构建。详细开发计划见 [`docs/development-plan.md`](docs/development-plan.md)，实时待办状态见 [`docs/TODO.md`](docs/TODO.md)。

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

- 首次启动读取 Preferences DataStore；首页网址可以留空，留空时在透明背景上以四列圆形按钮宫格显示收藏的网站，不再强制弹出设置对话框。
- 未填写协议的网址会自动补充 `https://`；用户显式输入的 HTTP、私网、`file:`、`content:`、`data:`、`blob:` 和自定义 URI 都会尽力打开；WebView 不支持的协议会尝试交给系统处理器。
- 设置页可以修改首页网址、全屏状态和亮暗主题，所有设置都会持久化。
- 设置页支持选择自定义背景图片、恢复主题背景和清除 Cookie、缓存及历史数据。
- 设置页支持收藏当前页面、打开收藏列表，并可逐项打开或删除收藏。
- 所有应用内界面文字默认使用中文，不提供语言切换选项。
- 设置和全屏悬浮按钮无操作约 3 秒后自动隐藏，触摸网页后重新显示。
- 支持下拉刷新和刷新悬浮按钮，页面加载时显示进度状态。
- 支持任意站点导航，电话、短信、邮件、地图、商店、Intent 和未知协议在用户点击或输入后尝试交给系统处理。
- 支持网页文件上传和 HTTP/HTTPS 文件下载。
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
- `domain/UrlPolicy.kt`：可进行 JVM 单元测试的严格 HTTPS 规范化与宽松 URI 导航规范化逻辑。

## 访问策略

SlimBrowser 是供个人调试和测试网站的浏览器，核心原则是“用户要求打开，就尽力打开”，而不是建立域名白名单或 HTTPS-only 限制：

- 主框架导航支持 HTTP/HTTPS、局域网和回环地址、文件/Content/Data/Blob URI，以及用户输入的其他合法 URI。
- 自定义协议、`mailto:`、`tel:`、`geo:` 和 `intent:` 会交给 Android 系统处理器；没有处理器时才提示失败。
- 不因重定向来源、跨站跳转、混合内容、第三方 Cookie、证书错误或 Safe Browsing 告警而静默拒绝用户请求；WebView/系统仍可能因自身无法解析、网络不可达或平台权限而失败。
- 本地文件调试需要的文件访问、跨文件 URL 访问、JavaScript、DOM Storage、混合内容和自动播放均保持开启。
- 网页权限不再由浏览器代码一律拒绝，最终由 Android 系统权限和用户设备设置决定。

这不是面向不可信用户的安全浏览器。请只在个人测试环境中使用；不要在其中输入不希望暴露给网页的账号、Cookie 或敏感数据。

## 测试

`UrlPolicyTest` 是 JVM 本地测试，覆盖协议补充、主机名规范化、国际化域名、严格 HTTPS 规范化与宽松 URI 导航。

建议手工测试：

1. 全新安装后确认显示收藏页；在设置页保存一个 HTTPS 网址后确认可正常打开。
2. 输入 `example.com`，确认保存并加载 `https://example.com/`。
3. 输入 HTTP、`file:`、`data:`、`javascript:`、带账号密码或自定义协议的网址，确认浏览器或系统处理器会尽力打开。
4. 在中文系统和英文系统上分别启动，确认应用内界面均使用中文且设置页没有语言切换按钮；切换亮色/暗色主题并重启，确认主题状态保持。
5. 停止操作约 3 秒，确认设置和全屏按钮隐藏；触摸网页后确认重新显示。
6. 在收藏首页点击已收藏的网站，或点击底部搜索图标输入关键词后确认跳转百度；在网页设置中收藏当前页面，再从收藏列表打开和删除。
6. 打开多个站内页面并按返回键，确认优先消耗 WebView 历史。
7. 旋转屏幕或重建 Activity，确认页面和历史记录恢复。
8. 访问不存在的域名、HTTP 404/500 页面或错误 TLS 地址，确认错误覆盖层和重试功能正常。

## 验收清单

- [ ] 使用 AGP 9.3.0、Gradle 9.5.0 和 JDK 17+ 成功完成 Gradle 同步。
- [ ] `testDebugUnitTest` 通过。
- [ ] `assembleDebug` 成功生成 APK。
- [ ] 在 API 26 和 API 37 设备或模拟器上完成首次启动、协议兼容、配置持久化、全屏、主题、错误页、返回历史、状态恢复和渲染进程恢复测试。

详细实现方案请参阅 [`docs/implementation-plan.md`](docs/implementation-plan.md)。
