# DeepDeep 插件开发

面向插件作者的开发文档：契约、清单、打包签名、生命周期、贡献面（工具/技能/设置/连接流程/事件/UI 槽位）、宿主服务、ABI、分发与调试。产品能力边界见 [GUIDE.md](GUIDE.md)，设计取舍见 [DECISIONS.md](DECISIONS.md)。

插件分两种来源，但实现同一套 `DeepDeepPlugin` 契约：

| 来源 | 说明 | 文本/图标 |
|---|---|---|
| `BUILTIN` 内置 | 编译进 APK，由 App 直接实例化，不经过装载器 | `LocalizedText.Resource` / `IconRef.Resource` 复用 App 资源 |
| `EXTERNAL` 外部 | `.ddp` 包（dex + assets），宿主用 `InMemoryDexClassLoader` 装载 | `LocalizedText.Literal` / `IconRef.Asset`，不能引用 App 资源 |

`plugin-api` 是唯一契约工件（Android library，包名 `io.github.whywood.deepdeep.plugin.api`，当前 `PLUGIN_API_LEVEL = 1`、SDK 版本 `0.2.0`）。插件工程只依赖它，宿主内部类型一律不可用。

**安全前提**：外部插件与 App 同进程、同 UID、无沙箱、同权限运行。安装即信任——插件代码等同 App 代码，能读 App 私有数据、反射宿主内部、让 App 崩溃。只安装自己信任的插件；第三方工具源请走 MCP。

## 快速开始

仓库内自带两份参考：

- `plugin-sample/`：最小外部插件，用于真机装载/验签回归。
- `plugin-template/`：完整模板工程（工具 + partial 更新 + 技能 + 设置 + setup + Compose/声明式槽位 + hooks + 签名），复制后改名即用。

从零开发一个新插件：

```bash
# 1. 发布 SDK 到 mavenLocal（在主仓库根目录）
./scripts/gradlew-locked :plugin-api:publishToMavenLocal

# 2. 复制模板并改名
cp -R plugin-template my-plugin && cd my-plugin
#    改 build.gradle.kts 的 namespace/applicationId/versionName
#    改 src/main/assets/plugin.json 的 id/version/entryClass/name/summary/provides
#    改 src/main/kotlin 下包名与入口类

# 3. 打包 + 签名（复用主仓库 wrapper，或系统 gradle >= 9.8）
../gradlew assemblePlugin
../gradlew signPlugin
../gradlew verifyPlugin

# 4. 产物
#    build/plugin/my-plugin.unsigned.ddp   未签名（排障用）
#    build/plugin/my-plugin.ddp            可安装的签名包
```

安装：把 `.ddp` 导入 App（插件页「从文件安装」；P8 安装器落地后可用），首次安装会展示发布者指纹、整包 sha256、将新增的能力，并提示「插件拥有 App 全部权限」。开发期也见「调试」一节。

## 工程与依赖

外部插件就是一个普通 Android application 工程，产物只需 `classes*.dex` 与 `assets/**`（见「包格式」），APK 的 manifest/资源不参与。要求：

| 项 | 值 |
|---|---|
| AGP | 9.4.1 |
| Kotlin | 2.4.21（Compose compiler 插件同版本） |
| Compose BOM | 2026.03.00 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization-json | 1.12.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 29（插件 dex 的 minApi 必须 ≤ 宿主 minSdk） |
| JVM target | 17 |

依赖规则：

- 宿主已提供的库只能 `compileOnly`：`io.github.whywood.deepdeep:plugin-api`、Compose（`ui`/`foundation`/`material3` + BOM）、`kotlinx-coroutines-core`、`kotlinx-serialization-json`。
- **禁止** `implementation` 这些库：宿主 parent-first 加载，插件包里重复打包会触发类冲突/方法数膨胀；`plugin-api` 由宿主提供同一份类，插件里只留编译期引用。
- 插件内部可以带自己的第三方库，但它们必须一起打进 `classes*.dex`，且要避开宿主已占用的包/类名。
- 不提供 `plugin-api` 之外的宿主内部类；不要反射 App 内部实现（升级会坏）。
- 原生库：`.ddp` 支持 `lib/<abi>/**` 进入签名摘要，但 v1 宿主不把 `lib` 加进 classloader 的 library search path，`System.loadLibrary` 不可用，等后续版本。
- 不要把 `assets/` 当 Android 资源用（没有 `res/`、没有 `R`）；文案、图标、技能文件都走清单 + assets。

## plugin.json 清单

