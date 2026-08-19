# SlimBrowser 详细开发计划

> 文档模式：**1 个总体大纲 + N 个可独立交付的功能模块**。
>
> 产品目标：打造一款个人使用、简洁、现代、全屏无干扰的 Android 浏览器。用户主要面对三个场景：**首页、浏览页、设置页**。
>
> 技术基线：Kotlin、XML Views、ViewBinding、Android System WebView、AndroidX WebKit、Material 3、DataStore、minSdk 26。

---

# 一、总体开发大纲

## 1. 产品原则

1. **网页内容优先**：浏览页绝大部分面积属于 WebView，应用控件默认收起或保持紧凑。
2. **三场景清晰**：首页负责开始浏览，浏览页负责内容消费，设置页负责持久配置和数据管理。
3. **全屏但可退出**：沉浸式全屏不能隐藏所有逃生路径；边缘呼出、系统返回和工具栏必须行为一致。
4. **Liquid Glass 克制使用**：玻璃效果只用于应用控制层，不给 WebView 内容本身施加模糊或滤镜。
5. **本地优先**：历史、收藏、设置默认仅存本机；首版不建设账号和云同步。
6. **安全失败优先**：TLS、Safe Browsing、危险 scheme 和未知外部 Intent 默认阻止，不提供绕过证书错误能力。
7. **渐进增强**：API 31+ 使用更完整的模糊效果，API 26-30 使用透明渐变和描边降级。
8. **模块可独立交付**：每个模块必须可构建、可测试、可验收，不以一次性大重构为前提。

## 2. 目标信息架构

```text
MainActivity
├── Home Scene
│   ├── 统一地址/搜索输入
│   ├── 快捷站点
│   ├── 最近访问
│   └── 继续上次浏览
├── Browser Scene
│   ├── WebView
│   ├── Liquid Glass 浏览工具栏
│   ├── 错误/恢复状态
│   └── 更多操作 Sheet
└── Settings Scene
    ├── 浏览体验
    ├── 外观与全屏
    ├── 隐私与数据
    └── 关于与诊断
```

首期继续保持单 Activity。三场景可以先使用同一布局中的三个 View 容器和显式 `AppScene` 状态切换，避免立即引入 Fragment/Navigation 造成架构迁移。待历史、收藏、下载等列表页面增多后，再评估 Fragment + Navigation。

## 3. 建议目标架构

```text
ui/
├── MainActivity.kt
├── AppScene.kt
├── BrowserUiState.kt
├── home/
│   ├── HomeView.kt / HomeController.kt
│   └── HomeAdapter.kt
├── browser/
│   ├── BrowserController.kt
│   ├── BrowserToolbarController.kt
│   ├── BrowserWebViewController.kt
│   └── BrowserErrorRenderer.kt
└── settings/
    └── SettingsController.kt

domain/
├── InputResolver.kt
├── UrlPolicy.kt
├── NavigationPolicy.kt
├── ExternalLinkPolicy.kt
├── BrowserError.kt
└── SearchEngine.kt

data/
├── BrowserPreferences.kt
├── BrowserSessionRepository.kt
├── BrowserDataManager.kt
├── history/
├── bookmark/
└── download/

design/
├── GlassTokens.kt（如需运行时参数）
├── styles.xml
├── colors.xml
└── drawable/glass_*.xml
```

当前 MVP 可以渐进演进，不要求第一阶段立刻改为 MVVM。核心要求是：Activity 不再同时承担全部状态、策略、WebView、设置和视觉逻辑。

## 4. 状态模型

建议新增统一状态：

```kotlin
enum class AppScene { HOME, BROWSER, SETTINGS }

data class BrowserUiState(
    val scene: AppScene = AppScene.HOME,
    val url: String = "",
    val displayHost: String = "",
    val title: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isFullscreen: Boolean = false,
    val toolbarVisible: Boolean = true,
    val error: BrowserError? = null,
)
```

所有按钮状态和场景显示均从该状态渲染，避免直接从多个 View、Presenter 字段和 WebView 临时读取状态。

## 5. 开发阶段

### Phase 0：可重复构建与基线冻结

