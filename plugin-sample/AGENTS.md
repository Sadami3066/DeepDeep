# plugin-sample · 示例外部插件

一个最小但完整的外部插件工程，用于真机验收、模板蓝本与 SDK 回归。不是 App 的一部分，`compileOnly` 依赖 `:plugin-api`，`assemblePlugin` 把 debug APK 重打包成 `.ddp`，`signPlugin` 用仓库内测试密钥产出可安装的签名包。

## 文件

| 文件 | 职责 |
|---|---|
| `build.gradle.kts` | application 模块；`compileOnly(project(":plugin-api"))`；`assemblePlugin` 产出 `build/plugin/plugin-sample.ddp`；`signPlugin` 产出 `build/plugin/plugin-sample-signed.ddp` |
| `src/main/assets/plugin.json` | 插件清单：id/version/apiLevel/entryClass/provides/icon |
| `src/main/assets/icon.png` | 清单 `icon: asset:icon.png` 引用的插件图标，安装时解到版本目录 `assets/` |
| `src/main/kotlin/.../SamplePlugin.kt` | 入口类：一个 `sample_echo` 工具与一个 Compose 槽位 |
| `signing/dev-ed25519.pem` | 提交进仓库的 Ed25519 **测试**私钥，仅供 sample 签名，禁止用于分发 |
| `signing/dev-ed25519.pub` | 对应公钥；`keyId = ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213` |
| `signing/README.md` | 测试密钥的用途与风险说明 |

## 要点

- 只能依赖 Android SDK、`:plugin-api` 与 `compileOnly` 的宿主库；不得把宿主依赖打进包。
- `assemblePlugin` 只取 APK 的 `classes*.dex` 与 `assets/`，产物是无 AndroidManifest 的 `.ddp`；不改变原有行为。
- `signPlugin` 依赖 `assemblePlugin`，按 spec v1 生成 `META-INF/plugin-signature.json`（canonical entry digest 排 `META-INF/`、UTF-8 字节序；Ed25519；`keyId` 由 SPKI DER 派生），并在写完后自检签名。命令：`./scripts/gradlew-locked :plugin-sample:assemblePlugin :plugin-sample:signPlugin`。
- 真机验收：`scripts/plugin-spike.sh`（P0 直载，绕过安装器）；P8 安装器链路使用 `plugin-sample-signed.ddp`。