外部插件的清单固定放在包内 `assets/plugin.json`，由宿主 `PluginManifestCodec` 解析与校验。字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 反向域名小写，如 `io.github.whywood.deepdeep.plugintemplate`；外部插件必须至少两段，宿主用它做安装目录与唯一标识 |
| `version` | string | 是 | `major.minor.patch`，可带 `-dev`/`+build` 后缀；宿主记录安装版本、判断更新 |
| `apiLevel` | int | 否（默认 1） | 插件要求的宿主契约等级；必须 ≤ 宿主 `PLUGIN_API_LEVEL`，否则拒绝装载 |
| `minHostVersion` | string | 否 | 最低宿主版本；比较只看 major.minor.patch（后缀忽略，`0.2.0-dev` 视为 `0.2.0`） |
| `entryClass` | string | 是 | 实现 `DeepDeepPlugin` 的类全名，必须有无参构造 |
| `name` | LocalizedText | 是 | 插件名，至少含 `default` |
| `summary` | LocalizedText | 是 | 一句话简介，至少含 `default` |
| `description` | LocalizedText | 否 | 详情页说明 |
| `sizeLabel` | LocalizedText | 否 | 详情页体积文案（外部插件一般由宿主按包体计算） |
| `homepage` | string | 否 | 必须是 `http(s)://` 链接 |
| `changelog` | string | 否 | 更新说明 |
| `icon` | string | 否 | 外部插件只支持 `asset:<包内相对路径>`，如 `asset:icon.png`；资源 id 不支持 |
| `ui` | object | 否 | `{ "apiLevel": 1, "kotlin"?: ..., "compose"?: ... }`；`apiLevel` 是 Compose 槽要求的 UI 契约等级，`kotlin`/`compose` 是构建版本信息；三者不匹配时宿主只禁用 Compose 绑定 |
| `source` | - | - | 由宿主设置：JSON 清单一律按 `EXTERNAL` 解析，不能声明 |
| `publisher` | object | 否 | `{ "name": ..., "keyId": ... }`；显示用，信任以签名里的 `keyId` 为准 |
| `requires` | string[] | 否 | 能力声明开放词汇，如 `host:linux`、`host:node>=22`、`privilege:adb`、`permission:CALENDAR`；只用于安装提示/可用性判定，不做运行时拦截 |
| `provides` | object | 否 | 贡献预检，见下 |
| `installable` | bool | 否（默认 true） | 为 false 表示启用前需要额外安装步骤 |
| `order` | int | 否（默认 0） | 插件列表排序，小的靠前 |
| `requiresPrivilege` | bool | 否（默认 false） | 需要 ADB/Root 能力，UI 上标黄提示 |
| `defaultEnabled` | bool | 否（默认 true） | 首次安装后是否默认启用 |
| `tintIcon` | bool | 否（默认 true） | 内置插件矢量图标是否按主题着色；外部插件用包内图片，一般保持默认 |

`provides` 用于安装/启用前的冲突预检与详情展示，必须与插件实际贡献一致：

| 键 | 内容 |
|---|---|
| `tools` | 工具名列表，小写 `snake_case`，如 `template_echo` |
| `skills` | 技能 id 列表，如 `template-skill` |
| `settings` | 设置项 key 列表（`Group` 本身不占 key） |
| `actions` | 详情页动作 id 列表 |
| `events` | 声明使用的事件名，如 `turnFinished` |
| `uiSlots` | 注册的槽位 id，如 `setup.content`、`tool.card.content:template_echo` |
| `pages` | 插件页面 id 列表 |

`LocalizedText` 写法：

```json
"name": { "default": "Plugin Template", "zh": "插件模板", "en": "Plugin Template" }
```

- `default` 为必填回退；`zh`/`en` 是语言标签，宿主按 App 语言取，缺失回退 `default`。
- 也可以给字符串简写（`"name": "Plugin Template"`），等价于只有 `default`。
- 外部插件不能用 `LocalizedText.Resource`（没有 App 资源）；`host.text()` 负责解析。
- `host.text(source, *args)` 支持 `String.format` 占位符（`%s`、`%d`），参数只传字符串/数字。

校验规则（`PluginValidation`，宿主在安装时执行，`PluginManifestCodec.decode` 汇总为 `Valid/Invalid`）：

| 错误码 | 含义 |
|---|---|
| `INVALID_ID` | 外部 id 不是反向域名小写格式 |
| `INVALID_VERSION` | version 不是 `major.minor.patch[后缀]` |
| `INVALID_MIN_HOST_VERSION` | minHostVersion 解析失败 |
| `HOST_VERSION_UNSUPPORTED` | 宿主版本低于 minHostVersion |
| `API_LEVEL_UNSUPPORTED` | apiLevel 不在 `1..宿主等级` |
| `UI_API_LEVEL_UNSUPPORTED` | ui.apiLevel 与宿主 `HOST_UI_API_LEVEL` 不匹配（**警告**，Compose 槽会被禁用，插件仍可加载） |
| `ENTRY_CLASS_MISSING` / `INVALID_ENTRY_CLASS` | 外部插件缺入口类或类名非法 |
| `MISSING_TEXT` / `INVALID_TEXT` | name/summary 缺 `default`，或外部插件用了资源文本 |
| `INVALID_ICON` / `INVALID_URL` | 图标路径/主页不合法 |
| `INVALID_REQUIRES` | requires 有空项 |
| `INVALID_PROVIDES` | provides 条目非法或重复 |
| `INVALID_UI_SLOT` | uiSlots 中的槽位 id 未知或缺实例后缀 |
| `INVALID_SKILL` / `INVALID_UI_NODE` | 技能文件或声明式 UI 节点不合法 |

## `.ddp` 包格式

`.ddp` 就是一个 zip（不分大小写扩展名无要求，App 只认文件本身）：

```
plugin.ddp
├── classes.dex            # 必需，至少一个；多个按 classes.dex, classes2.dex, ... 排序装载
├── classes2.dex
├── assets/
│   ├── plugin.json        # 必需，且只能在这个路径
│   ├── icon.png           # 可选，清单 icon 引用
│   └── skills/...         # 可选，技能 markdown 与附加文件
├── lib/<abi>/*.so         # 可选；进入签名摘要，v1 不加载
└── META-INF/plugin-signature.json   # 外部插件安装必需
```

约束：

