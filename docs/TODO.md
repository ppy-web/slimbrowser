# SlimBrowser 产品 TODO

> 详细技术路线、实施步骤和逐模块验收标准参见 [`development-plan.md`](development-plan.md)。本文件用于维护实时完成状态。
>
> 目标：个人使用的简洁 Android 浏览器，围绕三个主要场景构建：**首页、浏览页、设置页**。
>
> 设计关键词：全屏无干扰、现代化 Liquid Glass、轻量、快速、可恢复、隐私可控。
>
> 状态：
> - `[x]` 已完成并可复用
> - `[~]` 已有基础但需要完善
> - `[ ]` 尚未实现
>
> 优先级：
> - **P0**：完成核心产品闭环后才能称为可用浏览器
> - **P1**：提升日常使用体验
> - **P2**：差异化和长期增强

## 2026-08-30 开发与验收记录

- [x] Renderer 恢复现在显式区分“已开始加载”和“已成功提交”的地址；崩溃重建后优先恢复最后成功页面，再降级到持久会话或主页，并补充 JVM 回归测试。
- [x] WebView 销毁期间使用可接管晚到 Renderer 回调的终止客户端，避免销毁竞态并消除 `MissingOnRenderProcessGone` Lint 告警。
- [x] 首页与设置页改为按当前场景渲染，避免浏览进度回调反复重建首页快捷入口；“降低透明度”偏好已覆盖全部玻璃卡片。
- [x] 工具栏动画同时遵循应用内“减少动态效果”和系统 Animator 开关；首页快捷/最近入口统一使用玻璃按钮样式，兼容 URI 的输入提示也已与实际策略同步。
- [x] 场景淡入和进度动画已接线并共享减少动画策略；标题语义改用 AndroidX 兼容接口覆盖 API 26–27，同时消除相关 `UnusedAttribute` Lint 告警。
- [x] API 37.1 设备测试新增显式 `file:` URI、favicon/历史、隐私退出清理和关键控件无障碍断言，设备测试由 1 项扩展为 4 项。
- [x] favicon 回调按页面 URL/导航代次关联，新导航会清除旧图标；文件采用唯一临时文件原子替换，历史重访在没有新图标时保留已有 favicon。
- [x] 内置离线页使用应用资源 favicon，API 37.1 设备测试已验证“页面加载 → 历史写入 → 私有 favicon 文件落盘”完整链路。
- [x] API 37.1 自动化已验证隐私页面不写历史/会话、Activity 正常退出清除 Cookie，以及首页/设置/浏览关键控件满足 48dp 和可访问标签要求。
- [x] 修改后重新通过 JVM 单测、Debug Lint、Debug/Release 构建，以及 API 37.1 模拟器 `connectedDebugAndroidTest`。

## 2026-08-23 开发与验收记录

- [x] 已恢复正式开发计划，并按用户 2026-08-23 指示切换为协议兼容优先：显式 URI 尽力打开，私网与 HTTP 不再默认阻断，未知协议交给系统处理器尝试。
- [x] 模块 1–16 的 P0/P1 代码路径已接线；模块 17 属于按需 P2，未纳入本轮交付。
- [x] JVM 单测、Debug/Release 构建、Debug Lint 和 API 37.1 设备冒烟均已通过；设备测试覆盖首页与设置场景、`http://example.com`、自动隐藏工具栏边缘呼出，以及内置离线页中的链接/图片长按菜单。特殊协议的协议分流由 JVM 单测覆盖，仍应在安装目标处理器的实体设备上补充端到端验证。
- [~] 发布门槛剩余：API 26 真实设备/模拟器、TalkBack/大字体、复杂下载上传与 TLS/Safe Browsing/Renderer 故障注入。未通过这些验证前，不把对应“已实现”模块标为最终发布完成。

## 0. 当前基础盘点

