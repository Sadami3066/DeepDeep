# plugin-sample · 示例外部插件

一个最小但完整的外部插件工程，用于真机验收、模板蓝本与 SDK 回归。不是 App 的一部分，`compileOnly` 依赖 `:plugin-api`，`assemblePlugin` 把 debug APK 重打包成 `.ddp`，`signPlugin` 用仓库内测试密钥产出可安装的签名包。

## 文件

| 文件 | 职责 |
|---|---|
| `build.gradle.kts` | application 模块；`compileOnly(project(":plugin-api"))` 与 `compileOnly` 宿主库；`assemblePlugin` 取 APK 的 `classes*.dex` + `src/main/assets/**` 产出 `build/plugin/plugin-sample.ddp`；`signPlugin` 用 `signing/dev-ed25519.*` 产出 `build/plugin/plugin-sample-signed.ddp`（密钥与未签名包声明为任务输入） |
| `src/main/assets/plugin.json` | 插件清单：id/version/apiLevel=2/minHostVersion/entryClass/provides/icon；tools `sample_echo`/`sample_card`，uiSlots `chat.message:sample.card`/`plugin.page:sample.main`，pages `sample.main` |
| `src/main/assets/icon.png` | 清单 `icon: asset:icon.png` 引用的插件图标，安装时解到版本目录 `assets/` |
| `src/main/kotlin/.../SamplePlugin.kt` | 入口类：`sample_echo`（工具）、`sample_card`（写入会话的自定义消息卡）、`chat.message:sample.card` 与 `plugin.page:sample.main` 两个 Compose 槽位（页面为插件自绘） |
| `signing/dev-ed25519.pem` | 提交进仓库的 Ed25519 **测试**私钥，仅供 sample 签名，禁止用于分发 |
| `signing/dev-ed25519.pub` | 对应公钥；`keyId = ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213` |
| `signing/README.md` | 测试密钥的用途与风险说明 |

## 要点

- 只能依赖 Android SDK、`:plugin-api` 与 `compileOnly` 的宿主库；不得把宿主依赖打进包。
- `assemblePlugin` 只取 APK 的 `classes*.dex` 与 `src/main/assets/**`（不读 APK 内 assets），产物是无 AndroidManifest 的 `.ddp`。
- `signPlugin` 依赖 `assemblePlugin`，按 spec v1 生成 `META-INF/plugin-signature.json`（canonical entry digest 排 `META-INF/`、UTF-8 字节序；Ed25519；`keyId` 由 SPKI DER 派生），并在写完后自检签名。命令：`./scripts/gradlew-locked :plugin-sample:assemblePlugin :plugin-sample:signPlugin`。
- 真机验收：`scripts/plugin-e2e.sh` 构建并推送 `plugin-sample-signed.ddp`，跑到 `ExternalPluginE2ETest`（安装 → 启用 → 工具调用 → 页面绑定 → 停用 → 卸载链路）；也可手动把签名包导入 App 插件页。
- 测试密钥只用于 sample：换密钥 = 换发布者身份，分发插件请在 `plugin-template` 里生成自己的密钥。