- 没有 `AndroidManifest.xml`、没有 `res/`；zip 里只保留 dex/assets/lib/签名四类条目。
- ZIP 条目名必须使用 `/`；不能是绝对路径、不能含 `..`、`\`、`:`、NUL；同名条目拒绝；目录条目忽略。
- `assets/plugin.json` 只能是这一个路径（旧版本曾约定根 `plugin.json`，已废弃）。
- dex 必须经 D8/R8 产出（AGP 的 `assembleDebug` 已处理），插件工程 `minSdk = 29` 保证 dex minApi 不高于宿主。
- 安装后宿主把 `classes*.dex` 读进内存用 `InMemoryDexClassLoader` 装载（Android 14+ 动态加载只读文件限制的规避方式），`bundle` 原包复制到 `files/plugins/<id>/<version>/bundle/plugin.ddp`，`assets`/`lib` 解开到同版本目录，插件运行数据固定放 `files/plugins/<id>/data`（升级不丢）。
- 重装/升级：旧版本目录保留作回滚点，进程重启后旧 ClassLoader 才能真正释放。

## 签名、信任与更新

### 签名算法（spec v1）

外部插件安装必须带 `META-INF/plugin-signature.json`，算法 Ed25519（JDK `Ed25519`；宿主优先平台 Provider，旧平台回退 BouncyCastle）。

**canonical entry digest**：把 zip 内所有非目录条目按**条目名的 UTF-8 字节序**升序排列，排除所有以 `META-INF/` 开头的条目；每个条目一行：

```
<sha256-hex-lowercase><SP><uncompressed-size-decimal><SP><entry-name>\n
```

其中 sha256 是对该条目**未压缩内容**做 SHA-256，size 是未压缩字节数，行与行直接拼接（无额外分隔、最后一行以 `\n` 结尾）；整段 UTF-8 字节即待签名消息。宿主验签时用同一算法重算。

**签名文件**正好 4 个字段（未知字段忽略）：

```json
{
  "algorithm": "Ed25519",
  "keyId": "ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213",
  "publicKey": "<base64(std) of X.509 SubjectPublicKeyInfo DER，即 PublicKey.getEncoded()>",
  "signature": "<base64(std) of 64 字节 Ed25519 签名，签 canonical entry digest 字节>"
}
```

**keyId 必须由公钥派生**：`keyId = "ed25519:" + 小写十六进制(SHA-256(SPKI DER))`。宿主要求签名 JSON 里的 `keyId` 与公钥重算结果一致，防止签名自称成别人的 keyId；该值同时作为发布者指纹展示与 TOFU 信任库的键。`publicKey` 的 DER 就是被哈希的那段字节（Ed25519 SPKI DER 恒为 44 字节）。

模板工程的 `signPlugin` 已实现上述算法，并在写完后自检；`verifyPlugin` 可独立重算校验。

### 密钥管理

- 插件作者本地生成密钥（`KeyPairGenerator.getInstance("Ed25519")`），私钥保存为 PKCS#8 PEM，**不入版本库、不外发**；公钥可与签名一起分发。
- `plugin-template` 首次 `signPlugin` 在 `keys/plugin-signing.pem`/`.pub` 自动生成；模板 `.gitignore` 已忽略 `keys/`。私钥丢了就无法再发同名更新。
- 仓库内 `plugin-sample/signing/dev-ed25519.pem` 是提交的**测试**密钥，只用于 sample 回归，任何人不该用它签分发插件。

### 信任（TOFU）与更新

- 首装是唯一信任决策点：确认页展示发布者名/`keyId`、整包 sha256、将新增的工具/技能/设置、所需宿主能力，并写明「插件拥有 App 全部权限」。确认后 `keyId → publicKey` 记入 TOFU 信任库。
- 每次装载前用记录里的 `bundleSha256` 校验包体，并重算 canonical digest 验签；不匹配 → 拒绝并标记 `FAILED`。
- **更新必须同钥**：`id` 相同的新版本必须用同一 `keyId` 签名，否则按「发布者不匹配」拒绝。
- 更新流程：下载/选择新包 → 校验 sha256 + 签名（同钥）→ 暂存到新版本目录 → 下一次进程启动激活；激活失败回滚上一版本。

### 分发索引

内部分发使用统一索引 `index.json`，App 侧索引地址与自动检查开关存在 `files/plugins/sources.json`（插件页「插件源」可编辑，或直接改该文件）：

```json
{
  "schemaVersion": 1,
  "plugins": [
    {
      "id": "io.github.whywood.deepdeep.plugintemplate",
      "version": "1.0.0",
      "apiLevel": 1,
      "minHostVersion": "0.2.0",
      "url": "https://example.com/plugins/plugin-template-1.0.0.ddp",
      "sha256": "<.ddp 整包 sha256>",
      "size": 123456,
      "publisherKeyId": "ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213"
    }
  ]
}
```

不引入第三方仓库/二进制分发依赖；首装必须用户确认，sha256 必须校验。

## 生命周期与相位

宿主维护相位 `IMPORTED → INSTALLED → ACTIVE / DEGRADED / FAILED / DISABLED / PENDING_RESTART / UNLOADED`。只有 `ACTIVE`、`DEGRADED`、`PENDING_RESTART` 相位会聚合插件的工具/技能/提示词段/设置/动作/详情/UI/hooks；其它相位仍可展示清单与错误信息。相位表达「用户选择 ≠ 加载成功」：启用后加载失败是 `FAILED` 而不是 `DISABLED`。

`DeepDeepPlugin` 生命周期方法（除前两个外都有默认空实现；回调异常由宿主隔离，不会拖垮 App）：

| 方法 | 调用时机 |
|---|---|
| `availability(host)` | 刷新快照时判断能力是否可用；返回 `Unavailable(reason)` 时 UI 给出原因且不贡献工具 |
| `status(host)` | 刷新快照时生成详情页状态（`Ready(label, account?)` / `NotReady(label)` / `Failed(label)`），结果会缓存一段时间 |
| `install(host, progress)` | 用户确认安装后；`progress.report(percent, label)` 更新安装进度 |
| `uninstall(host)` | 卸载前清理插件自己的外部副作用（宿主随后删目录、清技能归属） |
| `onEnable(host)` / `onDisable(host)` | 用户启/停插件；启停会走 `host.reconfigure()` 安全点重新装配会话 |
| `onUpgrade(host, fromVersion)` | 版本升级激活后 |
| `setup(host, ui)` | 用户点「连接/设置」拉起流程；见「连接流程」 |
| `actions(host, status)` / `runAction(host, id, ui)` | 详情页动作按钮；`DESTRUCTIVE` 样式需用户确认 |
| `detailRows(host, status)` | 详情页只读信息行 |
| `skills(host)` | 提供技能（markdown + 附加文件） |
| `promptSections(host)` | 追加系统提示词段 |
| `settings()` / `onSettingsChanged(host, values)` | 声明式设置；保存后回调归一化后的值 |
| `contributions(host)` | 工具 + hooks（无会话上下文时） |
| `contributionsFor(host, session)` | 每会话构建工具时调用，`session` 为 `null` 或 `PluginSessionInfo`；默认委托 `contributions` |
| `ui(host)` | 槽位/页面/输入区动作贡献 |
| `close()` | 插件停用/卸载/进程退出；宿主取消插件 scope，`close` 后不要再异步回调 |

`host` 是插件唯一入口（`PluginHost`）：`manifest`、`context`（application context）、`dataDir`（`files/plugins/<id>/data`，升级保留）、`assetsDir`（解包后的只读 assets）、`scope`（插件级 `CoroutineScope`，卸载时统一取消）、`text/log` 与 17 个宿主服务访问器（见「宿主服务」）。

## 工具

```kotlin
override fun contributions(host: PluginHost) = Contributions(
    tools = listOf(
        PluginTool(
            name = "template_echo",
            label = "Template Echo",
            description = "Echo text back, streaming one partial update.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("text") { put("type", "string") } }
                put("required", buildJsonArray { add(JsonPrimitive("text")) })
            },
            promptSnippet = "template_echo echoes text",
            promptGuidelines = listOf("Use it only in development demos."),
        ) { call ->
            val text = call.args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            call.partial(PluginToolResult.Text("received ${text.length} chars"))
            PluginToolResult.Text("echo: $text")
        },
    ),
)
```

| 字段 | 说明 |
|---|---|
| `name` | 全局工具名，小写 `snake_case`，不能与其它插件/宿主工具重名。跨插件重名时先注册者保留，冲突记入宿主诊断 |
| `label` | 工具卡片标题 |
| `description` | 给模型的工具描述，写清做什么/何时用/边界 |
| `parameters` | JSON Schema（`{"type":"object","properties":{...},"required":[...]}`），宿主原样交给 pi SDK |
| `promptSnippet` | 系统提示里的一句话摘要（可选） |
| `promptGuidelines` | 追加给模型的工具使用规则（可选） |
| `execute` | `suspend (PluginToolCall) -> PluginToolResult` |

`PluginToolCall`：

- `callId`：本次调用 id，只用于日志/关联。
- `args: JsonObject`：模型传入参数，按 `parameters` 校验后的原始 JSON；取值用 `jsonPrimitive.contentOrNull` / `intOrNull` 等。
- `signal: PluginRunSignal?`：取消信号；长任务里在阶段边界调用 `signal?.throwIfCancelled()`，不要吞取消异常。
- `partial(result)`：流式中间结果，宿主在有实时工具卡时展示（best-effort，没有监听者时丢弃）；`execute` 的返回值才是最终结果。

`PluginToolResult`：

| 形式 | 用途 |
|---|---|
| `Text(text, details?, isError)` | 普通文本结果；`details` 是宿主侧结构化元数据（不进模型上下文） |
| `Rich(blocks, details?, isError)` | 多块结果：`ToolBlock.Text` 文本、`ToolBlock.Image(data, mimeType)` 图片（base64 进 pi `ImageContent`）、`ToolBlock.FileRef(path, mimeType?)` 文件引用（宿主转成 `file: <path> (<mime>)` 一行文本，不读文件、不当附件） |
| `PluginToolResult.text(...)` / `.rich(...)` | 便捷构造 |

`isError = true` 对应一次失败的普通工具结果（模型可重试/解释）；`execute` 抛异常（取消除外）也会转成 error 结果，所以不要用异常传业务错误。耗时工具请配合 `signal` 支持取消。

工具默认随启用插件进入会话；`contributionsFor` 里可按 `PluginSessionInfo`（`sessionId`/`cwd`/`file`/`supportsChatDelivery`）决定是否/如何暴露会话相关工具。

## 技能

`PluginSkill(id, markdown, files)`：

- `id`：小写 `[a-z][a-z0-9_-]*`，与 SKILL.md 的 frontmatter `name` 一致；全局技能 id 冲突时先注册者保留。
- `markdown`：完整 SKILL.md 内容，必须带 YAML frontmatter（`name`、`description`），正文写给模型用。
- `files`：附加文件，`PluginSkillFile(path, content?, assetPath?)`——`path` 是技能目录内相对路径，`content`（内联字符串）与 `assetPath`（包内 `assets/<path>` 资源）二选一。
- 宿主在插件可贡献时把技能物化到 agent 技能仓库/启用目录，并用归属标记在停用/卸载时清理；不要在 `skills()` 里写文件。
- `skill` 读取资源用 `File(host.assetsDir, ...)`；`assetsDir` 是解包后的只读目录（外部插件）或随 APK 的 assets 目录（内置）。
- 技能内容不本地化（它是模型输入），描述语言按插件作者习惯即可；`plugin-template` 提供了多文件示例。

## 设置

```kotlin
override fun settings(): List<SettingSpec> = listOf(
    SettingSpec.Toggle("streaming", literal("Stream partial results", "流式返回中间结果"), defaultValue = true),
    SettingSpec.Choice(
        "mode", literal("Echo mode", "回显模式"),
        options = listOf(SettingOption("fast", literal("Fast", "快速"))),
        default = "fast",
    ),
    SettingSpec.Number("max_results", literal("Max results", "最大结果数"), min = 1, max = 20, defaultValue = 5),
    SettingSpec.Text("api_key", literal("API key", "API Key"), secret = true, requiresRestart = true),
    SettingSpec.Group("connection", literal("Connection", "连接"), items = listOf(/* ... */)),
)
```

| 类型 | 值 | 归一化 |
|---|---|---|
| `Toggle` | `"true"`/`"false"` | 非法值丢弃 |
| `Choice` | `options` 里的 value | 不在选项内丢弃 |
| `Number` | 整数 | 超界钳到 `[min, max]`，非数字丢弃 |
| `Text` | 字符串 | 非 secret 去首尾空白；`secret = true` 原样保留 |
| `Group` | 仅 UI 分组容器 | 不落值 |

- 所有类型都支持 `requiresRestart`（保存后需重启会话/进程生效）与 `summary`（设置页辅助说明）。
- 宿主用 `List<SettingSpec>.normalize(map)` 补默认值、丢弃非法存值；`onSettingsChanged` 收到的是归一化后的值。
- **设置值明文存本机** `files/plugins/settings.json`（App `allowBackup=false`）。真正的密钥/令牌用 `host.secrets()`（按插件 id 命名空间加密存储），不要塞进设置项展示。
- 设置结构尽量稳定：删 key 会丢用户值；语义变化请换 key 或做迁移。

## 连接流程（setup）

平台 `plugin_setup` 工具或插件详情「连接」会拉起 setup sheet，插件实现 `setup(host, ui)`：

```kotlin
override suspend fun setup(host: PluginHost, ui: PluginSetupUi) {
    ui.stage(title, message, url = null, imagePath = null, progress = 0)
    if (ui.cancelled()) return
    ui.progress(50, label)
    // 长流程：轮询登录状态/读 CLI 输出，定期检查 ui.cancelled()
    ui.progress(100, done)
}
```

- `stage(...)`：展示阶段（标题/说明/链接/图片路径/进度）；未提供任何自定义 UI 时宿主用通用视图渲染这些字段。
- `progress(percent, label)`：安装/连接进度（同时进系统通知）。
- `cancelled()`：用户取消；长循环里要主动轮询并及时返回（抛取消异常也可，宿主按取消处理）。
- 整个流程最多等 15 分钟，超时取消本次连接（已安装部分保留）；连接必须在前台启动，App 不在前台时只通过悬浮窗事件卡提示回到 App。
- 需要独立页面/Activity 的流程不在 v1 承诺内（外部插件不能注册 Activity）；二维码用 `stage(imagePath = ...)` 或声明式 `qr_code` 节点。
- 安装确认、取消、进度、系统通知、失败回退由宿主统一处理，插件不要重复弹系统 UI。

## 事件与 hooks

`contributions(host).hooks` 返回 `PluginHooks` 实现（全部方法有默认空实现，只覆盖需要的）：

### 观察事件（只通知，不能改变流程）

按注册顺序派发，单个 handler 超时 5s（`PluginHooks.OBSERVE_TIMEOUT_MS`）；异常记录后继续，不影响会话。

| 方法 | 时机 |
|---|---|
| `sessionStarted` / `sessionClosed` | 会话创建/关闭 |
| `runStarted` / `runFinished` | 一轮 agent run 开始/结束（`RunFinished.error == null` 表示成功） |
| `turnStarted` / `turnFinished` | 单轮开始/结束（`failed`） |
| `messageStarted` / `messageDelta` / `messageFinished` | 消息流（`MessageRole.USER/ASSISTANT/TOOL/SYSTEM`） |
| `toolExecutionUpdate` | 工具执行状态（`STARTED/PARTIAL/FINISHED/FAILED`，可带 partial 结果） |
| `compactionStarted` / `compactionFinished` / `compactionFailed` | 上下文压缩 |
| `modelChanged` / `thinkingLevelChanged` / `toolSetChanged` | 会话配置变化 |
| `retryScheduled` / `retrySucceeded` / `retryExhausted` | 网络/限流重试 |
| `mcpStatusChanged` / `mcpToolsChanged` | MCP 服务器状态/工具变化 |
| `busEvent` | 插件总线事件（见「总线」） |

### 变换与闸门（可改流程）

按注册顺序串行执行，单个 handler 超时 2s（`PluginHooks.TRANSFORM_TIMEOUT_MS`）；超时/异常按各阶段的失败策略处理。

| 方法 | 输入 → 输出 | 失败策略 |
|---|---|---|
| `promptTransform(request: PromptRequest): String` | 会话 id + prompt → 新 prompt，链式传递 | fail-open：保留上一个值 |
| `inputTransform(request: InputRequest): InputDecision` | 输入文本/图片路径 → `Continue(text, imagePaths)` 继续（链式），`Handled` 短路并丢弃本轮输入 | fail-open：保留当前输入 |
| `beforeToolCall(request: ToolCallRequest): ToolCallDecision` | 工具调用 → `Continue` / `Block(reason)` / `Rewrite(args)`（后续插件基于改写后的 args 继续） | **fail-closed**：超时/异常按 `Block` 阻止调用并给出原因 |
| `afterToolExecution(request: ToolExecutionResult): ToolResultDecision` | 工具结果 → `Keep` / `Replace(result)`（链式替换） | keep：保留上一个结果 |

注意：

- 这些回调在会话关键路径上，必须快速返回；耗时工作丢到 `host.scope` 异步做，不要在里面做网络/重 IO。
- 不要在 transform 里无限重试或阻塞等待用户；需要用户确认的闸门请只做同步判定。
- `PluginHooks.None` 可用于占位；事件顺序 = 快照里的加载/注册顺序。
- 每轮开始宿主冻结一次 handler 快照，轮内新增/停用插件不改变本轮的派发集合；能力变更走安全点重配置。
- 用户主动取消导致的 `CancellationException` 会向上传播，不要吞。

## UI 扩展

宿主固定主壳/导航/聊天骨架，插件只能向**槽位**挂绑定或注册页面/输入区动作，不能替换整页、不能注册 Activity。

### 槽位目录

| slotId | 场景 |
|---|---|
| `setup.content` | setup sheet 内容区；可替换宿主通用阶段视图 |
| `plugin.detail.section:<sectionId>` | 插件详情页自定义区块；`sectionId` 必填且为小写 `[a-z][a-z0-9_-]*` |
| `tool.card.content:<toolName>` | 指定工具调用卡片的内容区 |
| `chat.message:<customType>` | 自定义消息类型的内容区 |

页面与输入区动作是贡献，不是槽位：`PluginUiContributions.pages`（`PluginPage(id, title, icon?)`）与 `.composerActions`（`ComposerAction(id, label, icon?)`）。

`SlotBinding(slotId, priority, content)`：同一槽位只渲染一个绑定——按 `priority` 从大到小取第一个可渲染的（同优先级按插件注册顺序），其余绑定被覆盖；没有插件绑定时宿主用默认视图兜底。渲染前宿主写崩溃标记、成功后清除，残留标记表示上次渲染导致进程死亡，下次启动禁用该绑定。

`SlotIds` 提供 helper：

```kotlin
SlotIds.setupContent()                       // "setup.content"
SlotIds.detailSection("account")             // "plugin.detail.section:account"
SlotIds.toolCard("template_echo")            // "tool.card.content:template_echo"
SlotIds.chatMessage("card")                  // "chat.message:card"
SlotIds.matches(slotId)                      // 校验完整槽位 id
```

### 两种渲染方式

1. **声明式 `SlotContent.Declarative(UiNode)`**：默认推荐，跨版本稳定。节点是 `type + text + props + children` 的树。
2. **Compose `SlotContent.Compose(uiApiLevel, render)`**：与宿主 Kotlin/Compose 版本强绑。以下任一不满足时宿主只禁用该 Compose 绑定（回退到同槽位下一个可渲染绑定或宿主默认视图），不会拒绝插件：`uiApiLevel <= HOST_UI_API_LEVEL`、清单 `ui.apiLevel <= HOST_UI_API_LEVEL`、清单若填了 `ui.kotlin` 则与宿主 Kotlin **主次版本**一致（如 `2.4`）、清单若填了 `ui.compose` 则与宿主 Compose runtime 版本**完全一致**（注意不是 Compose BOM 版本）。不填写 `ui.kotlin`/`ui.compose` 时跳过对应检查，因此模板只在清单里写 `ui.apiLevel`。约定使用 Compose 绑定的插件必须声明 `ui.apiLevel`：未声明 `ui` 块时当前宿主按宿主等级放行，但作者不应依赖这种行为。`render` 收到的 `SlotState` 取决于槽位族；插件必须按宿主版本编译。

声明式节点（`UiNodeTypes`）：

| type | 必填 | 可选 props | children |
|---|---|---|---|
| `column` | - | - | 是（纵向，间距由宿主控制） |
| `row` | - | - | 是（横向） |
| `text` | `text` | `style`：`body`/`title`/`label`/`caption`/`code` | 否 |
| `markdown` | `text` | - | 否 |
| `image` | `props.source`：`https://...` 或 `asset:<相对路径>` | `content_description` | 否 |
| `button` | `text`（按钮文案）、`props.action`（动作 id） | - | 否 |
| `link` | `text`、`props.url`（`http(s)://` 或 `mailto:`） | - | 否 |
| `progress` | - | `value`：0–100 整数，缺省为不确定进度 | 否 |
| `qr_code` | `props.data` | - | 否 |
| `divider` | - | - | 否 |
| `spacer` | - | `height`：0–480 dp | 否 |