- 修复 Gradle Wrapper/JDK 环境。
- 跑通单元测试、Lint 和 Debug APK。
- 保存当前 APK、关键页面截图和手工测试结果作为回归基线。

### Phase 1：核心状态与三场景骨架

- 模块 1～5。
- 完成 AppScene、BrowserUiState、输入解析、导航安全和会话恢复。
- 能够从首页进入浏览页、进入设置页并正确返回。

### Phase 2：浏览体验与 Liquid Glass

- 模块 6～10。
- 完成首页、玻璃视觉系统、浏览工具栏、无干扰全屏、结构化错误页。

### Phase 3：设置、隐私和个人数据

- 模块 11～15。
- 完成独立设置页、数据清理、历史、收藏、分享和页内查找。

### Phase 4：网页兼容与高级能力

- 模块 16～18。
- 完成下载上传、轻量标签页评估与质量体系。

## 6. 每模块完成定义 Definition of Done

每个功能模块必须同时满足：

1. 代码实现完成，不留阻断性 TODO。
2. 纯逻辑具备 JVM 单元测试；Android 行为具备仪器测试或明确手工测试步骤。
3. API 26 和 API 31+ 至少各验证一次。
4. 浅色、深色、横屏、系统返回、进程重建不出现明显回归。
5. 更新 `docs/TODO.md` 对应状态。
6. 新增依赖时记录用途、版本和许可证。
7. `testDebugUnitTest`、`lintDebug`、`assembleDebug` 通过。

---

# 二、功能模块

## 模块 1：构建环境与质量基线

### 目标

建立可重复构建环境，确保后续每个模块都有可靠验证入口。

### 技术方案

- 补齐 `gradle/wrapper/gradle-wrapper.jar`，固定 Wrapper 版本。
- 使用 JDK 17+，确认 Android SDK 37 可用。
- 保持 AGP 内置 Kotlin，不额外应用 `org.jetbrains.kotlin.android`。
- 将 `.kotlin/`、构建目录等保持在 `.gitignore`。
- 建立最小检查脚本或 CI：单元测试、Lint、Debug 构建。

### 实现路径

