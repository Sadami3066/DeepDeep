# plugin-template · 外部插件模板

一个**独立**的 Gradle Android 工程，用来开发 DeepDeep 外部插件并产出带签名的 `.ddp` 包。它不参与主仓库构建（`settings.gradle.kts` 没有 include 它），模板代码覆盖工具、流式 partial 更新、技能、设置、连接流程、事件钩子与 UI 槽位，可直接改名后当自己的插件起点。

开发文档见仓库 `docs/PLUGIN_DEV.md`；契约源码见 `plugin-api/`。

## 前置：发布插件 SDK 到 mavenLocal

模板通过 `compileOnly("io.github.whywood.deepdeep:plugin-api:0.2.0")` 取契约，第一次构建前先在主仓库根目录执行：

```bash
./scripts/gradlew-locked :plugin-api:publishToMavenLocal
```

产物落在 `~/.m2/repository/io/github/whywood/deepdeep/plugin-api/0.2.0/`（AAR + sources + POM）。改了 `plugin-api` 后重新执行一次即可；升级 SDK 版本时同步改本工程 `build.gradle.kts` 的 `pluginApiVersion`、`plugin.json` 的 `apiLevel` / `minHostVersion`。

## 构建、打包、签名

在本目录执行（复用主仓库的 Gradle wrapper，会自动下载/使用 Gradle 9.8.2）：

```bash
cd plugin-template

../gradlew assemblePlugin      # debug APK -> build/plugin/plugin-template.unsigned.ddp
../gradlew signPlugin          # 生成/复用 Ed25519 密钥 -> build/plugin/plugin-template.ddp
../gradlew verifyPlugin        # 按插件验签规则自检签名
```

也可以用系统 Gradle：`gradle assemblePlugin signPlugin verifyPlugin`（Gradle ≥ 9.8）。

产物：

| 文件 | 内容 |
|---|---|
| `build/plugin/plugin-template.unsigned.ddp` | `classes*.dex` + `assets/**` + `lib/**`（若有），未签名 |
| `build/plugin/plugin-template.ddp` | 可安装包：上面的内容 + `META-INF/plugin-signature.json` |
| `keys/plugin-signing.pem` | 签名私钥（PKCS#8 PEM），首次 `signPlugin` 自动生成，**绝不提交/外发** |
| `keys/plugin-signing.pub` | 公钥（SPKI PEM），用于计算 `keyId = ed25519:<sha256(SPKI DER)>` |

`signPlugin` 打印 `publisher keyId`、整包 `sha256`、canonical entry digest 的 sha256，前两个用于分发索引 `index.json` 与安装记录。

换密钥 = 换发布者身份：同名插件后续升级必须使用同一个 `keyId`（首次安装写入 TOFU 信任库），否则宿主按「发布者不匹配」拒绝更新。

## 在设备上安装

1. 把 `build/plugin/plugin-template.ddp` 传到手机（文件选择器导入，或开发期 `adb push` 到应用外部目录）。
2. 宿主会校验 `plugin.json`、`apiLevel`/`minHostVersion`、Ed25519 签名与 `keyId`；首次安装需确认「插件代码拥有 App 全部权限」。
3. 启用插件后，`template_echo` / `template_session` 工具、`template-skill` 技能、设置页与 `setup.content` 槽位才会出现。

当前主仓库的 `scripts/plugin-spike.sh` 是绕过安装器的最小装载验证（P2 阶段）；安装器与插件页 UI 落地后按插件页流程导入。

## 工程结构

```
plugin-template/
├── build.gradle.kts                 # Android 配置 + assemblePlugin/signPlugin/verifyPlugin
├── settings.gradle.kts              # 独立工程；mavenLocal 只为本 SDK 开启
├── src/main/AndroidManifest.xml     # 无 Activity；APK 仅作为 dex/assets 容器
├── src/main/assets/plugin.json      # 插件清单（id/version/apiLevel/entryClass/provides/ui）
├── src/main/assets/icon.png         # 清单 icon 引用的资源
├── src/main/assets/skills/...       # 技能 markdown 与附加文件
└── src/main/kotlin/.../TemplatePlugin.kt   # 入口实现；清单 ui 只写 apiLevel
```

## 依赖规则

- 宿主已提供的库**只能** `compileOnly`：`plugin-api`、Compose（BOM/ui/foundation/material3）、`kotlinx-coroutines-core`、`kotlinx-serialization-json`。绝不能 `implementation`，否则会把宿主类打进插件包、触发 parent-first 类冲突。
- 版本必须与宿主对齐：Kotlin `2.4.21`、Compose BOM `2026.03.00`、AGP `9.4.1`、`compileSdk 36`、`minSdk 29`、JVM target 17。
- 插件代码运行在宿主进程里，`plugin-api` 与宿主共享同一份类；不要依赖 `plugin-api` 之外的主工程内部类型。
- 模板只编 `debug` 变体；`release` 变体故意不开 minify，模板未提供 keep 规则。

## 改名清单

1. `build.gradle.kts`：`namespace`、`applicationId`、`versionName`。
2. `src/main/assets/plugin.json`：`id`（反向域名）、`version`、`entryClass`、`name`/`summary`、`provides`。
3. `src/main/kotlin/`：包名与入口类，保持实现 `DeepDeepPlugin` 且有无参构造。
4. 打包后 `signPlugin` 会自动用同一密钥签名；换发布者时删除 `keys/` 重新生成并同步更新分发索引。

## 常见失败

| 现象 | 原因 |
|---|---|
| `plugin.json is missing entryClass` / `INVALID_ID` | 清单元数据不合法，见 `PluginValidation` 错误码 |
| 安装被拒 `apiLevel` | 插件 `apiLevel` > 宿主 `PLUGIN_API_LEVEL`，或 `minHostVersion` 高于宿主版本 |
| 安装被拒「签名校验失败」 | `.ddp` 在签名后被改动、公钥/`keyId` 不匹配、缺少 `META-INF/plugin-signature.json` |
| 安装被拒「发布者不匹配」 | 升级包与首次安装的 `keyId` 不同 |
| `ClassNotFoundException` | `entryClass` 写错，或类被 minify/改名；保持入口类与无参构造 |
| Compose 槽不显示 | `ui.apiLevel` 超过宿主 `HOST_UI_API_LEVEL`；或清单填了 `ui.kotlin`/`ui.compose` 但与宿主版本不一致（Kotlin 比主次版本、Compose 比 runtime 精确版本，不是 BOM 版本）。不确定就别填这两个字段，模板只写 `ui.apiLevel`；禁用后同槽位的声明式绑定/宿主默认视图仍可用 |