限制：最大深度 8 层、最多 200 个节点；未知 type、缺必填 props、容器里放 children 之外的内容都是错误，未知 props 只是警告。可用 `PluginValidation.validateUiNode(tree)` 自查。

绑定示例：

```kotlin
override fun ui(host: PluginHost) = object : PluginUiContributions {
    override val bindings = listOf(
        SlotBinding(
            slotId = SlotIds.toolCard("template_echo"),
            content = SlotContent.Declarative(
                UiNode(UiNodeTypes.COLUMN, children = listOf(
                    UiNode(UiNodeTypes.TEXT, text = "Template echo", props = mapOf("style" to "title")),
                    UiNode(UiNodeTypes.DIVIDER),
                    UiNode(UiNodeTypes.LINK, text = "Docs", props = mapOf("url" to "https://example.com")),
                )),
            ),
        ),
        SlotBinding(
            slotId = SlotIds.setupContent(),
            content = SlotContent.Compose(uiApiLevel = 1) { state -> MySetupContent(host, state) },
        ),
    )
}
```

`SlotState` 族（Compose 渲染入参）：`SetupSlotState`（title/message/url/imagePath/progress/cancelled）、`DetailSlotState`（pluginId/status/rows）、`ToolCardSlotState`（callId/toolName/args/result）、`CustomMessageSlotState`（messageId/customType/payload）、`ComposerActionState`（actionId/input/sessionId）、`PluginPageState`（pluginId/pageId/sessionId）；无状态时宿主传 `NoSlotState`。

