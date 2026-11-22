# 墨记 MochiLedger

**墨记 (MochiLedger)** 是一款基于 **Kotlin Multiplatform + Compose Multiplatform** 构建的开源跨平台记账软件，原生支持 **Windows** 与 **Android** 双端数据互通，支持 **WebDAV 备份/双向同步** 与 **自建同步服务端（Ktor + Docker）**，并在 Android 端内置 **LSPosed 自动记账模块**（监听微信与支付宝支付通知及收付款事件）。

UI 视觉与交互设计融合参考了：
- [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) — Compose Multiplatform HyperOS/MIUIX 风格组件库与 G2 连续圆角（Squircle）
- [feice11/mochi-compose](https://github.com/feice11/mochi-compose) — iOS 风格弹性动效、液态玻璃层次感与 G2 连续圆角卡片

---

## 核心特性

- **Windows & Android 双端互通**：共用同一套 SQLite Schema 与 `.mochi.json` 跨端快照格式，支持 JSON / CSV 导入导出。
- **WebDAV 双向合并同步与备份**：内置坚果云、Nextcloud、Alist 预设，支持 LWW（Last-Write-Wins）按记录 UUID 冲突合并与时间戳快照备份。
- **独立同步服务端**：已拆分至独立仓库 [ackd7/MochiLedger-SyncServer](https://github.com/ackd7/MochiLedger-SyncServer)，支持 **Cloudflare Workers + KV（0 元免服务器）** 与 **Ktor + Docker（NAS / VPS 自建）** 两种部署方式。
- **LSPosed 微信 / 支付宝自动记账**：
  - 声明标准 `xposedmodule`、`xposedscope`（`com.tencent.mm`、`com.eg.android.AlipayGphone`）与 `assets/xposed_init`。
  - 支持自 Hook 实时检测 LSPosed 模块激活状态。
  - 内置非 Root 降级方案（`NotificationListenerService`）与时间窗口防重复入账去重机制。
  - 支持自定义关键词智能分类规则，并在 Windows / Android 端内置 **支付通知解析模拟器**，可实时测试并入账。
- **完整记账体验**：收支/转账记录、多账户资产负债统计、月度环形占比图、每日支出柱状图、预算进度预警、深浅色与多套 Mochi 强调色切换。

---

## 项目结构

```text
MochiLedger/
├── shared/          # KMP 核心模块：数据模型、SQLite 驱动、支付解析器、WebDAV/自建同步引擎、Compose UI
├── desktopApp/      # Windows / Desktop JVM 桌面端入口与 MSI/EXE 打包配置
├── androidApp/      # Android 客户端 + LSPosed (Xposed) 模块 + NotificationListenerService 降级服务
├── syncServer/      # 轻量自建同步服务端 (Ktor + Netty + Dockerfile + docker-compose.yml)
└── .github/workflows/build.yml  # GitHub Actions 自动测试与 Android APK / Windows 构建
```

---

## 快速开始

### 1. 运行 Windows 桌面端
需要 **JDK 21**：
```powershell
.\gradlew.bat :desktopApp:run
```
打包 Windows 安装目录：
```powershell
.\gradlew.bat :desktopApp:createDistributable
```

### 2. 构建 Android APK（含 LSPosed 模块）
配置 `ANDROID_HOME`（Android SDK 36）后执行：
```bash
./gradlew :androidApp:assembleDebug -PincludeAndroid=true
```
**LSPosed 使用步骤**：
1. 安装 `MochiLedger` APK。
2. 打开 **LSPosed Manager → 模块 → 墨记 MochiLedger**，启用模块。
3. 在作用域中勾选 **微信 (`com.tencent.mm`)**、**支付宝 (`com.eg.android.AlipayGphone`)** 及 **墨记自身 (`com.mochiledger.android`)**。
4. 强行停止并重新打开微信/支付宝，进入墨记「自动记账」页即可看到 **LSPosed 模块已激活**。

> 未 Root 设备可直接在「自动记账」页开启 **系统通知使用权（NotificationListenerService）** 作为备用监听方案。

### 3. 启动自建同步服务器（Docker）
```bash
cd syncServer
docker compose up -d
```
默认监听 `http://localhost:8787`，在 Windows 和 Android 客户端「同步设置 → 自建同步服务器」中填入地址与 `MOCHI_SYNC_TOKEN` 即可实现双端双向同步。

---

## 运行单元测试

```powershell
.\gradlew.bat :shared:desktopTest :syncServer:test
```

---

## 开源协议

[Apache-2.0 License](LICENSE)
