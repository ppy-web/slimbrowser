# SlimBrowser Deep Link 配置指南

## 支持的 Deep Link 类型

### 1. URL Scheme（自定义协议）
- **格式**: `slimbrowser://open?url=<encoded-url>`
- **示例**: `slimbrowser://open?url=https%3A%2F%2Fwww.example.com`
- **特点**: 最通用，Android/iOS 均支持，无需域名验证
- **缺点**: 未安装 App 时会报错；部分浏览器可能拦截

### 2. Android App Links（安卓官方）
- **格式**: `https://www.slimbrowser.com/...`
- **域名**: `slimbrowser.com` / `www.slimbrowser.com`
- **要求**: 
  - Android 6.0 (API 23) 及以上
  - 需在域名根目录放置 `.well-known/assetlinks.json`
  - 需使用发布签名密钥的 SHA-256 指纹
- **特点**: 安全性高，无需用户选择，直接唤起 App

### 3. Universal Links（iOS 官方 / 跨平台）
- **格式**: `https://www.slimbrowser.com/u/...`
- **路径前缀**: `/u/`
- **要求**:
  - iOS 9 及以上
  - 需在域名 `/.well-known/apple-app-site-association` 配置关联
  - 需在 Apple Developer 启用 Associated Domains
- **特点**: 已安装 App 直接唤起，未安装则打开网页

---

## 部署步骤

### Android App Links 部署

1. **获取签名密钥指纹**:
   ```bash
   # Debug 密钥
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey

   # Release 密钥
   keytool -list -v -keystore your-release-key.keystore -alias your-alias
   ```

2. **更新 `assetlinks.json`**:
   - 将 `docs/deeplink/assetlinks.json` 中的 `sha256_cert_fingerprints` 替换为实际指纹
   - 将文件放置到 `https://www.slimbrowser.com/.well-known/assetlinks.json`

3. **验证配置**:
   ```bash
   # 使用 Google 提供的工具验证
   curl -H "Accept: application/json" https://www.slimbrowser.com/.well-known/assetlinks.json
   ```

### iOS Universal Links 部署

1. **更新 `apple-app-site-association`**:
   - 将 `TEAM_ID` 替换为实际的 Apple Developer Team ID
   - 将文件放置到 `https://www.slimbrowser.com/.well-known/apple-app-site-association`

2. **Xcode 配置**:
   - 在项目的 Signing & Capabilities 中添加 Associated Domains
   - 添加 `applinks:www.slimbrowser.com`

3. **验证配置**:
   ```bash
   curl https://www.slimbrowser.com/.well-known/apple-app-site-association
   ```

---

## URL Scheme 使用示例

```kotlin
// 打开指定 URL
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("slimbrowser://open?url=https://example.com"))
startActivity(intent)

// 打开搜索
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("slimbrowser://open?url=https://www.google.com/search?q=test"))
startActivity(intent)
```

---

## 注意事项

1. **域名验证**: Android App Links 和 Universal Links 均需域名验证，确保 `.well-known/` 文件可公开访问
2. **签名一致性**: `assetlinks.json` 中的指纹必须与 App 签名一致
3. **测试工具**: 
   - Android: [Google Digital Asset Links Tool](https://developers.google.com/digital-asset-links/tools/generator)
   - iOS: [Apple App Site Association Validator](https://app-site-association.cdn-apple.com/a/v1/your-app-id)
4. **launchMode**: MainActivity 使用 `singleTask` 模式，确保 Deep Link 可正确路由到已有实例