## 宿主服务

`PluginHost` 暴露 18 个服务访问器；宿主没提供该服务时**抛 `PluginServiceUnavailable`**（不是返回 null），插件应 `runCatching` 后降级。服务的实现由 App 装配，同一接口也可能部分能力不可用（如未装 Linux、未开特权）。

| 访问器 | 主要能力 | 常见不可用原因 |
|---|---|---|
| `linux()` | `available`、`exec(command, cwd, timeoutMs)`、`stream(command)` → `LinuxResult`/`LinuxChunk` | 未安装/未启用运行环境 |
| `privilege()` | `state`（`PrivilegeState`：selected/effective/rootGranted/adbConnected）、`observe()`、`shell(command)` | 未选 ADB/Root 档或后端未就绪 |
| `screen()` | 虚拟屏/主屏：`status`、`startVirtual/stopVirtual`、`observe`、`screenshot`、`apps`、`launch`、`tap/tapNode/swipe/scroll/type/key`、`waitFor` | 未开特权/无障碍 |
| `notifications()` | `enabled`、`notify(PluginNotification)`、`updateProgress`、`cancel` | 通知权限未开 |
| `secrets()` | `get/put/remove/clear`，按插件 id 命名空间加密存储 | 宿主未提供 |
| `network()` | `request(method,url,headers,body,timeoutMs)`、`download(url,targetPath,expectedSha256,onProgress)`；扩展 `get`/`post` | 无网络 |
| `files()` | 插件数据/工作区文件：`list/readText/writeText/readBytes/writeBytes/delete/exists/resolve` | 宿主未提供 |
| `sessions()` | `active`、`observe()`、`list()`、`prompt(text, sessionId?)`（向会话发消息） | 宿主未提供 |
| `jobs()` | 后台任务：`observe()`、`start(command,label,cwd,sessionId)`、`read(jobId)`、`kill(jobId)` | 宿主未提供 |
| `timers()` | 定时任务：`schedule(TimerRequest)`（`Once(epochMillis)`/`Cron(expression)`）、`list()`、`cancel(id)` | 宿主未提供 |
| `goals()` | 目标：`observe()`、`get/create/edit/pause/resume/complete/clear` | 宿主未提供 |
| `workshop()` | 创意工坊：`scan/load/create/edit/delete/run` | 宿主未提供 |
| `mcp()` | MCP：`servers()`、`probe(name)`、`tools(server)`、`call(server,tool,arguments)` | 宿主未提供 |
| `ui()` | `openUrl`、`share(ShareRequest)`、`toast`、`openPage(pageId)` | 宿主未提供 |
| `app()` | `info: AppInfo`（versionName/versionCode/pluginApiLevel/uiApiLevel/language/androidSdkInt/deviceModel/installationId） | 宿主未提供 |
| `bus()` | 插件总线：`publish(PluginBusEvent)`、`subscribe(topic?)` → `Flow<PluginBusEvent>` | 宿主未提供 |
| `logs()` | `log(level, message, error?)` + `debug/info/warn/error` 便捷方法 | 宿主未提供 |
| `chat()` | `postCustomMessage(sessionId, customType, payload, text, display)`：把插件卡片写入会话 JSONL（`custom_message`），并实时投递到聊天界面 | 无活动会话 / 宿主未提供 |