1. 检查 `JAVA_HOME`、`ANDROID_HOME` 和 Wrapper 完整性。
2. 执行 `gradlew.bat --version`。
3. 执行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`。
4. 记录现有 Warning，区分安全意图明确的 WebSettings 兼容 API 和真实问题。
5. 将成功构建命令写入 README。

### 影响文件

- `gradle/wrapper/*`
- `.gitignore`
- `README.md`
- `README.zh-CN.md`
- 可选 `.github/workflows/android.yml`

### 效果验证

- 全新环境按 README 能完成构建。
- `app-debug.apk` 可安装启动。
- 三条 Gradle 检查全部返回成功。
- Git 工作区不出现 `.kotlin/`、`build/` 等缓存文件。

### 完成标准

- [ ] Wrapper 可执行
- [ ] Unit Test 通过
- [ ] Lint 通过或已记录并批准例外
- [ ] Debug APK 成功生成

---

## 模块 2：三场景状态与应用导航

### 目标

建立首页、浏览页、设置页三个明确场景及一致的返回规则。

### 技术方案

首期采用单 Activity + 三个 View 容器：

```xml
<FrameLayout id="homeContainer" />
<FrameLayout id="browserContainer" />
<FrameLayout id="settingsContainer" />
```

使用 `AppScene` 和单一渲染函数切换可见性。禁止在多个点击回调中零散设置 View 显隐。

返回规则：

- 设置页：返回进入设置前的场景。
- 浏览页：WebView 有历史则后退；无历史则回首页。
- 首页：系统返回退出应用。
- 错误状态：优先执行错误页定义的返回行为。

### 实现路径

1. 新增 `AppScene` 和 `previousScene`。
2. 扩展 `BrowserUiState.scene`。
3. 改造 `activity_main.xml` 为三容器结构。
4. 新增 `renderScene(scene)`。
5. 将现有设置 Dialog 暂时保留为兼容入口，后续模块 11 替换。
6. 改造返回键分发逻辑。
7. 保存当前 scene 到轻量状态，进程恢复时验证是否合法。

### 影响文件

- `MainActivity.kt`
- `BrowserContract.kt` / `BrowserPresenter.kt`
- `activity_main.xml`
- 新增 `AppScene.kt`、`BrowserUiState.kt`

### 效果验证

- 冷启动进入首页。
- 首页打开 URL 后进入浏览页。
- 浏览页无历史时返回首页，而不是直接退出。
- 设置页返回原场景。
- 旋转屏幕后场景不错误跳转。

### 自动化建议

- Presenter/状态机 JVM 测试覆盖所有返回路径。
- Espresso 验证三个容器同一时间仅一个主场景可见。

---

## 模块 3：输入解析、搜索引擎与 URL 安全策略

### 目标

让统一输入框同时支持网址与关键词搜索，并保证所有主框架导航经过一致策略。

### 技术方案

新增：

```kotlin
sealed interface InputResolution {
    data class Url(val normalized: String) : InputResolution
    data class Search(val url: String, val query: String) : InputResolution
    data class Invalid(val reason: InputError) : InputResolution
}
```

- `InputResolver`：判断输入是 URL 还是搜索关键词。
- `UrlPolicy`：只负责 URL 规范化与主机语法。
- `SearchEngine`：保存名称和 HTTPS 查询模板。
- `NavigationPolicy`：处理网页点击、重定向、主框架 URL 和外部 scheme。

普通个人浏览器建议允许任意语法合法的 HTTPS 端口；拒绝 HTTP、用户凭据、控制字符和危险 scheme。是否允许局域网/IP 地址作为单独设置项，不与默认公共浏览策略混合。

### 实现路径

1. 明确端口、IPv4、IPv6、尾点域名和 IDN 策略。
2. 修复可靠的 IPv6 语法验证。
3. 新增搜索引擎模型和默认模板。
4. 新增 `InputResolver.resolve(input, engine)`。
5. 地址栏和首页输入均调用同一 Resolver。
6. `shouldOverrideUrlLoading` 改为调用 `NavigationPolicy`，不再只判断 scheme。
7. 将被拒绝原因映射为结构化错误或内联提示。

### 测试矩阵

- 普通域名、路径、查询、fragment。
- HTTPS 443/8443。
- IPv4、IPv6 完整/压缩/畸形形式。
- Unicode IDN、同形域提示策略、尾点域名。
- 空白、控制字符、凭据、`javascript:`、`data:`、`file:`。
- 中文和英文搜索词、带空格关键词、URL 编码。

### 效果验证

- `example.com` 打开 `https://example.com/`。
- “android webview”进入默认搜索引擎。
- 危险协议不加载，不启动未知 Intent。
- 网页点击和手工输入遵循同一安全规则。

---

## 模块 4：浏览状态同步与会话恢复

### 目标

准确维护当前 URL、标题、进度和历史能力，并修复 Renderer 崩溃后回到主页的问题。

### 技术方案

- `BrowserUiState` 作为 UI 状态单一来源。
- 从以下回调同步真实状态：
  - `onPageStarted`
  - `onPageFinished`
  - `doUpdateVisitedHistory`
  - `WebChromeClient.onProgressChanged`
  - `WebChromeClient.onReceivedTitle`
  - `WebChromeClient.onReceivedIcon`
- 只将通过 `NavigationPolicy` 的主框架 URL 写入 `lastSafeUrl`。
- DataStore 保存轻量 `BrowserSession(lastSafeUrl, title, timestamp)`。
- WebView Bundle 仅作为短期完整恢复；恢复失败时加载 `lastSafeUrl`，再失败回首页。

### 实现路径

1. 修改 Presenter 回调，使 `onPageStarted(url)`、`onPageFinished(url)` 接收真实 URL。
2. 增加历史更新和标题回调。
3. 实现 `BrowserSessionRepository`。
4. Renderer Gone 时先重建 WebView，再让用户重试 `lastSafeUrl`。
5. 限制/评估完整 WebView Bundle 保存，加入降级路径。
6. 所有状态变化统一调用 `renderBrowserState()`。

### 效果验证

- 主页进入二级页面后 Renderer 崩溃，重试恢复二级页面而不是主页。
- 多次重定向后地址栏显示最终 URL。
- 旋转后能恢复；完整状态失败时仍能打开最后安全 URL。
- 返回/前进按钮始终和 WebView 状态一致。

---

## 模块 5：外部链接和 Intent 安全策略

### 目标

兼容电话、邮件、地图、应用商店等外部能力，同时避免网页静默启动任意应用。

### 技术方案

新增 `ExternalLinkPolicy`：

- `tel:` → `ACTION_DIAL`，不直接拨号。
- `mailto:` → `ACTION_SENDTO`。
- `geo:` → 用户确认后打开。
- `market:` → 用户确认后打开应用商店。
- `intent:` → 默认拒绝；如支持，必须解析后校验 package、fallback URL 和类别。
- 未知 scheme → 显示确认/错误，不静默启动。

### 实现路径

1. 抽离现有 `openExternal()`。
2. 建立 `ExternalAction` sealed class。
3. 由 Activity 负责执行 Intent，由 Domain Policy 决定动作。
4. 使用 chooser 或确认 Bottom Sheet 显示目标域名/应用。
5. 捕获 `ActivityNotFoundException` 并呈现结构化错误。

### 效果验证

- 电话只进入拨号界面。
- 邮件只打开邮件客户端。
- 未安装外部应用时不崩溃。
- `intent:` 和未知 scheme 不会静默跳出应用。

---

## 模块 6：首页场景

### 目标

实现简洁的启动入口：统一搜索/地址输入、快捷站点、最近访问和继续浏览。

### 技术方案

首页布局分三层：

1. 背景层：纯色/弱渐变，避免持续动态背景。
2. 主操作层：居中的 Liquid Glass 搜索胶囊。
3. 辅助层：最多 4～8 个快捷站点、最近访问和设置入口。

首版数据可先使用固定空状态；历史和收藏在模块 13/14 完成后接入。

### 实现路径

1. 新建 `view_home.xml`。
2. 实现搜索胶囊：默认态、聚焦态、错误态。
3. 接入 `InputResolver`。
4. 添加“继续上次浏览”，读取 BrowserSession。
5. 添加快捷站点占位和空状态。
6. 将首次启动强制设置 Dialog 改为首页引导。
7. 处理 IME、粘贴、清空和 Search/Go 动作。

### 交互规范

- 聚焦形变 180～240ms。
- 不自动读取剪贴板；仅用户点击粘贴时读取。
- 输入错误显示在胶囊下方，不弹阻断对话框。
- 点击快捷站点立即进入浏览页。

### 效果验证

- 首次安装不需要先填写固定主页。
- URL 和关键词都能正确进入浏览页。
- 无历史时页面不空洞，有清晰引导。
- 键盘不会遮挡输入与错误提示。

---

## 模块 7：Liquid Glass 设计系统

### 目标

建立统一、可降级、性能可控的玻璃视觉系统，而不是在各页面零散设置透明背景。

### 技术方案

定义 design tokens：

- 圆角：搜索胶囊 28dp，工具栏 24dp，设置卡片 20dp。
- 透明度：浅色 72%～86%，深色 60%～76%，最终以对比度测试调整。
- 描边：1dp 高光/低对比描边。
- 阴影：小范围柔和阴影，避免大面积实时阴影。
- 模糊：API 31+ 原生 RenderEffect；API 26-30 半透明渐变降级。
- 动画：标准 180ms，场景切换 240ms，禁止持续漂浮。

是否引入 `Dimezis/BlurView` 必须经过性能 PoC：特别测试 WebView 滚动、视频和低端 API 26 设备。默认计划不依赖第三方模糊库。

### 实现路径

1. 扩展 `colors.xml`、`themes.xml`、`styles.xml`。
2. 新增玻璃背景 drawable：浅色、深色、pressed、disabled。
3. 建立统一 `Widget.SlimBrowser.Glass.*` 样式。
4. 实现 API 31+ 模糊帮助类和低版本降级。
5. 应用到首页搜索、浏览工具栏、设置分组和错误卡片。
6. 增加“降低透明度/减少动态效果”适配。

### 性能约束

- 不对整个 WebView 做 RenderEffect。
- 模糊区域不超过工具栏/卡片实际范围。
- 滚动过程中不持续修改 blur radius。
- 低端设备可完全关闭模糊但保留视觉层级。

### 效果验证

- 浅色/深色文字对比度可读。
- API 26 降级后布局与交互不缺失。
- WebView 快速滚动无明显掉帧或拖影。
- 系统减少动画开启后，形变和滑动动画正确简化。

---

## 模块 8：浏览页玻璃工具栏

### 目标

以低干扰悬浮工具栏替代两个常驻 FAB，提供浏览器最小控制闭环。

### 技术方案

工具栏包含：

- 返回
- 前进
- 地址/标题胶囊
- 刷新/停止
- 更多

两种形态：

- 收起态：显示 host/标题和少量图标。
- 编辑态：展开 URL 输入、清空和确认按钮。

工具栏位置允许后续设置为顶部/底部，首版先固定底部以便单手操作。

### 实现路径

1. 新建 `view_browser_toolbar.xml`。
2. 删除/隐藏现有 settings/fullscreen FAB。
3. 接入 `BrowserUiState` 渲染按钮 enable、刷新/停止图标、标题和进度。
4. 接入输入解析和地址提交。
5. 更多按钮打开 Material Bottom Sheet。
6. 设置、全屏、分享、主页等入口移入 Sheet。
7. 处理 WindowInsets、IME 和横屏布局。

### 效果验证

- 控件不遮挡网页主要交互区域。
- 返回/前进/刷新/停止全部可用。
- 地址编辑时显示完整 URL，收起时只显示 host/标题。
- 横屏和手势导航下不贴边误触。
- 所有图标按钮触控面积至少 48dp。

---

## 模块 9：无干扰全屏与工具栏自动隐藏

### 目标

让用户专注网页内容，同时保持可发现的浏览控制和退出路径。

### 技术方案

定义工具栏状态机：

```text
VISIBLE → COLLAPSED → HIDDEN
```

触发规则：

- 页面开始加载、出现错误、用户向上滚动、轻触边缘 → 显示。
- 页面稳定后向下滚动 → 收起/隐藏。
- 地址编辑、Bottom Sheet、设置页 → 保持显示系统栏或必要控件。
- 全屏退出：边缘呼出工具栏中的退出按钮，或系统返回按既定规则处理。

WebView 滚动监听可先通过 `setOnScrollChangeListener`，设置方向阈值和节流，避免每个像素都触发动画。

### 实现路径

1. 抽出 `ToolbarVisibilityController`。
2. 定义滚动阈值、最短显示时间和动画时长。
3. 与沉浸式系统栏状态同步。
4. 替换现有常驻全屏 FAB。
5. 页面加载失败时强制显示工具栏。
6. 适配减少动态效果。

### 效果验证

- 下滑阅读时工具栏自然消失。
- 上滑/轻触后可立即找回。
- 全屏状态始终存在明确退出路径。
- 页面横向滑动、地图、Canvas 不频繁误触发隐藏。

---

## 模块 10：结构化错误与恢复

### 目标

将 WebView 原始错误转换为用户可理解、可操作、可测试的错误状态。

### 技术方案

```kotlin
sealed interface BrowserError {
    data object Offline : BrowserError
    data object DnsFailure : BrowserError
    data object TlsFailure : BrowserError
    data object SafeBrowsingBlocked : BrowserError
    data class Http(val statusCode: Int) : BrowserError
    data object NavigationBlocked : BrowserError
    data object Timeout : BrowserError
    data object RendererGone : BrowserError
    data class Unknown(val diagnosticCode: Int?) : BrowserError
}
```

错误 UI 使用玻璃卡片，但背景必须足够不透明以保证可读性。TLS 和 Safe Browsing 不提供继续访问按钮。

### 实现路径

1. 新增 BrowserError 和错误映射器。
2. Presenter 不再接收任意字符串，改为接收 BrowserError。
3. 错误 View 根据类型渲染标题、说明、主按钮和次按钮。
4. 引入页面加载 token/navigation id，避免旧页面错误覆盖新页面。
5. 加入可选超时计时器，页面完成/停止时取消。
6. RendererGone 使用模块 4 的 lastSafeUrl 恢复。

### 效果验证

- 离线、DNS、404、500、TLS、Safe Browsing 显示不同信息。
- TLS 和恶意站点没有绕过操作。
- 新页面开始加载后，旧页面迟到错误不覆盖当前内容。
- TalkBack 自动聚焦错误标题，并能读取操作按钮。

---

## 模块 11：独立设置页

### 目标

将设置 Dialog 改成独立设置场景，按浏览体验、外观、隐私、关于分组。

### 技术方案

首版使用 `NestedScrollView + LinearLayout/ConstraintLayout` 的分组玻璃卡片。设置项较多后改用 RecyclerView Preference-like Adapter，但不必引入 AndroidX Preference 的传统视觉。

设置模型扩展：

- 默认搜索引擎
- 启动行为：主页/上次页面/空白首页
- 全屏开关
- 工具栏自动隐藏
- 外观：跟随系统/浅色/深色
- 降低透明度
- 地址栏位置（后续）
- 桌面版网站（后续）

DataStore 继续作为偏好单一来源，写失败需要显示 Snackbar/错误状态，而不是静默失败。

### 实现路径

1. 新建 `view_settings.xml`。
2. 扩展 `BrowserSettings` 和 DataStore keys。
3. 设置项改为即时保存，无全局“保存”按钮。
4. 观察 DataStore Flow 并渲染当前值。
5. 写失败回滚 UI 并提示。
6. 从首页和浏览页进入，返回进入前场景。
7. 移除旧 `dialog_settings.xml` 和相关逻辑。

### 效果验证

- 修改设置即时生效并在重启后保留。
- 设置保存失败时 UI 不显示虚假的成功状态。
- 从首页/浏览页进入后能返回原位置。
- 大字体和横屏可完整滚动所有设置项。

---

## 模块 12：浏览数据与隐私控制

### 目标

让用户明确控制 Cookie、缓存、Web Storage、历史和会话数据。

### 技术方案

新增 `BrowserDataManager`，统一封装：

- `CookieManager.removeAllCookies`
- `WebStorage.getInstance().deleteAllData()`
- `WebView.clearCache(true)`
- `WebView.clearHistory()`
- 历史/收藏数据库操作
- Session DataStore 清理

清除操作必须显示影响范围和完成回调。一键清除不应误删收藏，除非用户明确勾选。

### 实现路径

1. 新增 BrowserDataManager。
2. 设置页增加各数据项和一键清理 Bottom Sheet。
3. 显示每项说明和确认。
4. 清理过程中禁用重复点击并显示进度。
5. 清理结束后重置 BrowserUiState、WebView 和首页数据。
6. 隐私模式作为后续子阶段：不记录历史，关闭会话后清理站点数据。

### 效果验证

- 清 Cookie 后登录状态失效。
- 清缓存不会错误删除收藏。
- 清历史后首页最近访问同步为空。
- 一键清理完成后无旧 Session 自动恢复。
- 操作失败或超时有提示。

---

## 模块 13：历史记录与最近访问

### 目标

提供本地历史记录，并在首页展示最近访问。

### 技术方案

引入 Room：

```text
HistoryEntity
- id
- url (indexed)
- title
- host
- faviconPath/hash（可选）
- visitedAt (indexed)
- visitCount
```

只在主框架页面成功完成后记录；隐私会话不记录；同 URL 在短时间内合并更新。

### 实现路径

1. 添加 Room、KSP/处理器所需配置（根据 AGP 内置 Kotlin兼容性验证）。
2. 创建数据库、Entity、DAO、Repository。
3. `onPageFinished` 成功后异步记录。
4. 首页读取最近 6 项。
5. 新增历史 Bottom Sheet/页面：按日期分组、搜索、单项删除、全部清除。
6. 建立数据库迁移测试。

### 效果验证

- 成功页面写入历史，错误页不写入。
- 最近访问顺序正确，重复访问合理合并。
- 删除历史后首页立即更新。
- 1000+ 历史记录时列表滚动流畅。

---

## 模块 14：收藏夹与首页快捷站点

### 目标

让用户保存常用网页，并将精选收藏固定到首页。

### 技术方案

Room 模型：

```text
BookmarkEntity
- id
- title
- url (unique or indexed)
- createdAt
- sortOrder
- pinnedToHome
```

首版不做复杂文件夹，可支持置顶首页和拖拽排序；文件夹放到后续版本。

### 实现路径

1. 增加 Bookmark Entity/DAO/Repository。
2. 更多菜单增加“收藏当前页面”。
3. 添加编辑名称、URL 和置顶开关的 Bottom Sheet。
4. 首页展示最多 8 个置顶收藏。
5. 收藏列表支持编辑、删除、排序。
6. favicon 可先使用首字母/域名图标，后续使用 Coil 加载缓存。

### 效果验证

- 收藏当前页后首页可立即显示。
- 重启后收藏保留。
- 重复 URL 有明确更新/已收藏提示。
- 删除、排序和置顶结果稳定。

---

## 模块 15：浏览辅助操作

### 目标

补齐日常高频但不应占据主界面的操作。

### 技术方案

通过浏览页“更多”Bottom Sheet 提供：

- 分享页面
- 复制 URL
- 外部浏览器打开
- 页内查找
- 桌面版网站
- 收藏
- 设置
- 回首页

页内查找使用 `findAllAsync`、`findNext` 和 `WebView.FindListener`。桌面版网站首版只作用于当前会话，后续支持站点级持久化。

### 实现路径

1. 新增浏览更多菜单 Bottom Sheet。
2. 实现分享和复制。
3. 使用 AndroidX Browser/系统 chooser 提供外部打开。
4. 新增页内查找悬浮条。
5. 新增桌面版 UA 切换并重载当前页。
6. 对所有操作补充无障碍文案和操作反馈。

### 效果验证

- 分享内容包含页面标题和 URL。
- 页内查找显示匹配数量并可前后跳转。
- 外部打开不存在处理器时不崩溃。
- 桌面版切换后刷新，退出/重进遵循设计的持久化范围。

---

## 模块 16：下载、文件上传与网页媒体兼容

### 目标

在保持权限克制的前提下，支持现代网站常见的下载、上传和全屏视频流程。

### 技术方案

分三个子阶段：

1. 下载：WebView `DownloadListener` + Android `DownloadManager`。
2. 上传：`WebChromeClient.onShowFileChooser` + Activity Result API。
3. 视频：`onShowCustomView/onHideCustomView` 管理全屏播放器。

权限策略：

- 不默认授予摄像头、麦克风和定位。
- 文件选择使用系统 Picker，避免广泛存储权限。
- 下载前显示来源域名、文件名和 MIME 类型。
- `blob:` 下载单独评估，不通过不安全 JavaScript Bridge 快速实现。

### 实现路径

1. 增加下载确认 Sheet 和 DownloadManager 封装。
2. 监听下载完成并提供打开文件 Intent。
3. 实现系统文件选择器上传。
4. 如网站请求相机/麦克风，建立按次确认和 Android 权限映射。
5. 实现视频 Custom View 与返回键优先级。
6. 测试 OAuth popup；仅在确有需求时实现受控临时 WebView。

### 效果验证

- 常见 PDF/图片/压缩包可下载并打开。
- 文件上传可选择文件并返回网页。
- 用户拒绝权限后网页收到拒绝，应用不崩溃。
- 全屏视频能进入、旋转和退出，系统栏恢复正确。

---

## 模块 17：轻量多标签页与隐私会话（后置）

### 目标

在不破坏简洁与内存控制的前提下，提供有限多页面会话。

### 技术方案

先做产品验证，再决定是否开发。建议限制为 4～8 个标签，非当前标签保存轻量状态或冻结 WebView，不保持无限 WebView 实例。

```text
BrowserTab
- id
- url
- title
- isPrivate
- lastActiveAt
- serializedState（可选）
```

隐私标签：不写历史，关闭后清理其会话数据。必须明确 System WebView Cookie 进程级共享带来的隔离限制。

### 实现路径

1. 先通过用户实际使用确认多标签需求。
2. 抽象单标签 BrowserSession。
3. 实现标签列表和切换状态机。
4. 限制常驻 WebView 数量，建立 LRU 回收。
5. 实现普通标签恢复。
6. 最后实现隐私标签，并在 UI 明确其能力边界。

### 效果验证

- 多标签切换不会丢失当前 URL。
- 内存压力下回收后可按安全 URL 降级恢复。
- 关闭隐私标签后不出现在历史中。
- 低端设备标签上限下不发生明显卡顿或频繁 Renderer 崩溃。

---

## 模块 18：测试、性能、无障碍与发布

### 目标

形成覆盖逻辑、UI、WebView 行为、视觉降级和发布安全的质量体系。

### 技术方案

测试分层：

- JVM：InputResolver、UrlPolicy、NavigationPolicy、状态机、错误映射。
- Room：DAO、迁移和历史合并规则。
- Espresso：三场景导航、设置持久化、工具栏行为。
- 真机/模拟器：WebView、下载上传、TLS、Renderer、视频。
- 性能：启动、页面滚动、模糊工具栏、内存和标签回收。
- 无障碍：TalkBack、字体放大、对比度、48dp 触控目标、减少动画。

### 实现路径

1. 增加 test/androidTest 依赖和基础测试 Runner。
2. 为每个模块建立验收测试文件。
3. 建立本地测试网页或测试服务器方案，覆盖重定向、下载、上传、HTTP 错误等。
4. 建立 API 26、API 31、目标 API 设备矩阵。
5. 使用 Profiler/FrameMetrics 观察 WebView + blur 性能。
6. Release 构建确认 WebView 调试关闭、混淆可用、无敏感日志。
7. 建立开源许可证页面和依赖清单。

### 效果验证 / 发布验收矩阵

- 冷启动：首页出现且可输入。
- 页面加载：URL、搜索、重定向、前进后退。
- 恢复：旋转、进程重建、Renderer Gone。
- 错误：离线、DNS、TLS、404、500、Safe Browsing。
- 视觉：浅色、深色、API 26 降级、API 31+ 模糊。
- 设备：竖屏、横屏、刘海屏、三键/手势导航。
- 无障碍：TalkBack、大字体、减少动画。
- 数据：历史、收藏、清理、隐私会话。

### 完成标准

- 所有 P0/P1 模块验收项通过。
- Release APK 能安装、启动、浏览和恢复。
- 无阻断性 Lint/崩溃/ANR。
- 第三方依赖许可证和隐私说明完整。

---

# 三、推荐的逐步开发批次

## 批次 A：基础可开发状态

1. 模块 1：构建环境与质量基线
2. 模块 2：三场景状态与应用导航
3. 模块 3：输入解析与导航策略
4. 模块 4：浏览状态与会话恢复
5. 模块 5：外部链接策略

**交付效果**：有独立首页入口，能安全进入浏览页，状态和恢复可靠。

## 批次 B：产品核心体验

1. 模块 6：首页
2. 模块 7：Liquid Glass 设计系统
3. 模块 8：浏览页工具栏
4. 模块 9：无干扰全屏
5. 模块 10：结构化错误

**交付效果**：形成现代、简洁、全屏无干扰的浏览器核心体验。

## 批次 C：设置与个人数据

1. 模块 11：设置页
2. 模块 12：隐私和数据清理
3. 模块 13：历史
4. 模块 14：收藏
5. 模块 15：浏览辅助操作

**交付效果**：具备个人日常长期使用能力。

## 批次 D：兼容与高级能力

1. 模块 16：下载/上传/视频
2. 模块 17：多标签/隐私会话（按需求决定）
3. 模块 18：完整质量与发布体系

**交付效果**：完善网站兼容性并达到可发布质量。

---

# 四、计划维护规则

1. `docs/development-plan.md` 记录稳定的技术路线和模块验收标准。
2. `docs/TODO.md` 记录实时完成状态和下一步任务。
3. 开始某模块时，在 TODO 中将对应项标记为 `[~]`。
4. 仅当代码、测试、文档和验收均完成时标记 `[x]`。
5. 若实现与计划不同，先更新本计划的技术决策，再修改代码。
6. 每完成一个模块，记录：
   - 修改文件
   - 新增依赖
   - 自动测试结果
   - 手工设备验证结果
   - 已知限制
7. P2 模块必须由实际使用需求驱动，不为追求“功能齐全”而默认开发。
