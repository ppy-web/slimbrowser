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

## 0. 当前基础盘点

- [x] Kotlin + XML Views + ViewBinding
- [x] Android System WebView + AndroidX WebKit
- [x] Preferences DataStore 保存主页地址和全屏状态
- [x] 首次启动配置主页
- [x] HTTPS URL 规范化和基础校验
- [x] 禁止明文流量、Mixed Content、文件访问和 Web 权限
- [x] Safe Browsing、TLS 错误和 Renderer 崩溃基础处理
- [x] 返回键优先回退 WebView 历史
- [~] 页面错误覆盖层：已有，但错误类型、文案和恢复操作需要结构化
- [~] 全屏：已有沉浸式模式，但交互控件和 Liquid Glass 视觉需要重做
- [~] 状态恢复：已有 WebView Bundle 恢复，但缺少真实当前 URL 的轻量降级恢复
- [ ] 当前地址栏、页面标题和站点安全状态
- [ ] 浏览历史、收藏夹、下载管理、文件上传
- [ ] 标签页或最小多页面会话
- [ ] 独立首页和设置页

## 1. P0：产品定位与核心架构

- [ ] 将产品定位明确为“个人轻量浏览器”，不再默认按单站点容器设计
- [ ] 引入 `BrowserUiState`，统一维护 URL、标题、加载进度、前进后退状态和全屏状态
- [ ] 引入 `NavigationPolicy`，统一处理 HTTPS、外部链接、重定向和搜索输入
- [ ] 引入 `ExternalLinkPolicy`，明确 `mailto:`、`tel:`、`intent:`、应用商店和未知 scheme 的行为
- [ ] 记录最后一个通过策略的主框架 URL，修复 Renderer 重建后重试回主页的问题
- [ ] 增加轻量 `BrowserSession` 持久化，WebView Bundle 恢复失败时按安全 URL 降级恢复
- [ ] 统一文档、测试和实现中的端口策略（普通浏览器允许 HTTPS 合法端口，或明确只允许 443）
- [ ] 加强 IPv4、IPv6、尾点域名、Unicode 同形域名和畸形主机测试
- [ ] 建立 `BrowserError` 结构化错误模型：离线、DNS、TLS、Safe Browsing、HTTP、策略拒绝、超时、Renderer 崩溃

## 2. P0：首页场景

首页是启动后的轻量入口，不直接强迫用户进入设置页。

- [ ] 新增独立 Home UI，而不是只显示 WebView 或首次配置对话框
- [ ] 首页 Liquid Glass 搜索/地址输入框
- [ ] 支持 URL 输入和搜索关键词输入
- [ ] 可配置默认搜索引擎（默认提供一个安全的 HTTPS 搜索 URL 模板）
- [ ] 最近访问入口（首版可只保留本地最近 6 项）
- [ ] 收藏快捷方式入口
- [ ] “继续上次浏览”入口
- [ ] 无历史数据时显示简洁空状态和引导
- [ ] 首页背景、快捷方式和搜索引擎设置可在设置页调整
- [ ] 首页动画保持短、轻、可跳过，支持系统“减少动态效果”偏好

## 3. P0：浏览页场景

### 顶部/底部最小导航工具栏

- [ ] 地址栏：显示当前 URL/host，点击后聚焦并全选
- [ ] HTTPS 安全图标和当前站点 host
- [ ] 返回、前进、刷新/停止按钮
- [ ] 加载进度与页面标题同步
- [ ] 输入 URL 自动补全 HTTPS
- [ ] 输入关键词交给搜索引擎
- [ ] 键盘 IME Done 直接访问
- [ ] 页面完成后自动隐藏工具栏，轻触/上滑重新显示
- [ ] 全屏模式下工具栏支持边缘呼出，不遮挡网页内容
- [ ] 工具栏悬浮于 WebView 上方，使用半透明玻璃卡片而非固定大面积背景

### 浏览行为

- [ ] 当前页面真实 URL 在 `onPageStarted`、`onPageFinished`、重定向和历史更新时同步
- [ ] 页面标题和 favicon 同步到浏览状态
- [ ] WebView `canGoBack` / `canGoForward` 更新按钮可用状态
- [ ] 页面加载超时和取消加载
- [ ] 页面内查找
- [ ] 页面缩放、字体大小和“请求桌面版网站”
- [ ] 分享当前页面、复制 URL、使用外部浏览器打开
- [ ] 长按链接和图片提供复制/分享/打开操作
- [ ] 统一处理下载、`blob:`、文件上传和受控外部 Intent
- [ ] 设计 OAuth/登录跳转策略，不盲目放开多窗口