`requires` 里的能力声明只用于安装提示与「插件是否可用」，不构成运行时权限边界；同进程插件本来就能直接调 Android API。

**总线**：`host.bus().publish(PluginBusEvent(topic, publisher, payload, publishedAt))` 广播给所有插件；订阅 `host.bus().subscribe("topic")` 拿 `Flow`，同时 `PluginHooks.busEvent(...)` 观察类回调也能收到。事件只用于插件间协作与轻量通知，不要用它传大载荷或做同步 RPC。

**自定义消息卡**：在工具执行里用 `host.chat().postCustomMessage(call.sessionId, "my.card", payload, text = "卡片摘要")` 把卡片写进当前会话（`PluginToolCall.sessionId` 由宿主注入）；再注册 `chat.message:my.card` 的声明式或 Compose 槽位渲染。`display=false` 只记日志不显示。消息会写入 pi JSONL 的 `custom_message` 条目，重开会话后仍由 `HistoryMapper` 还原。

## ABI 与版本

- `PLUGIN_API_LEVEL = 1`：宿主加载时要求 `plugin.apiLevel <= 宿主等级`；`minHostVersion` 高于宿主版本直接拒绝。
- **同一 apiLevel 内只做加法**：新增类型、新增带默认值的可选参数、接口新增带默认实现的方法、data class 新增带默认值的字段。删除/改签名/改默认语义必须升 `PLUGIN_API_LEVEL`，旧插件会被明确拒绝而不是崩溃。
- 插件必须 `compileOnly` 依赖宿主库（见「工程与依赖」）；宿主 parent-first 保证 `plugin-api` 只存在一份。
- Compose 槽：`ui.apiLevel` 与宿主 `HOST_UI_API_LEVEL`（当前 1）不匹配时只禁用 Compose 绑定、回退声明式/默认渲染；Compose 没有跨版本二进制 ABI 承诺，插件要按宿主 Kotlin/Compose 版本重新编译。
- `ui.kotlin` / `ui.compose` 参与 Compose 槽的兼容判定：Kotlin 比主次版本，Compose 比 runtime 精确版本（不是 BOM 版本），不匹配只禁用 Compose 绑定。不确定宿主版本时不要填这两个字段，只填 `ui.apiLevel`。
- 版本比较（`PluginVersion`）：核心 `major.minor.patch` 参与比较，`-dev`/`+build` 等后缀保留展示但不影响比较（`0.2.0-dev` 视为 `0.2.0`），避免开发版被自己的 `minHostVersion` 卡住。
- SDK 版本（`0.2.0`）与 `PLUGIN_API_LEVEL` 独立维护：SDK 发布版本升级不等于契约 breaking，ABI 以 `apiLevel` 为准。