- [x] Kotlin + XML Views + ViewBinding
- [x] Android System WebView + AndroidX WebKit
- [x] Preferences DataStore 保存主页地址和全屏状态
- [x] 首次启动配置主页
- [x] URL/URI 规范化与兼容分流（裸主机默认 HTTPS）
- [x] 尽力支持明文 HTTP、混合内容、文件和内容 URI；Web 权限不再由浏览器一律拒绝
- [x] Safe Browsing、TLS 错误和 Renderer 崩溃基础处理
- [x] 返回键优先回退 WebView 历史
- [x] 页面错误覆盖层：按离线、DNS、TLS、Safe Browsing、HTTP、策略、超时和 Renderer 映射操作
- [x] 全屏与边缘可呼出玻璃工具栏
- [x] 状态恢复：WebView Bundle 优先，安全会话 URL 作为降级路径
- [x] 当前地址栏、页面标题和站点安全状态
- [x] 浏览历史、收藏夹、下载管理、文件上传
- [ ] 标签页或最小多页面会话
- [x] 独立首页和设置页

## 1. P0：产品定位与核心架构

- [x] 将产品定位明确为“个人轻量浏览器”，不再默认按单站点容器设计
- [x] 引入 `BrowserUiState`，统一维护 URL、标题、加载进度、前进后退状态和全屏状态
- [x] 引入 `NavigationPolicy`，统一处理网页 URI、外部链接、重定向和搜索输入；局域网地址始终遵循用户输入优先策略
- [x] 引入 `ExternalLinkPolicy`，明确 `mailto:`、`tel:`、`intent:`、应用商店和未知 scheme 的行为
- [x] 显式记录最后一个成功提交的主框架 URL；Renderer 重建后优先恢复该地址，再降级到持久会话或主页
- [x] 增加轻量 `BrowserSession` 持久化，WebView Bundle 恢复失败时按安全 URL 降级恢复
- [x] 统一文档、测试和实现中的端口策略（合法 HTTPS 端口 1..65535）
- [x] 加强 IPv4、IPv6、尾点域名、Unicode 同形域名和畸形主机测试
- [x] 建立 `BrowserError` 结构化错误模型：离线、DNS、TLS、Safe Browsing、HTTP、策略拒绝、超时、Renderer 崩溃

## 2. P0：首页场景

首页是启动后的轻量入口，不直接强迫用户进入设置页。

- [x] 新增独立 Home UI，而不是只显示 WebView 或首次配置对话框
- [x] 首页 Liquid Glass 搜索/地址输入框
- [x] 支持 URL 输入和搜索关键词输入
- [x] 可配置默认搜索引擎（百度、Bing、DuckDuckGo）
- [x] 最近访问入口（本地最近 6 项）
- [x] 收藏快捷方式入口
- [x] “继续上次浏览”入口
- [x] 无历史数据时显示简洁空状态和引导
- [x] 首页背景、快捷方式和搜索引擎设置可在设置页调整
- [x] 首页与浏览控件动画保持短、轻，并同时支持应用偏好和系统 Animator 关闭状态

## 3. P0：浏览页场景

### 顶部/底部最小导航工具栏

- [x] 地址栏：显示当前 URL/host，点击后聚焦并全选
- [x] HTTPS 安全图标和当前站点 host
- [x] 返回、前进、刷新/停止按钮
- [x] 加载进度与页面标题同步
- [x] 输入 URL 自动补全 HTTPS
- [x] 输入关键词交给搜索引擎
- [x] 键盘 IME Done 直接访问
- [x] 页面完成后自动隐藏工具栏，轻触/上滑重新显示
- [x] 全屏模式下工具栏支持边缘呼出，不遮挡网页内容
- [x] 工具栏悬浮于 WebView 上方，使用半透明玻璃卡片而非固定大面积背景

### 浏览行为

- [x] 当前页面真实 URL 在 `onPageStarted`、`onPageFinished`、重定向和历史更新时同步
- [~] 页面标题和 favicon 同步到浏览状态（API 37.1 自动化已验证私有文件与历史关联，仍待实体设备验证）
- [x] WebView `canGoBack` / `canGoForward` 更新按钮可用状态
- [x] 页面加载超时和取消加载
- [x] 页面内查找
- [x] 页面缩放、字体大小和“请求桌面版网站”
- [x] 分享当前页面、复制 URL、使用外部浏览器打开
- [x] 长按链接和图片提供复制/分享/打开操作（API 37.1 内置离线页已验证链接和图片菜单）
- [~] 统一处理下载、`blob:`、文件上传和受控外部 Intent（`blob:` 下载待特定站点适配）
- [x] 设计 OAuth/登录跳转策略，不盲目放开多窗口

### 浏览页错误与恢复