### 浏览页错误与恢复

- [ ] 为每种错误提供明确标题、说明和可执行按钮
- [ ] 无网络：重试、打开系统网络设置
- [ ] TLS/Safe Browsing：说明已阻止，不提供绕过按钮
- [ ] HTTP 错误：返回、重试、复制 URL
- [ ] Renderer 崩溃：按最后安全 URL恢复
- [ ] 错误覆盖层支持无障碍焦点和 Live Region

## 4. P1：设置页场景

设置页采用分组卡片，避免复杂的传统设置列表。

### 浏览体验

- [~] 主页 URL 编辑
- [x] 沉浸式全屏开关
- [ ] 默认搜索引擎
- [ ] 启动时打开：主页 / 上次页面 / 空白页
- [ ] 地址栏位置：顶部 / 底部
- [ ] 工具栏自动隐藏
- [ ] 默认桌面版网站
- [ ] 字体大小和页面缩放
- [ ] 深色网页模式

### 隐私与数据

- [ ] 清除缓存
- [ ] 清除 Cookie
- [ ] 清除 Web Storage
- [ ] 清除历史记录
- [ ] 清除表单数据
- [ ] 一键清除全部浏览数据
- [ ] 隐私模式
- [ ] 退出时清理隐私会话
- [ ] 第三方 Cookie 策略说明
- [ ] 站点权限策略（当前默认拒绝，未来支持按站点例外）

### 关于与诊断

- [ ] WebView/浏览器版本信息
- [ ] 开源许可清单
- [ ] 隐私说明和信任模型
- [ ] 导出诊断信息（不包含 Cookie、密码和完整浏览历史）
- [ ] 手工测试入口：清空会话、模拟错误页、恢复测试

## 5. P1：个人数据模块

- [ ] `HistoryRepository`：标题、URL、favicon、访问时间、隐私会话标记
- [ ] 历史按今天/昨天/更早分组
- [ ] 历史搜索、单项删除、全部清除
- [ ] `BookmarkRepository`：添加、编辑、删除、排序和文件夹
- [ ] 收藏快捷方式显示在首页
- [ ] `DownloadRepository`：下载状态、失败重试、文件打开、删除记录
- [ ] 使用 Room 保存结构化历史、收藏和下载元数据
- [ ] 使用 DataStore 继续保存少量偏好，不把结构化列表塞进 DataStore
- [ ] 数据库迁移和备份策略

## 6. P1：Liquid Glass 视觉系统

先用 Android 原生能力实现，不急于引入重量级 UI 框架。

- [ ] 定义玻璃设计 tokens：背景透明度、模糊半径、描边、圆角、阴影和强调色
- [ ] 建立 Light/Dark 两套玻璃主题
- [ ] 首页搜索框、快捷方式卡片、浏览工具栏和设置分组使用统一玻璃容器
- [ ] 玻璃容器使用弱渐变和细描边，保证文字对比度
- [ ] WebView 内容区保持完全不加滤镜，避免影响网页渲染性能
- [ ] 优先使用 Android 12+ RenderEffect/系统模糊；API 26-30 使用半透明渐变降级
- [ ] 如原生模糊效果不足，再评估 `Dimezis/BlurView`
- [ ] FAB 改为轻量悬浮工具条，避免遮挡网页右下角内容
- [ ] 设置页使用 Bottom Sheet/Dialog 转场，保持场景连续性
- [ ] 加入触摸反馈、页面淡入、工具栏滑入/滑出和进度动画
- [ ] 所有动画支持减少动态效果和低端设备降级
- [ ] 增加无障碍对比度、触摸目标尺寸、TalkBack 标签和焦点顺序

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

- [ ] 补齐 `gradle-wrapper.jar`，确保可重复构建
- [ ] 运行 `testDebugUnitTest`
- [ ] 运行 `lintDebug`
- [ ] 运行 `assembleDebug`
- [ ] API 26 真机/模拟器验证降级玻璃效果
- [ ] API 31+ 验证系统模糊和沉浸式全屏
- [ ] 验证低端设备 WebView 滚动和工具栏动画性能
- [ ] 验证横屏、挖孔屏、手势导航和无障碍
- [ ] 验证离线、DNS、TLS、HTTP、Safe Browsing 和 Renderer 恢复
- [ ] 建立第三方开源许可证页
- [ ] 发布前固定依赖版本并检查许可证、维护状态和安全公告