## 安全模型

- **同进程、同 UID、无沙箱**：外部插件代码与 App 代码同等权限，能读写 App 私有数据、访问已授权的系统能力（日历、无障碍、ADB/Root 桥）、反射宿主内部类、起线程、结束进程。同进程没有可信的权限收窄机制，`requires` 只是提示，不是边界。
- **安装即信任**：首装确认页必须让用户明确「插件拥有 App 全部权限」；签名只解决「包有没有被篡改、是不是同一个发布者」，不解决「作者是否可信」。
- **适用来源**：可信的第一方/个人插件包；第三方工具源用 MCP，不走 .ddp。
- **完整性**：安装记录钉死整包 `bundleSha256`，每次装载前校验；canonical digest 验签防篡改；更新必须同 `keyId`；失败标 `FAILED` 并保留上一可用版本。
- **宿主不暴露的东西**：Provider 凭据/OAuth、ADB 配对流程、宿主内部加密密钥、pi SDK 内部类型。插件拿到的是 `PluginHost` 契约与 `host.context`。
- **插件作者的义务**：不读与本插件无关的用户数据；不把凭据写日志；网络请求走 `host.network()`（带超时/大小上限）或自证安全；删除/覆盖用户数据的动作要可逆或在 UI 里明确确认；不要用 `System.exit`/`Runtime.halt` 等结束进程的调用。
- **崩溃**：工具/回调异常会被宿主隔离成 error/失败记录；但插件在主线程、Compose 槽或工具执行中把进程搞崩仍会带崩 App（v1 不承诺进程级隔离），槽渲染有崩溃标记保护。