- [x] 为每种错误提供明确标题、说明和可执行按钮
- [x] 无网络：重试、打开系统网络设置
- [x] TLS/Safe Browsing：按用户请求继续尝试，保留 WebView/系统原生能力
- [x] HTTP 错误：返回、重试、复制 URL
- [~] Renderer 崩溃：创建新 WebView 后由“重试”恢复最后安全 URL（已提供同路径手工故障注入，待真实 Renderer 崩溃验证）
- [x] 错误覆盖层支持无障碍焦点和 Live Region

## 4. P1：设置页场景

设置页采用分组卡片，避免复杂的传统设置列表。

### 浏览体验

- [x] 主页 URL 编辑
- [x] 沉浸式全屏开关
- [x] 默认搜索引擎
- [x] 启动时打开：主页 / 上次页面 / 空白页
- [x] 地址栏位置：顶部 / 底部
- [x] 工具栏自动隐藏
- [x] 默认桌面版网站
- [x] 字体大小和页面缩放
- [x] 深色网页模式

### 隐私与数据

- [x] 清除缓存
- [x] 清除 Cookie
- [x] 清除 Web Storage
- [x] 清除历史记录
- [x] 清除表单数据
- [x] 一键清除全部浏览数据
- [~] 隐私模式（API 37.1 自动化已验证不新增历史/持久会话和退出清 Cookie，仍待实体设备验证缓存/存储）
- [~] 退出时清理隐私会话（API 37.1 已验证 Activity 正常结束；进程被系统直接杀死仍需平台矩阵验证）
- [x] 第三方 Cookie 策略说明
- [x] 站点权限策略（不再由浏览器代码一律拒绝，最终由 Android 系统权限决定）

### 关于与诊断

- [x] WebView/浏览器版本信息
- [x] 开源许可清单
- [x] 隐私说明和信任模型
- [x] 导出诊断信息（不包含 Cookie、密码和完整浏览历史）
- [~] 手工测试入口：支持清空会话、预览离线/HTTP 错误页、同路径重建 WebView 的 Renderer 恢复测试，以及内置离线兼容性测试页；真实 Renderer 崩溃仍待设备故障注入验证

## 5. P1：个人数据模块

- [~] `HistoryRepository`：标题、URL、访问时间、隐私会话标记和 favicon URI（API 37.1 已验证持久化链路，待实体设备列表验证）
- [x] 历史按今天/昨天/更早分组
- [x] 历史搜索、单项删除、全部清除
- [~] `BookmarkRepository`：添加、编辑、删除、排序和主页置顶（文件夹后置）
- [x] 收藏快捷方式显示在首页
- [x] `DownloadRepository`：下载状态、失败重试、文件打开、删除记录
- [x] 使用 Room 保存结构化历史、收藏和下载元数据
- [x] 使用 DataStore 继续保存少量偏好，不把结构化列表塞进 DataStore
- [x] 数据库迁移和备份策略

## 6. P1：Liquid Glass 视觉系统

先用 Android 原生能力实现，不急于引入重量级 UI 框架。

- [x] 定义玻璃设计 tokens：背景透明度、模糊半径、描边、圆角、阴影和强调色
- [x] 建立 Light/Dark 两套玻璃主题
- [x] 首页搜索框、快捷方式/最近入口、浏览工具栏和设置分组使用统一玻璃视觉
- [x] 玻璃容器使用弱渐变和细描边，并提供降低透明度的高不透明降级
- [x] WebView 内容区不施加滤镜；模糊仅作用于控制层的一次性背景采样
- [x] Android 12+ 使用 RenderEffect 模糊采样；API 26-30 使用半透明渐变降级
- [ ] 如原生模糊效果不足，再评估 `Dimezis/BlurView`
- [x] FAB 已改为可自动收起和边缘呼出的轻量悬浮工具条
- [x] 设置采用独立场景保持导航连续性；历史、收藏、下载及浏览操作使用 Bottom Sheet/Dialog
- [x] 加入触摸反馈、场景短淡入、工具栏滑入/滑出和进度动画，并统一支持减少动画
- [x] 所有应用动画支持应用内减少动态效果、系统 Animator 关闭和零动画降级
- [~] 已增加高不透明模式、48dp 触摸目标、动态 TalkBack 标签、兼容标题语义和错误 Live Region；API 37.1 自动断言已通过，焦点顺序及 TalkBack/大字体仍待人工验收

## 7. P2：特色能力

