# plugin-template · 外部插件模板工程

独立 Gradle Android 工程，作为外部插件起点与 `.ddp` 打包/签名参考；不参与主仓库构建（不在根 `settings.gradle.kts` 里）。只通过 mavenLocal 依赖 `io.github.whywood.deepdeep:plugin-api:0.2.0`，其余宿主库全部 `compileOnly`。

## 文件

| 文件 | 职责 |
|---|---|
| `build.gradle.kts` | Android 配置；`assemblePlugin` 重打包 APK 为 `.ddp`，`signPlugin` 生成 Ed25519 密钥并写入 `META-INF/plugin-signature.json`，`verifyPlugin` 自检签名 |
| `settings.gradle.kts` | 独立工程设置；`mavenLocal` 仅为 `io.github.whywood.deepdeep` 开启 |
| `src/main/assets/plugin.json` | 插件清单：id/version/apiLevel/minHostVersion/entryClass/name/summary/description/icon/ui/provides |
| `src/main/assets/skills/template-skill/` | 技能 markdown（SKILL.md）与附加文件（references/format.md） |
| `src/main/kotlin/.../TemplatePlugin.kt` | 参考实现：工具 + partial 更新、会话感知工具、技能、设置、setup、actions/detailRows、hooks、声明式与 Compose 槽位 |
| `README.md` | 构建/打包/签名/安装流程与改名清单 |

## 要点

- 插件只能依赖 Android SDK + `plugin-api` + `compileOnly` 宿主库；`implementation` 会把宿主类打进包，禁止。
- 签名规范与宿主校验一致：canonical entry digest（按 UTF-8 字节序排序、排除 `META-INF/`）、Ed25519、`keyId = ed25519:<sha256(SPKI DER)>`；详细公式见 `docs/PLUGIN_DEV.md`。
- `keys/` 已被 `.gitignore`；私钥丢失 = 无法再发同名更新，换钥必须换发布者身份。
- 版本对齐表（Kotlin/Compose/AGP/compileSdk/minSdk）随主仓库升级同步改本工程与 README。
- 改了打包/签名逻辑要同时改 `plugin-sample/build.gradle.kts` 中的同构实现，并跑一次 `verifyPlugin`。