## 调试

构建/打包/签名（主仓库内或模板目录）：

```bash
./scripts/gradlew-locked :plugin-sample:assemblePlugin :plugin-sample:signPlugin
cd plugin-template && ../gradlew assemblePlugin signPlugin verifyPlugin
```

排查路径：

- **安装前**：用 `verifyPlugin` 自检签名；检查 `plugin.json` 的 id/version/apiLevel/entryClass/provides 与槽位 id；确认包内 `assets/plugin.json` 路径正确、至少一个 `classes*.dex`。
- **安装被拒**：安装器返回 `PluginValidation.Issue` 错误码（见清单章节）或验签失败原因；记录在 `plugins.json` 的 `phase=FAILED` + `lastError`，插件详情能看到。
- **装载失败**：`ClassNotFoundException` → `entryClass` 错/类被 minify；`NoSuchMethodException` → 缺无参构造；`NoClassDefFoundError` → 宿主版本不一致或把宿主依赖打进包。
- **行为不符**：`host.log(...)` 会进宿主日志；Android 侧用 `adb logcat` 按 App 包名过滤。插件抛出的异常宿主会隔离并记录，不会静默吞工具业务错误。
- **技能没出现**：插件必须处于可贡献相位；SKILL.md 必须有 frontmatter，`PluginSkill.id` 与 `name` 一致；文件路径必须相对且安全。
- **槽位不显示**：槽位 id 必须过 `SlotIds.matches` 且在 `provides.uiSlots` 声明；Compose 槽检查 `uiApiLevel`；声明式节点跑一遍 `PluginValidation.validateUiNode`。
- **更新失败**：确认签名 `keyId` 与首装一致、整包 sha256 与记录一致；换密钥只能当新发布者重装。

## 限制（v1 明确不做）

- 不做第三方插件商店/审核/沙箱：同进程插件无隔离，只能靠来源可信 + 安装确认 + 签名/TOFU。
- 不给插件注册 Activity/Service/权限：平台不允许运行期组件注册；需要独立 UI 只能另装独立 APK（不在 v1 范围）。
- 外部插件不能用 `res/` 资源 id；文案/图标/技能都走清单与 assets。
- 不提供 Provider 凭据/OAuth、ADB 配对流程、宿主内部加密密钥等服务；插件可以直接用 `host.context` 调 Android API，宿主不做拦截。
- 不承诺插件崩溃的进程级隔离：Compose 槽有崩溃标记保护（下次启动禁用该绑定），但插件在主线程/工具执行里把进程搞崩仍会带崩 App。
- 原生库 v1 不加载（`lib/**` 仅进入摘要与解包）。
- `messageTransform`（改写消息）不在 v1 事件面内。

## 参考

- 契约源码与逐文件说明：`plugin-api/AGENTS.md`、`plugin-api/src/main/kotlin/io/github/whywood/deepdeep/plugin/api/`
- 示例：`plugin-sample/`（最小）、`plugin-template/`（完整模板与签名流程）
- 宿主装载/校验/服务实现：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/host/`
- 分发/信任/索引决策：[DECISIONS.md](DECISIONS.md) 的 #68–#76；产品能力边界：[GUIDE.md](GUIDE.md)