- [ ] 多标签页：新建、切换、关闭、恢复和标签页计数
- [ ] 隐私标签页与普通标签页隔离
- [ ] 阅读模式
- [ ] 广告/跟踪域名拦截（先做可解释的轻量规则，不直接集成大型规则库）
- [ ] 稍后阅读/离线页面
- [ ] 页面截图和导出 PDF
- [ ] 手势前进后退、下拉刷新
- [ ] 二维码扫描打开 HTTPS 地址
- [ ] 自定义首页背景和快捷方式
- [ ] PWA/添加到桌面能力评估

## 8. 建议采用的成熟开源组件

### 当前已使用、建议保留

- `AndroidX Core KTX`：Kotlin Android 基础扩展
- `AndroidX AppCompat`：兼容 Activity 和主题
- `AndroidX Activity`：返回键与生命周期支持
- `AndroidX CoordinatorLayout`：悬浮控件和滚动联动
- `AndroidX Lifecycle`：生命周期 CoroutineScope
- `AndroidX WebKit`：Safe Browsing、WebView 兼容 API 和特性检测
- `AndroidX DataStore Preferences`：少量偏好配置
- `Material Components for Android`：Material 3 XML 控件、Dialog、Switch、TextInput
- `JUnit 4`：当前 URL Policy JVM 测试

### 建议评估后引入

| 组件 | 用途 | 建议 |
|---|---|---|
| AndroidX Room | 历史、收藏、下载元数据 | P1 必选；比 DataStore 适合结构化列表 |
| AndroidX RecyclerView | 历史、收藏、下载、标签页列表 | P1 引入，稳定且适合 XML Views |
| AndroidX ConstraintLayout | 首页/工具栏响应式布局 | P0/P1 可引入，减少嵌套布局 |
| AndroidX Fragment | 首页、浏览页、设置页场景拆分 | 页面复杂后再引入；首版也可单 Activity 状态切换 |
| AndroidX Navigation | 三场景导航和返回栈 | 若使用 Fragment，建议引入；不必和当前 MVP 同时大改 |
| `Dimezis/BlurView` | API 26+ 玻璃背景模糊降级 | 视觉验证后再引入；需处理 WebView 性能和许可证 |
| AndroidX Palette | 从壁纸/站点截图提取强调色 | P2，可选；不应阻塞核心浏览流程 |
| Coil | favicon、收藏图标和首页图片 | P1 可选；图片加载简单时可先不用 |
| Lottie Android | 首页/错误页轻量动效 | P1/P2 可选；避免大量动画和 APK 体积膨胀 |
| AndroidX Browser | Custom Tabs 外部打开网页 | P1；适合作为“外部浏览器/安全打开”出口 |
| WorkManager | 下载重试、后台清理、定时维护 | 下载和离线能力加入后再引入 |
| AndroidX Security Crypto | 本地敏感配置加密 | 若保存令牌/诊断信息才引入；不要保存密码 |
| Espresso/UI Automator | 首页、工具栏、设置页 UI 测试 | P1，设备环境可用后加入 |
| Robolectric | 部分 Android 单元测试 | 谨慎使用；WebView 行为仍需真机测试 |

### 不建议当前阶段引入

- 完整 Compose 重写：当前项目是 XML/ViewBinding，混用会增加迁移成本
- 大型浏览器内核：Android System WebView 已满足当前目标
- 重型动画框架：会削弱简洁、快速和全屏无干扰目标
- 未审查的广告拦截规则库：规则更新、性能和许可证需要独立评估
- 直接复制其他浏览器的大量业务代码：与个人轻量定位不符

## 9. 验证与发布门槛

- [x] 补齐 `gradle-wrapper.jar`，确保可重复构建
- [x] 运行 `testDebugUnitTest`
- [x] 运行 `lintDebug`
- [x] 运行 `assembleDebug`
- [ ] API 26 真机/模拟器验证降级玻璃效果
- [x] API 31+ 验证系统模糊和沉浸式全屏（当前 API 37.1 模拟器）
- [ ] 验证低端设备 WebView 滚动和工具栏动画性能
- [ ] 验证横屏、挖孔屏、手势导航和无障碍
- [ ] 验证离线、DNS、TLS、HTTP、Safe Browsing 和 Renderer 恢复
- [x] 建立第三方开源许可证页
- [ ] 发布前固定依赖版本并检查许可证、维护状态和安全公告
