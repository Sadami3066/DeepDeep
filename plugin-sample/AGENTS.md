# plugin-sample · 示例外部插件

一个最小但完整的外部插件工程，用于真机验收、模板蓝本与 SDK 回归。不是 App 的一部分，`compileOnly` 依赖 `:plugin-api`，`assemblePlugin` 把 debug APK 重打包成 `.ddp`。

## 文件

| 文件 | 职责 |
|---|---|
| `build.gradle.kts` | application 模块；`compileOnly(project(":plugin-api"))`；`assemblePlugin` 任务产出 `build/plugin/plugin-sample.ddp` |
| `src/main/assets/plugin.json` | 插件清单：id/version/apiLevel/entryClass/provides |
| `src/main/kotlin/.../SamplePlugin.kt` | 入口类：一个 `sample_echo` 工具与一个 Compose 槽位 |

## 要点

- 只能依赖 Android SDK、`:plugin-api` 与 `compileOnly` 的宿主库；不得把宿主依赖打进包。
- `assemblePlugin` 只取 APK 的 `classes*.dex` 与 `assets/`，产物是无 AndroidManifest 的 `.ddp`。
- 真机验收：`scripts/plugin-spike.sh`（P0）或 `scripts/plugin-e2e.sh`（P8+）。
