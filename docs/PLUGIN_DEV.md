# DeepDeep 插件开发

面向插件作者的开发文档：工程搭建、契约、打包签名、生命周期与全部扩展面。产品能力边界见 [GUIDE.md](GUIDE.md)，设计取舍见 [DECISIONS.md](DECISIONS.md)。

DeepDeep 的插件是 **Kotlin 代码插件**：实现 `plugin-api` 的 `DeepDeepPlugin`，由宿主在 App 进程内加载。插件分两种来源，实现同一套契约：

| 来源 | 说明 | 文本/图标 |
|---|---|---|
| `BUILTIN` 内置 | 编译进 APK，由 App 直接实例化 | 可用 `LocalizedText.Resource` / `IconRef.Resource` 复用 App 资源 |
| `EXTERNAL` 外部 | `.ddp` 包（dex + assets）经签名校验后由宿主在 App 进程内装载 | 只能用 `LocalizedText.Literal` 与 `asset:` 路径 |

`plugin-api` 是唯一契约工件（Android library，包名 `io.github.whywood.deepdeep.plugin.api`）：`PLUGIN_API_LEVEL = 1`、`BASELINE_API_LEVEL = 1`、`HOST_UI_API_LEVEL = 1`，SDK 版本 `0.4.0`。插件工程只依赖它，宿主内部类型一律不可用。

**安全前提**：外部插件与 App 同进程、同 UID、没有沙箱，安装即信任——插件代码等同 App 代码，能读 App 私有数据、反射宿主内部、让 App 崩溃。只安装自己信任的插件；第三方工具源请走 MCP。

## 快速开始

仓库内自带两份参考：

- `plugin-sample/`：最小外部插件（两个工具 + 自定义消息卡槽位），用于真机端到端回归。
- `plugin-template/`：完整模板工程（工具 + partial 更新 + 技能 + 设置 + setup + Compose 槽位 + hooks + 签名），复制后改名即用。

前置条件：

- JDK 17+ 与 Android SDK；SDK 位置通过 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 环境变量或工程内 `local.properties` 的 `sdk.dir` 指定（模板工程不带 `local.properties`，复制后要自备 SDK 配置）。
- 模板用 `../gradlew` 调用主仓库的 Gradle wrapper，因此**工程目录要直接位于主仓库根目录下**（模板位于 `<repo>/plugin-template` 时天然成立；`cp -R plugin-template my-plugin` 复制到仓库根下也可以）。复制到主仓库外时改用主仓库 wrapper 的绝对路径（如 `/path/to/DeepDeep/gradlew`）或系统 Gradle ≥ 9.8。

从零开发一个新插件：

```bash
# 1. 发布插件 SDK 到 mavenLocal（在主仓库根目录执行）
./scripts/gradlew-locked :plugin-api:publishToMavenLocal

# 2. 复制模板并改名
cp -R plugin-template my-plugin && cd my-plugin
#    改 build.gradle.kts 的 namespace/applicationId/versionName
#    改 settings.gradle.kts 的 rootProject.name（决定产物文件名 <rootProject.name>.ddp）
#    改 src/main/assets/plugin.json 的 id/version/entryClass/name/summary/provides
#    改 src/main/kotlin 下包名与入口类

# 3. 打包 + 签名 + 自检（复用主仓库 wrapper，或系统 gradle >= 9.8）
../gradlew assemblePlugin
../gradlew signPlugin
../gradlew verifyPlugin

# 4. 产物
#    build/plugin/my-plugin.unsigned.ddp   未签名（排障用）
#    build/plugin/my-plugin.ddp            可安装的签名包
```

产物文件名由 `settings.gradle.kts` 的 `rootProject.name` 决定：只改 `build.gradle.kts` 的 `applicationId` 不会改产物名，不改 `rootProject.name` 时产物仍叫 `plugin-template.ddp`。

签名任务默认在工程内 `keys/plugin-signing.pem`/`.pub` 读写密钥（模板会把私钥权限收敛为仅所有者可读写，即 `600`），也可以用 Gradle 属性指定别的位置（不带扩展名）：

```bash
../gradlew -PpluginSigningKey=/secure/keys/my-plugin signPlugin
```

安装：把 `.ddp` 传到手机，插件页「导入插件」选择文件。首次导入会展示发布者名称/`keyId`、整包 sha256、体积、将新增的工具/技能/设置/页面与是否要求特权，并提示「插件拥有 App 全部权限」；确认信任发布者后才写入 TOFU 信任库、解包安装并加载。之后升级必须用同一发布者密钥签名。

## 工程与依赖

外部插件就是一个普通 Android application 工程，产物只需要 `classes*.dex` 与 `assets/**`（见「`.ddp` 包格式」），APK 的 manifest、资源和签名不参与。

| 项 | 值 |
|---|---|
| AGP | 9.4.1 |
| Kotlin（Compose compiler 插件同版本） | 2.4.21 |
| Compose BOM | 2026.03.00 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization-json | 1.12.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 29（插件 dex 的 minApi 不能高于宿主） |
| JVM target | 17 |

构建环境：JDK 17+；Android SDK 由 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 或 `local.properties` 的 `sdk.dir` 提供（模板不带 `local.properties`，需要自备）。

依赖规则：

- 宿主已提供的库只能 `compileOnly`：`io.github.whywood.deepdeep:plugin-api`、Compose（`ui`/`foundation`/`material3` + BOM）、`kotlinx-coroutines-core`、`kotlinx-serialization-json`。
- **禁止** `implementation` 这些库：宿主 parent-first，`plugin-api` 与 Compose 只存在宿主那一份，插件包里重复打包会触发类冲突。
- 插件内部可以带自己的第三方库，但必须一起打进 `classes*.dex`，并避开宿主已占用的包/类名。
- 不提供 `plugin-api` 之外的宿主内部类；不要反射 App 内部实现（升级会坏）。
- 原生库：`.ddp` 里的 `lib/<abi>/**` 进入签名摘要，但当前宿主不把它加入 classloader 的库搜索路径，`System.loadLibrary` 不可用。
- 没有 `res/`、没有 `R`：文案、图标、技能文件都走清单 + assets。

## plugin.json 清单

外部插件的清单固定放在包内 `assets/plugin.json`，由宿主解析与校验。字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 反向域名小写，至少两段，正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`（如 `com.example.myplugin`）；宿主用它做安装目录与唯一标识。内置插件的 id 正则为 `^[a-z][a-z0-9_-]*$` |
| `version` | string | 是 | `major.minor.patch`，可带 `-dev`/`+build` 后缀；宿主记录安装版本、判断更新 |
| `apiLevel` | int | 否（默认 1） | 插件要求的契约等级；必须 ≤ 宿主 `PLUGIN_API_LEVEL`，否则拒绝装载。不写时按 `BASELINE_API_LEVEL`（1）处理 |
| `minHostVersion` | string | 否 | 最低宿主版本。宿主版本取 `BuildConfig.VERSION_NAME`（当前 `0.2.0-dev`）；比较用 `compareCore` 只看 `major.minor.patch`、忽略后缀，所以 `0.2.0-dev` 宿主满足 `minHostVersion: "0.2.0"` |
| `entryClass` | string | 是 | 实现 `DeepDeepPlugin` 的类全名，必须有无参构造 |
| `name` | LocalizedText | 是 | 插件名，至少含 `default` |
| `summary` | LocalizedText | 是 | 一句话简介，至少含 `default` |
| `description` | LocalizedText | 否 | 详情页说明 |
| `sizeLabel` | LocalizedText | 否 | 详情页体积文案（外部插件一般由宿主按包装体积展示） |
| `homepage` | string | 否 | 必须是 `http(s)://` 链接 |
| `changelog` | string | 否 | 更新说明 |
| `icon` | string | 否 | 外部插件只支持 `asset:<包内相对路径>`，如 `asset:icon.png` |
| `ui` | object | 否 | `{ "apiLevel": 1, "kotlin"?: "2.4", "compose"?: "1.9.0" }`；参与 Compose 槽兼容判定，见「UI 扩展」 |
| `publisher` | object | 否 | `{ "name": ..., "keyId": ... }`；只做展示，信任以签名里的 `keyId` 为准 |
| `provides` | object | 否 | 贡献预检，见下；必须与实际贡献一致 |
| `installable` | bool | 否（默认 true） | 为 false 表示启用前需要额外安装步骤；外部插件一般保持 true |
| `requiresPrivilege` | bool | 否（默认 false） | 需要 ADB/Root 能力，详情页标黄提示 |
| `defaultEnabled` | bool | 否（默认 true） | 首次安装后是否默认启用 |
| `tintIcon` | bool | 否（默认 true） | 内置插件矢量图标是否按主题着色；外部插件用包内图片，一般保持默认 |

`source` 由宿主设置：JSON 清单一律按 `EXTERNAL` 解析，不能声明。

`provides` 用于安装/启用前的冲突预检与详情展示：

| 键 | 内容 |
|---|---|
| `tools` | 工具名列表，小写 `snake_case`，如 `template_echo`；不能与宿主保留工具名冲突 |
| `skills` | 技能 id 列表，如 `template-skill` |
| `settings` | 设置项叶子 key 列表（`Group` 本身不占 key） |
| `actions` | 详情页动作 id 列表 |
| `uiSlots` | 注册的槽位 id，如 `setup.content`、`tool.card.content:template_echo`、`plugin.page:home` |
| `pages` | 插件页面 id 列表 |

`LocalizedText` 写法：

```json
"name": { "default": "Plugin Template", "zh": "插件模板", "en": "Plugin Template" }
```

- `default` 为必填回退；`zh`/`en` 是语言标签，宿主按 App 语言取，缺失回退 `default`。
- `host.text(source, *args)` 支持 `String.format` 占位符（`%s`/`%d`），参数只传字符串/数字。
- 也可以给字符串简写（`"name": "Plugin Template"`），等价于只有 `default`。
- 外部插件不能用 `LocalizedText.Resource`（没有 App 资源）；`host.text()` 负责解析。

校验错误码（宿主在安装时执行，`PluginValidation`）：

| 错误码 | 含义 |
|---|---|
| `INVALID_ID` | 外部 id 不是反向域名小写格式 |
| `INVALID_VERSION` | version 不是 `major.minor.patch[后缀]` |
| `INVALID_MIN_HOST_VERSION` | minHostVersion 解析失败 |
| `HOST_VERSION_UNSUPPORTED` | 宿主版本低于 minHostVersion |
| `API_LEVEL_UNSUPPORTED` | apiLevel 不在 `1..宿主等级` |
| `UI_API_LEVEL_UNSUPPORTED` | `ui.apiLevel` 与宿主 `HOST_UI_API_LEVEL` 不匹配（**警告**：只禁用 Compose 绑定，插件仍可加载） |
| `ENTRY_CLASS_MISSING` / `INVALID_ENTRY_CLASS` | 外部插件缺入口类或类名非法 |
| `MISSING_TEXT` / `INVALID_TEXT` | name/summary 缺 `default`，或外部插件用了资源文本 |
| `INVALID_ICON` / `INVALID_URL` | 图标路径/主页不合法 |
| `INVALID_PROVIDES` | provides 条目非法或重复 |
| `INVALID_UI_SLOT` | uiSlots 中的槽位 id 未知或缺实例后缀 |
| `INVALID_SKILL` | 插件返回的技能文件不合法 |

## `.ddp` 包格式

`.ddp` 就是一个 zip（扩展名不参与判断）：

```
plugin.ddp
├── classes.dex                      # 必需，至少一个；多个按 classes.dex, classes2.dex, ... 排序装载
├── classes2.dex
├── assets/
│   ├── plugin.json                  # 必需，且只能在这个路径
│   ├── icon.png                     # 可选，清单 icon 引用
│   └── skills/...                   # 可选，技能 markdown 与附加文件
├── lib/<abi>/*.so                   # 可选；进入签名摘要，当前不加载
└── META-INF/plugin-signature.json   # 安装必需
```

约束：

- 只有 dex/assets/lib/签名四类条目；目录条目忽略。
- 条目名必须用 `/`，不能是绝对路径，不能含 `..`、`\`、`:`、NUL；同名条目拒绝。
- `assets/plugin.json` 只能是这一个路径。
- 整包 ≤ 100 MB、解压后总量 ≤ 256 MB；`classes*.dex` 序号不能重复。
- dex 由 D8/R8 产出（AGP 的 `assembleDebug` 已处理），`minSdk = 29` 保证 dex minApi 不高于宿主。
- 宿主用内存 ClassLoader 装载 `classes*.dex`（不依赖可写的 dex 文件）；安装时把整包复制到 `files/plugins/<id>/<version>/bundle/`，`assets`、`lib` 解包到同版本目录。
- 插件运行数据固定放 `files/plugins/<id>/data`（对应 `host.dataDir`），升级不丢；`host.assetsDir` 是当前版本解包后的只读 assets。
- 版本目录按版本保留：升级后的上一版本目录作为回滚点，直到下一次成功升级或卸载。

## 签名、信任与更新

### 签名算法（ddp-1）

外部插件安装必须带 `META-INF/plugin-signature.json`，算法 Ed25519（宿主优先平台 Provider，旧平台回退 BouncyCastle）。

**canonical entry digest**：把 zip 内所有非目录条目按**条目名的 UTF-8 字节序**升序排列，排除所有以 `META-INF/` 开头的条目；每个条目一行：

```
<sha256-hex-lowercase><SP><uncompressed-size-decimal><SP><entry-name>\n
```

sha256 对条目的**未压缩内容**计算，行与行直接拼接、最后一行以 `\n` 结尾；整段 UTF-8 字节即待签名消息。宿主验签时用同一算法重算。

**签名文件**正好 4 个字段（未知字段忽略）：

```json
{
  "algorithm": "Ed25519",
  "keyId": "ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213",
  "publicKey": "<base64(std) of X.509 SubjectPublicKeyInfo DER>",
  "signature": "<base64(std) of 64 字节 Ed25519 签名，签 canonical entry digest 字节>"
}
```

**keyId 必须由公钥派生**：`keyId = "ed25519:" + 小写十六进制(SHA-256(SPKI DER))`。宿主要求签名里的 `keyId` 与公钥重算结果一致（防止签名自称成别人的 keyId）；该值同时作为发布者指纹与 TOFU 信任库的键。

### 密钥管理

- 插件作者本地生成密钥（`KeyPairGenerator.getInstance("Ed25519")`），私钥保存为 PKCS#8 PEM，**不入版本库、不外发**；公钥可随签名一起分发。
- `plugin-template` 首次 `signPlugin` 自动生成 `keys/plugin-signing.pem`/`.pub`，模板 `.gitignore` 已忽略 `keys/`。私钥丢了就无法再发同名更新。
- 仓库内 `plugin-sample/signing/dev-ed25519.pem` 是提交的**测试**密钥，只用于 sample 回归，任何人不该用它签分发插件。

### 信任（TOFU）与更新

- 首装是唯一信任决策点：确认页展示发布者名/`keyId`、整包 sha256、将新增的能力，并写明「插件拥有 App 全部权限」。确认后 `keyId → publicKey` 记入 TOFU 信任库。
- 每次装载前用安装记录里的 `bundleSha256` 复核包体，并重算 canonical digest 验签；不匹配则拒绝并记 `lastError`。
- **更新必须同钥**：`id` 相同的新版本必须用同一 `keyId` 签名，否则按「发布者不匹配」拒绝。
- 更新流程：选择新包 → 校验 sha256 + 签名（同钥）→ 暂存到新版本目录，相位变 `PENDING_RESTART`，**下次进程启动时激活**；激活走 prepare → probe → commit → swap，probe 阶段会注入归一化设置、调用 `onUpgrade(fromVersion)` 并预生成贡献；probe/装载失败会丢弃 pending 并删除坏版本目录，旧版本继续可用；commit 写盘失败保留 pending 到下次启动重试；错误都写进 `lastError`，详情页可见。
- 安装记录保留 `previousVersion`/`previousInstallPath`/`previousBundleSha256` 作为回滚点。恢复规则：上次激活中断 → 下次启动跳过自动激活，相位 `FAILED` 并提示「上次激活中断」；同一版本连续 2 次启动失败 → 自动停用（`enabled=false`），用户手动启用即重新授权。恢复入口见「失败恢复」一节。

### 分发索引

内部分发使用统一索引 `index.json`，App 侧索引地址与自动检查开关存在 `files/plugins/sources.json`（插件页「插件源」可编辑）：

```json
{
  "schemaVersion": 1,
  "plugins": [
    {
      "id": "io.github.whywood.deepdeep.plugintemplate",
      "version": "1.0.1",
      "apiLevel": 1,
      "minHostVersion": "0.2.0",
      "url": "https://example.com/plugins/plugin-template-1.0.1.ddp",
      "sha256": "<.ddp 整包 sha256>",
      "size": 123456,
      "publisherKeyId": "ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213"
    }
  ]
}
```

- `schemaVersion` 必须为 1；`url` 支持 `http`/`https`/`file`；`sha256`/`publisherKeyId` 是候选筛选与下载校验依据，发布者信任仍来自首装 TOFU。
- 索引地址与 `autoCheck`、`intervalHours`（1–720）存 `sources.json`，schema 1；索引本身最大 2 MB、最多 512 条。
- 下载后校验 `size` + `sha256` + 签名同钥，再暂存；更新同样要经过用户确认。
- 不引入第三方仓库/二进制分发依赖。

## 生命周期与相位

宿主为每个插件维护相位（`PluginsUiState` 里可见）：

| 相位 | 含义 |
|---|---|
| `IMPORTED` | 已导入、尚未装载 |
| `INSTALLED` | 已安装但未装载 / 需要先安装 |
| `ACTIVE` | 已启用且运行正常，贡献全部生效 |
| `DEGRADED` | 已启用但 `availability()` 返回不可用：技能不启用、动作不显示，详情页显示原因；工具/提示词/UI/hooks 由插件自己决定是否继续提供 |
| `FAILED` | 装载/启用/激活失败，原因见 `lastError` |
| `DISABLED` | 用户关闭了插件（「用户选择 ≠ 加载成功」） |
| `PENDING_RESTART` | 有暂存的新版本，重启后激活 |

只有已装载运行的插件（`ACTIVE`；`DEGRADED` 同样会装载，`PENDING_RESTART` 时运行的仍是旧版本）会聚合贡献。`availability()` 不可用时宿主会隐藏状态/动作并停用技能，**但不会自动清空插件的工具/提示词/UI/hooks**——不可用的能力请在 `contributions()`/`ui()` 里按 availability 自行返回空。

`DeepDeepPlugin` 生命周期方法（`availability()`/`status()` 必须实现，其余方法都有默认空实现；回调异常由宿主隔离，不会拖垮 App）：

| 方法 | 调用时机与责任 |
|---|---|
| `availability(host)` | 每次刷新调用；返回 `Unavailable(reason)` 时相位 `DEGRADED`、详情页显示原因，技能停用、动作不显示；不可用的工具/提示词/UI 需插件自己按 availability 移除 |
| `status(host)` | 启用、已安装且可用时刷新调用；生成详情页状态（`Ready(label: String, account: String? = null)` / `NotReady(label: String)` / `Failed(label: String)`）。label/account 都是字符串，通常用 `host.text(LocalizedText)` 取本地化文案后传入 |
| `install(host, progress)` | 用户确认安装后（内置可下载能力与外部插件首装，更新不重复调用）；`progress.report(percent, label)` 更新进度；失败会记 `lastError`，内置插件首装失败由宿主回滚清理 |
| `uninstall(host)` | 用户删除插件前，清理插件自己的外部副作用（宿主随后删安装目录、技能覆盖） |
| `onEnable(host)` / `onDisable(host)` | 用户启/停插件；启动时对已启用插件也会调用 `onEnable` |
| `onUpgrade(host, fromVersion)` | 外部插件新版本激活的 probe 阶段调用（内置插件随 APK 升级不触发） |
| `setup(host, ui)` | 用户从插件详情「连接/配置」或 Agent 的 `plugin_setup` 拉起流程时 |
| `actions(host, status)` / `runAction(host, id, ui)` | 详情页动作按钮 / 输入区动作 chip；`DESTRUCTIVE` 是语义标记，破坏性操作请自己在 `runAction` 里确认或保证可逆 |
| `detailRows(host, status)` | 详情页只读信息行 |
| `skills(host)` | 提供技能；每次刷新调用 |
| `promptSections(host)` | 追加系统提示词段 |
| `settings()` / `onSettingsChanged(host, values)` | 声明设置 schema（`SettingSpec`）；保存后回调归一化后的整组值 |
| `contributions(host)` | 提供全局工具 + hooks |
| `contributionsFor(host, session)` | 每会话物化一次：返回该会话生效的工具 + hooks（hooks 按 `pluginId` 覆盖该插件的全局 hooks）；`session` 是 live handle（可空），只用来读会话快照；默认委托 `contributions`，只写 `contributions` 时行为不变 |
| `ui(host)` | 槽位/页面/输入区动作贡献 |
| `close()` | 插件停用/卸载/更新/进程退出时调用（宿主先取消插件 scope，最多等 5s）；`close` 之后不要再异步回调 |

`host` 是插件唯一入口（`PluginHost`）：`manifest`、`context`（宿主注入的 App 上下文，用于取资源和系统服务）、`dataDir`、`assetsDir`、`scope`、`text` 与 18 个宿主服务访问器（见「宿主服务」）。

### 每插件作用域

- 每个插件有独立的 `SupervisorJob` 与 `CoroutineScope`，即 `host.scope`。插件里启动的后台协程应挂在这个 scope 上（`host.scope.launch { ... }`）。
- 停用、卸载、更新、进程退出统一走：取消 scope → `close()`（5s 超时）→ 释放安装句柄 → 移除注册/清贡献 → 同步技能；任何一步异常都会记 `lastError` 并继续完成剩余收尾。进行中的一轮继续使用已装配的工具；会话 hooks 按本轮 run 开始时的冻结集合派发（见「事件与 hooks」），本轮结束后按最新快照生效。
- 正常退出时宿主会 best-effort 调用 `shutdown()` 收尾（Activity 结束、进程被系统回收前不保证）；**进程被强杀时不保证 `close()` 被调用**，需要跨进程存续的状态请及时写进 `host.dataDir`。
- 重新启用会创建新的 scope 与新的 `PluginHost`；插件对象可能被复用（内置），外部插件会重新装载（可能换版本）。**不要缓存 host 或 scope 到插件实例字段**，每次回调都以参数为准。
- 协程里必须处理取消（`CancellationException` 直接向上抛，不要吞）；长循环里用 `isActive` 或在阶段边界检查。

### 一次物化

宿主每次刷新对每个插件最多调用一次贡献方法，结果聚合成不可变快照；工具装配、提示词、技能同步、UI 槽位、冲突诊断、详情页都消费同一份快照，不会在消费路径重复调用插件。

- 每次刷新都会调用：`settings()`、`skills()`、`availability()`；启用且已安装时再调用 `contributions()`、`ui()`、`promptSections()`、`detailRows()`，可用时调用 `status()`，`status` 有结果时调用 `actions()`。
- `contributionsFor()` 每个会话只调用一次（同一 `SessionBinding` 从建立到关闭），该会话的工具与会话 hooks 都从这次结果取用，运行中不会因刷新重新调用；会话重建（见下条）后才重新物化。会话 hooks 按 pluginId 覆盖该插件的全局 hooks，会话贡献返回默认 `PluginHooks.None` 时沿用全局；宿主每轮 run 开始时冻结生效集合，run 结束后按最新快照解冻（见「事件与 hooks」）。
- 不要在 `contributions()`/`ui()`/`settings()` 里做重 IO 或依赖会变化的外部状态：这些方法会被频繁调用，耗时工作放 `host.scope` 异步做（如 `onEnable` 里启动轮询、`onDisable`/`close` 时靠 scope 取消停止）。
- 启停、设置变更、安装/动作完成都会触发宿主重建会话投影；忙碌会话在本轮结束的安全点生效。

### 失败恢复

插件装载/激活/启用失败时相位为 `FAILED`，`availability()` 不可用时为 `DEGRADED`。当相位是 `FAILED`/`DEGRADED` 且 `lastError` 非空时，插件详情页显示「插件故障」区，展示错误原文；恢复入口按插件来源区分：

| 入口 | 行为 |
|---|---|
| 「重试激活」（仅外部插件） | 清掉激活中断标记与启动计数后重新执行激活：已装载的插件重新注入设置并调用 `onEnable`，未装载/被自动禁用的插件重新装载并启用；若有待生效版本会完整重跑激活协议（prepare → probe → commit → swap）。成功后刷新详情页并清除错误；失败保留当前状态并显示新的错误文本 |
| 「回滚到 <previousVersion>」（仅外部插件且记录里有 `previousVersion`） | 把安装版本切回上一版本、清空 pending/previous 记录并清理失败版本目录。成功后刷新详情页，失败显示「回滚失败」 |
| 内置插件 | 没有外部激活记录，不提供上面两个按钮；「插件故障」区只显示错误文本，恢复走插件列表里的启用开关：关闭再启用即重新执行 `onEnable` |

- 同一版本连续 2 次启动失败会被自动禁用（相位 `DISABLED`，不显示「插件故障」区），手动重新启用即重新授权并清空启动计数。
- 「插件故障」区与「插件槽位」区是两回事：后者只处理 Compose 槽渲染崩溃标记与版本不兼容绑定（点「恢复」只清标记并重试渲染），前者处理装载/激活/启用失败（走激活/回滚或重新启用）。

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
| `name` | 全局工具名，小写 `snake_case`；不能与宿主保留名冲突（见下），跨插件重名时先注册者保留 |
| `label` | 工具卡片标题 |
| `description` | 给模型的工具描述，写清做什么/何时用/边界 |
| `parameters` | JSON Schema（`{"type":"object","properties":{...},"required":[...]}`），宿主原样交给 SDK |
| `promptSnippet` | 系统提示里的一句话摘要（可选） |
| `promptGuidelines` | 追加给模型的工具使用规则（可选） |
| `execute` | `suspend (PluginToolCall) -> PluginToolResult` |

宿主保留的工具名（安装期即拒绝冲突）包括：引擎默认工具 `read`/`write`/`edit`/`bash`/`ls`/`grep`/`find`，宿主工具 `ask_user_question`/`plugin_setup`/`present`/`adb_shell`/`root_shell`/`get_goal`/`create_goal`/`update_goal`/`job_*`/`timer_*`/`workshop_*`，SDK 工具 `tool_search`/`mcp_*`，以及所有 `mcp__` 前缀。

`PluginToolCall`：

- `callId`：本次调用 id，只用于日志/关联。
- `args: JsonObject`：模型传入参数。
- `sessionId`：宿主在 `execute` 时解析出的当前会话 id；没有活动会话时为空字符串。
- `signal: PluginRunSignal?`：取消信号；长任务在阶段边界调用 `signal?.throwIfCancelled()`，或用 `signal?.onCancel { ... }` 注册清理，不要吞取消异常。
- `partial(result)`：流式中间结果，有实时工具卡时展示；`execute` 的返回值才是最终结果。

`PluginToolResult`：

| 形式 | 用途 |
|---|---|
| `Text(text, details?, isError)` | 普通文本结果；`details` 是宿主侧结构化元数据（给工具卡/日志用，不进模型上下文），其中 `deepdeep.fileRefs` 是宿主保留键，插件不要读写 |
| `Rich(blocks, details?, isError)` | 多块结果：`ToolBlock.Text`、`ToolBlock.Image(data, mimeType)`（base64 进模型图片内容）、`ToolBlock.FileRef(path, mimeType?)`（content 保留一行 `file: <path> (<mime>)` 文本供模型读取，宿主同时把 `{path, mimeType}` 写入 details 保留键 `deepdeep.fileRefs`，工具卡与历史消息渲染为可点击的文件行，重开会话仍可还原；不读文件、不当附件） |
| `PluginToolResult.text(...)` / `.rich(...)` | 便捷构造 |

`details` 里除 `deepdeep.fileRefs` 外由你写入的字段原样透传给工具卡与自定义消息卡；`deepdeep.fileRefs` 由宿主按 `Rich` 里的 `ToolBlock.FileRef` 生成，插件不要覆盖它。

`isError = true` 表示一次失败的工具结果（模型可重试/解释）；`execute` 抛异常（取消除外）也会转成 error 结果，所以不要用异常传业务错误。

工具默认随启用插件进入会话；需要会话信息时用 `contributionsFor` 的 live handle：

```kotlin
override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?) = Contributions(
    tools = listOf(
        PluginTool(
            name = "session_info",
            label = "Session Info",
            description = "Return the active session id and working directory.",
            parameters = buildJsonObject { put("type", "object") },
        ) { call ->
            val snapshot = session?.snapshot()
            if (snapshot == null) {
                PluginToolResult.Text("No session is bound.", isError = true)
            } else {
                PluginToolResult.Text("session=${snapshot.sessionId} cwd=${snapshot.cwd}")
            }
        },
    ),
)
```

- `contributionsFor` 的 `session` 是**会话 live handle**：不要缓存/拷贝字段，也不要假设调用时已有活动会话。
- 在工具 `execute` 里调用 `session?.snapshot()` 取最新的 `PluginSessionSnapshot(sessionId, file, cwd)`；没有活动会话时返回 `null`。
- `session.supportsChatDelivery` 表示该会话是否支持投递自定义卡片（工作区一次性 Agent 等场景可能不支持）。

## 技能与提示词段

`PluginSkill(id, markdown, files)`：

- `id`：小写 `[a-z][a-z0-9_-]*`；全局技能 id 冲突时先注册者保留。
- `markdown`：完整 SKILL.md 内容，必须带 YAML frontmatter（`name`、`description`），正文写给模型用。
- `files`：附加文件，`PluginSkillFile(path, content?, assetPath?)` —— `path` 是技能目录内相对路径，`content`（内联字符串）与 `assetPath`（包内 `assets/<path>`）二选一；`assetPath` 会在宿主侧读成文本。
- 宿主在插件可贡献时把技能物化到 Agent 技能目录，并用归属标记在停用/卸载时清理；不要在 `skills()` 里自己写文件。

`promptSections(host)` 返回 `PromptSection(id, body, priority)`，追加到系统提示词；按 `priority` 升序拼接（小的在前），**同一插件内 id 需唯一**。

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
    SettingSpec.Group("connection", literal("Connection", "连接"), items = listOf(/* 叶子项 */)),
)
```

示例里的 `literal(...)` 是 `plugin-template` 的本地 helper（`LocalizedText.Literal(mapOf("default" to ..., "zh" to ...))` 的简写），**不是 SDK API**；SDK 只提供 `LocalizedText.Literal` 与 `LocalizedText.Resource`。`SettingSpec.Text` 的完整参数写法：

```kotlin
SettingSpec.Text(
    key = "api_key",
    title = LocalizedText.Literal(mapOf("default" to "API key", "zh" to "API Key")),
    default = "",
    secret = true,
    requiresRestart = true,
    summary = LocalizedText.Literal("Stored in plaintext; use host.secrets() for real tokens."),
)
```

| 类型 | 值 | 归一化 |
|---|---|---|
| `Toggle` | `"true"`/`"false"` | 非法值丢弃、回默认 |
| `Choice` | `options` 里的 value | 不在选项内丢弃、回默认 |
| `Number` | 整数 | 超界钳到 `[min, max]`，非数字丢弃 |
| `Text` | 字符串 | 非 secret 去首尾空白；`secret = true` 原样保留 |
| `Group` | 仅 UI 分组容器 | 不落值；组内叶子 key 正常保存 |

- 所有类型都支持 `requiresRestart`（保存后需重启进程生效）与 `summary`（设置页辅助说明）。
- 宿主用 `List<SettingSpec>.normalize(map)` 补默认值、丢弃非法存值；`onSettingsChanged` 收到的是归一化后的整组叶子值。
- **设置值明文存本机**（`files/plugins/settings.json`，App `allowBackup=false`）。真正的密钥/令牌用 `host.secrets()`（按插件 id 命名空间加密存储），不要塞进设置项展示。
- 设置结构尽量稳定：删 key 会丢用户值；语义变化请换 key。卸载插件不会自动清理设置值（重装后沿用），敏感数据请在 `uninstall()` 里自行清理 `host.secrets()`。

## 连接流程（setup）

需要用户登录/授权的插件通过 setup 流程与宿主交互。触发方式：用户在插件详情页点「连接」，或 Agent 调用 `plugin_setup` 工具（参数 `plugin` 取插件 id，内置可安装能力与外部 `.ddp` 都可以）；宿主打开 setup sheet 并等待用户操作。所有 `source = EXTERNAL` 的插件（内置可安装能力与外部 `.ddp`）都有连接入口；详情页「连接」会先自动启用已停用的插件再进入 setup，`plugin_setup` 工具要求插件已启用（停用时返回错误）。宿主进入 setup 前会先查询 `status()`，已经是 `Ready` 时直接返回成功，不会调用 `setup()`。

```kotlin
override suspend fun setup(host: PluginHost, ui: PluginSetupUi) {
    ui.stage(title, message, url = null, imagePath = null, progress = 0)
    if (ui.cancelled()) return
    ui.progress(50, label)
    // 长流程：轮询登录状态/读 CLI 输出，定期检查 ui.cancelled()
    ui.progress(100, done)
}
```

- `stage(...)`：展示阶段（标题/说明/`url` 显示为「打开浏览器」链接、`imagePath` 显示为二维码图片、`progress` 显示百分比）；未提供任何自定义 UI 时宿主用通用视图渲染这些字段。
- `progress(percent, label)`：更新 setup sheet 里的阶段文案与进度（安装进度会同时进系统通知）。
- `cancelled()`：用户关闭/取消了流程；长循环里要主动轮询并及时返回（抛取消异常也可，宿主按取消处理）。
- 从 `plugin_setup` 工具发起时宿主最多等 15 分钟，超时取消本次连接（已安装部分保留）；从详情页发起的连接由用户取消或切后台处理。
- App 不在前台、插件已停用、已有安装/连接在忙时宿主返回明确错误（不静默排队）；连接必须在前台完成，App 不在前台时只通过悬浮窗事件卡提示回到 App。
- 独立页面用 Compose 槽位在 setup sheet 内容区自绘；外部插件不能注册 Activity。
- 安装确认、取消、进度、系统通知、失败回退由宿主统一处理，插件不要重复弹系统 UI。

## 事件与 hooks

`PluginHooks` 全部方法有默认空实现，只覆盖需要的。hooks 在 `contributions(host)`（全局）或 `contributionsFor(host, session)`（会话级）里返回，两者汇入同一条派发管线。

**会话级 hooks**：`contributionsFor(host, session)` 返回的 `Contributions` 是该会话生效的工具 + hooks，其中 hooks 按 `pluginId` 覆盖同一插件的全局 hooks。默认实现委托 `contributions(host)`，所以只在 `contributions()` 里返回 hooks 的插件行为不变（每个插件仍只有一条）；只在 `contributionsFor` 里按会话加工具（`contributions(host).copy(tools = ...)`）也保留全局 hooks。会话贡献返回默认的 `PluginHooks.None`（未显式提供 hooks）时沿用该插件的全局 hooks，不会因为写了 `contributionsFor` 就让 hooks 静默失效。每个会话只物化一次：同一 `SessionBinding` 从建立到关闭只调用一次 `contributionsFor`，工具与会话 hooks 共用这次结果，运行中不会随插件刷新重新调用；插件能力变化（启停/设置/安装/动作）会触发宿主在安全点重建会话（新的 `SessionBinding`），重建后才重新物化。

**run 级冻结**：每轮 run 开始时（`RunStarted`）宿主冻结当时生效的 hooks 集合，本轮所有观察事件与变换/闸门都走这份集合：冻结后再启停插件、刷新贡献、新增插件都不影响本轮——已冻结的插件即使被停用，本轮剩余事件仍会派发给它；新启用的插件从下一轮开始生效。run 结束（`RunFinished`；失败结束同样以 `RunFinished(error)` 到达）并派发完本轮事件后解冻，下一轮按最新插件快照重新冻结。

工坊一次性 Agent 复用同一套 prompt/input/工具钩子与会话绑定，但它的会话事件不经过宿主会话观察事件管线，因此 21 个观察事件与 run 冻结只覆盖主会话运行路径。

### 观察事件（只通知，不能改变流程）

按插件注册顺序串行派发，单个 handler 超时 5s（`PluginHooks.OBSERVE_TIMEOUT_MS`）；超时/异常记录后继续，不影响会话。会话内事件按到达顺序逐条派发，结构化事件优先于流式 delta（delta 过载时丢弃最旧的）。

| 回调（`PluginHooks` 方法） | `PluginEvent` 类型 | 可用字段 | 时机 |
|---|---|---|---|
| `sessionStarted(event: SessionStarted)` | `PluginEvent.SessionStarted` | `sessionId`、`title?`、`workspace?` | 会话创建 |
| `sessionClosed(event: SessionClosed)` | `PluginEvent.SessionClosed` | `sessionId` | 会话关闭 |
| `runStarted(event: RunStarted)` | `PluginEvent.RunStarted` | `sessionId`、`runId` | 一轮 Agent run 开始 |
| `runFinished(event: RunFinished)` | `PluginEvent.RunFinished` | `sessionId`、`runId`、`error?`（`succeeded`） | run 结束 |
| `turnStarted(event: TurnStarted)` | `PluginEvent.TurnStarted` | `sessionId`、`turnId` | 模型回合开始 |
| `turnFinished(event: TurnFinished)` | `PluginEvent.TurnFinished` | `sessionId`、`turnId`、`failed` | 模型回合结束 |
| `messageStarted(event: MessageStarted)` | `PluginEvent.MessageStarted` | `sessionId`、`messageId`、`role`（`USER/ASSISTANT/TOOL/SYSTEM`） | 消息开始 |
| `messageDelta(event: MessageDelta)` | `PluginEvent.MessageDelta` | `sessionId`、`messageId`、`delta` | 流式增量 |
| `messageFinished(event: MessageFinished)` | `PluginEvent.MessageFinished` | `sessionId`、`messageId`、`text?` | 消息完成 |
| `toolExecutionUpdate(event: ToolExecutionUpdate)` | `PluginEvent.ToolExecutionUpdate` | `sessionId`、`callId`、`toolName`、`phase`（`STARTED/PARTIAL/FINISHED/FAILED`）、`partial?`（`PluginToolResult`） | 工具执行状态 |
| `compactionStarted(event: CompactionStarted)` | `PluginEvent.CompactionStarted` | `sessionId` | 上下文压缩开始 |
| `compactionFinished(event: CompactionFinished)` | `PluginEvent.CompactionFinished` | `sessionId`、`tokensBefore`、`tokensAfter` | 压缩完成 |
| `compactionFailed(event: CompactionFailed)` | `PluginEvent.CompactionFailed` | `sessionId`、`error` | 压缩失败 |
| `modelChanged(event: ModelChanged)` | `PluginEvent.ModelChanged` | `sessionId`、`model`（`provider/model`） | 模型切换 |
| `thinkingLevelChanged(event: ThinkingLevelChanged)` | `PluginEvent.ThinkingLevelChanged` | `sessionId`、`level` | 思考档切换 |
| `retryScheduled(event: RetryScheduled)` | `PluginEvent.RetryScheduled` | `sessionId`、`attempt`、`delayMs`、`reason?` | 安排重试 |
| `retrySucceeded(event: RetrySucceeded)` | `PluginEvent.RetrySucceeded` | `sessionId`、`attempt` | 重试成功 |
| `retryExhausted(event: RetryExhausted)` | `PluginEvent.RetryExhausted` | `sessionId`、`attempts`、`error` | 重试耗尽 |
| `mcpStatusChanged(event: McpStatusChanged)` | `PluginEvent.McpStatusChanged` | `server`、`status`（`OK/AUTH_REQUIRED/FAILED/UNKNOWN`） | MCP 状态变化 |
| `mcpToolsChanged(event: McpToolsChanged)` | `PluginEvent.McpToolsChanged` | `server`、`tools`（`List<String>`） | MCP 工具变化 |
| `busEvent(event: BusEvent)` | `PluginEvent.BusEvent` | `event`（`PluginBusEvent`：`topic`/`publisher`/`payload`/`publishedAt`） | 插件总线事件 |

共 21 个观察回调。

### 变换与闸门（可改变流程）

按插件注册顺序串行执行，单个 handler 超时 2s（`PluginHooks.TRANSFORM_TIMEOUT_MS`）；超时/异常按各阶段的失败策略处理：

| 方法 | 输入 → 输出 | 失败策略 |
|---|---|---|
| `promptTransform(request: PromptRequest): String` | 会话 id + prompt → 新 prompt，链式传递 | fail-open：保留上一个值 |
| `inputTransform(request: InputRequest): InputDecision` | 输入文本 + 图片路径 → `Continue(text, imagePaths)` 继续（链式），`Handled` 短路并丢弃本轮输入 | fail-open：保留当前输入 |
| `beforeToolCall(request: ToolCallRequest): ToolCallDecision` | 工具调用（含 MCP/动态工具）→ `Continue` / `Block(reason)` / `Rewrite(args)`（后续插件基于改写后的 args 继续） | **fail-closed**：超时/异常按 `Block` 阻止调用并给出原因 |
| `afterToolExecution(request: ToolExecutionResult): ToolResultDecision` | 工具结果 → `Keep` / `Replace(result)`（链式替换） | keep：保留上一个结果 |

注意：

- 这些回调在会话关键路径上，必须快速返回；耗时工作丢到 `host.scope` 异步做，不要在里面做网络/重 IO。
- 不要在 transform 里阻塞等待用户；需要用户确认的闸门只做同步判定。
- `inputTransform` 返回的 `imagePaths` 会真正生效：路径对应的图片会被读取并替换/加入本轮输入（无法读取的路径被忽略），无图片时返回空列表。
- `PluginHooks.None` 可用于占位；事件顺序 = 当前生效集合里的加载/注册顺序（内置插件按注册顺序，外部插件按安装顺序）。
- 用户取消导致的 `CancellationException` 会向上传播，不要吞。
- 插件停用/卸载后：工具/提示词/技能/槽位在安全点后失效；当前 run 内会话 hooks 仍按冻结集合派发（本轮结束前停用的插件会收到本轮剩余事件），下一轮按最新快照生效。

## UI 扩展

宿主固定主壳/导航/聊天骨架，插件只能向**槽位**挂 Compose 绑定或注册页面/输入区动作，不能替换整页、不能注册 Activity。

### 槽位目录

| slotId | 场景 |
|---|---|
| `setup.content` | setup sheet 内容区；可替换宿主通用阶段视图 |
| `plugin.detail.section:<sectionId>` | 插件详情页自定义区块；`sectionId` 为小写 `[a-z][a-z0-9_-]*`，同一插件可注册多个区块，按注册的 sectionId 查询 |
| `tool.card.content:<toolName>` | 指定工具调用卡片的内容区 |
| `chat.message:<customType>` | 自定义消息类型的内容区 |
| `plugin.page:<pageId>` | 插件页面内容区；页面本身通过 `PluginUiContributions.pages` 声明 |

页面与输入区动作是贡献，不是槽位：`PluginUiContributions.pages`（`PluginPage(id, title, icon?)`）与 `.composerActions`（`ComposerAction(id, label, icon?)`）。输入区动作点击后调用插件的 `runAction(host, actionId, ui)`；插件页面可通过 `host.ui().openPage(pageId)` 打开（详情页入口只列出第一个页面，多页面请在插件自己的 UI 里跳转）。

`SlotBinding(slotId, priority, content)`：同一槽位只渲染一个绑定——按 `priority` 从大到小取第一个可渲染的（同优先级按插件注册顺序），其余绑定被覆盖；没有可渲染绑定时宿主用默认视图兜底。

`SlotIds` 提供 helper：

```kotlin
SlotIds.setupContent()                       // "setup.content"
SlotIds.detailSection("account")             // "plugin.detail.section:account"
SlotIds.toolCard("template_echo")            // "tool.card.content:template_echo"
SlotIds.chatMessage("card")                  // "chat.message:card"
SlotIds.page("home")                         // "plugin.page:home"
SlotIds.matches(slotId)                      // 校验完整槽位 id
```

### Compose 渲染

`SlotContent.Compose(uiApiLevel, render: @Composable (SlotState) -> Unit)` 是唯一 UI 扩展方式，`SlotState` 取决于槽位族：

| 槽位族 | 状态 | 字段 |
|---|---|---|
| setup | `SetupSlotState` | title/message/url/imagePath/progress/cancelled |
| 详情区块 | `DetailSlotState` | pluginId/status/rows |
| 工具卡 | `ToolCardSlotState` | callId/toolName/args/result（结构化参数与结果） |
| 自定义消息 | `CustomMessageSlotState` | messageId/customType/payload |
| 插件页面 | `PluginPageState` | pluginId/pageId/sessionId |
| 无状态时 | `NoSlotState` | — |

兼容判定（任一不满足只禁用该绑定并显示原因，不拒绝插件）：

- 绑定里的 `uiApiLevel <= HOST_UI_API_LEVEL`。
- 清单 `ui.apiLevel <= HOST_UI_API_LEVEL`（未写 `ui` 块时按宿主等级放行）。
- 清单若填了 `ui.kotlin`，需与宿主 Kotlin **主次版本**一致（如 `2.4`）。
- 清单若填了 `ui.compose`，需与宿主 Compose runtime 版本**完全一致**（不是 Compose BOM 版本）；宿主探测不到自己的 Compose 版本时同样禁用该绑定。不确定宿主版本时不要填这两个字段，只写 `ui.apiLevel`。

```kotlin
override fun ui(host: PluginHost) = object : PluginUiContributions {
    override val bindings = listOf(
        SlotBinding(
            slotId = SlotIds.setupContent(),
            priority = 10,
            content = SlotContent.Compose(uiApiLevel = 1) { state -> MySetupContent(host, state) },
        ),
        SlotBinding(
            slotId = SlotIds.toolCard("template_echo"),
            content = SlotContent.Compose(uiApiLevel = 1) { state -> MyToolCard(state) },
        ),
    )
}
```

- 槽位渲染在 Compose 主线程；`render` 里不要做阻塞 IO，用 `remember` + `LaunchedEffect` 加载数据并处理取消。
- 槽位异常由宿主兜底：进入组合时写崩溃标记，组合正常释放时清除；若进程在渲染期间死亡，下次启动只禁用该绑定并回退宿主默认视图。插件详情页的「插件槽位」区块会列出崩溃绑定（可点「恢复」重试）与不兼容绑定（带具体原因）；升级插件版本后崩溃标记自动失效。一个绑定崩溃不影响同插件其它槽位。
- 插件详情页可注册多个区块：宿主按绑定顺序逐个渲染每个 `plugin.detail.section:<sectionId>`，各自拿到 `DetailSlotState`。
- 外部插件不能引用 App 资源；图标用 `IconRef.Asset("asset:<包内路径>")`。

### 宿主组件

`plugin-api` 的 `io.github.whywood.deepdeep.plugin.api.ui` 包提供基于宿主 `MaterialTheme` 的最小组件库，复杂界面可直接用 Material3：

| API | 用途 |
|---|---|
| `PluginTheme` | `colors` / `typography` / `spacing`，读取宿主主题 |
| `PluginSpacing` | `xs`=4dp、`sm`=8dp、`md`=16dp、`lg`=24dp |
| `PluginCard(content)` | 宿主风格卡片容器（整宽、内边距 16dp） |
| `PluginSection(title, content)` | 带标题的区块，内容间距 8dp |
| `PluginText(text, style = bodyMedium)` | 正文文本 |
| `PluginHint(text)` | 辅助说明（小字、次要色） |
| `PluginButton(text, onClick, enabled = true)` | 主按钮 |
| `PluginProgress(progress: Float?)` | `null` 为不确定进度，否则 0..1 |
| `PluginEmpty(text)` / `PluginError(message)` | 空态 / 错误文案 |

```kotlin
import io.github.whywood.deepdeep.plugin.api.ui.PluginButton
import io.github.whywood.deepdeep.plugin.api.ui.PluginCard
import io.github.whywood.deepdeep.plugin.api.ui.PluginHint
import io.github.whywood.deepdeep.plugin.api.ui.PluginProgress
import io.github.whywood.deepdeep.plugin.api.ui.PluginSection
import io.github.whywood.deepdeep.plugin.api.ui.PluginText

@Composable
private fun MyCard() {
    PluginCard {
        PluginSection(title = "连接状态") {
            PluginText("已连接")
            PluginHint("令牌保存在插件加密存储中")
            PluginProgress(progress = 1f)
        }
        PluginButton(text = "重新连接", onClick = { /* ... */ })
    }
}
```

### 自定义消息卡

在工具执行里用 `host.chat().postCustomMessage(...)` 把卡片写进当前会话：

```kotlin
val posted = host.chat().postCustomMessage(
    sessionId = call.sessionId,
    customType = "my.card",
    payload = buildJsonObject { put("title", "Hello") },
    text = "卡片摘要",
)
```

- `sessionId` 用 `PluginToolCall.sessionId`（宿主在 execute 时解析）或 `session.snapshot()?.sessionId`。
- `display = false` 写入会话历史但不显示在聊天里；返回 `false` 表示没有可用会话/投递失败。
- 消息写入 pi JSONL 的 `custom_message` 条目，重开会话后仍可还原，由 `chat.message:<customType>` 槽位渲染。

## 宿主服务

`PluginHost` 暴露 18 个服务访问器；宿主没提供该服务时**抛 `PluginServiceUnavailable`**（不是返回 null），插件应 `runCatching` 后降级。服务已装配但当前不可用（如未装 Linux、未授权 ADB）时，通常通过 `available`/`enabled` 字段或明确异常表达。

```kotlin
val linux = runCatching { host.linux() }.getOrNull()
if (linux == null || !linux.available) {
    return PluginToolResult.Text("Linux 环境不可用", isError = true)
}
```

| 访问器 | 主要能力 | 不可用/失败时 |
|---|---|---|
| `linux()` | `available`、`exec(command, cwd, timeoutMs)`、`stream(command)` → `LinuxResult`/`LinuxChunk` | 未安装/未启用运行环境时 `available=false`；`exec` 记录超时与输出截断 |
| `privilege()` | `state`（selected/effective/rootGranted/adbConnected）、`observe()`、`shell(command)` | 未选 ADB/Root 或后端未就绪时 `shell` 抛 `IllegalStateException` |
| `screen()` | `status`、`startVirtual/stopVirtual`、`observe`、`screenshot`、`apps`、`launch`、`tap/tapNode/swipe/scroll/type/key`、`waitFor` | 未授权时能力标记为不可用，调用抛异常 |
| `notifications()` | `enabled`、`notify(PluginNotification)`、`updateProgress`、`cancel` | 通知权限未开时 `enabled=false`，发布被忽略 |
| `secrets()` | `get/put/remove/clear`，按插件 id 命名空间加密存储 | 宿主未提供时抛 `PluginServiceUnavailable` |
| `network()` | `request(method,url,headers,body,timeoutMs)`、`download(url,targetPath,expectedSha256,onProgress)`；扩展 `get`/`post` | 网络错误抛异常；响应体上限 2 MB、下载上限 512 MB |
| `files()` | 插件数据/工作区文件：`list/readText/writeText/readBytes/writeBytes/delete/exists/resolve` | 相对路径相对插件 `dataDir`；绝对路径原样使用 |
| `sessions()` | `active`、`observe()`（含 busy 变化）、`list()`、`prompt(text, sessionId?)` | 无活动会话时 `prompt` 返回 false |
| `jobs()` | `observe()`、`start(command,label,cwd,sessionId)`、`read(jobId)`、`kill(jobId)` | 无会话/宿主未提供时 `start` 返回 null |
| `timers()` | `schedule(TimerRequest)`（`Once`/`Cron`）、`list()`、`cancel(id)` | 参数非法/宿主未提供时返回 null 或抛异常 |
| `goals()` | `observe()`、`get/create/edit/pause/resume/complete/clear` | 目标状态非法时返回 null |
| `workshop()` | 创意工坊：`scan/load/create/edit/delete/run` | 宿主未提供时抛 `PluginServiceUnavailable` |
| `mcp()` | `servers()`、`probe(name)`、`tools(server)`、`call(server,tool,arguments)` | 服务器不存在时返回错误结果或异常 |
| `ui()` | `openUrl`、`share(ShareRequest)`、`toast`、`openPage(pageId)` | 单项未接线时抛 `PluginServiceUnavailable("ui.xxx")` |
| `app()` | `info: AppInfo`（versionName/versionCode/pluginApiLevel/uiApiLevel/language/androidSdkInt/deviceModel/installationId） | 始终可用 |
| `bus()` | 插件总线：`publish(PluginBusEvent)`、`subscribe(topic?)` → `Flow<PluginBusEvent>` | 宿主未提供时抛 `PluginServiceUnavailable` |
| `logs()` | `log(level, message, error?)` + `debug/info/warn/error` | 始终可用 |
| `chat()` | `postCustomMessage(sessionId, customType, payload, text, display)`：写入会话 JSONL 并实时投递到聊天界面 | 无活动会话/投递失败返回 false |

各服务最小用法：

```kotlin
host.linux().exec("ls -la", cwd = host.dataDir.absolutePath)
host.privilege().state.effective
host.screen().status.virtual.available
host.notifications().notify(PluginNotification(id = "sync", title = "同步", body = "完成"))
host.secrets().put("token", value)
host.network().get("https://example.com/api")
host.files().writeText("state.json", "{}")
host.sessions().prompt("继续", sessionId)
host.jobs().start("sleep 5", "demo", cwd = null)
host.timers().schedule(TimerRequest("提醒", "喝水", TimerTrigger.Once(System.currentTimeMillis() + 60_000)))
host.goals().get(sessionId)
host.workshop().scan()
host.mcp().servers()
host.ui().toast("已复制")
host.app().info.versionName
host.bus().publish(PluginBusEvent(topic = "demo.sync", publisher = "", payload = buildJsonObject { }))
host.logs().info("plugin ready")
host.chat().postCustomMessage(call.sessionId, "my.card", buildJsonObject { }, text = "卡片摘要")
```

**总线**：`host.bus().publish(PluginBusEvent(topic, publisher, payload))` 广播给所有插件；`subscribe("topic")` 拿 `Flow`，同时 `PluginHooks.busEvent(...)` 观察类回调也能收到。`publisher` 由宿主按插件 id 覆盖。事件只用于插件间协作与轻量通知，不要用它传大载荷或做同步 RPC。

## ABI 与版本

- `PLUGIN_API_LEVEL = 1`：宿主加载时要求 `plugin.apiLevel <= 宿主等级`；未写 `apiLevel` 按 `BASELINE_API_LEVEL = 1`。
- `minHostVersion` 用 `PluginVersion.compareCore` 比较，只看 `major.minor.patch`、忽略后缀（`0.2.0-dev` 视为 `0.2.0`），避免开发版被自己的门槛卡住；完整 semver 比较按「release > prerelease、build 忽略」处理。宿主版本号来自 App 的 `BuildConfig.VERSION_NAME`（当前 `0.2.0-dev`），所以 `minHostVersion: "0.2.0"` 在当前开发版宿主上通过。
- **发布前没有兼容承诺**：当前处于发布前开发期，`plugin-api` 可以随时删改类型/签名且不升级 `PLUGIN_API_LEVEL`，插件必须按最新 SDK 重新编译；发布后才启用「同一 apiLevel 内只做加法、删改变必须升等级」的规则。SDK 版本号（当前 `0.4.0`）与 `PLUGIN_API_LEVEL` 独立维护。
- 插件必须 `compileOnly` 依赖宿主库；宿主 parent-first 保证 `plugin-api` 与 Compose 只存在宿主那一份。
- Compose 没有跨版本二进制 ABI 承诺：插件要按宿主 Kotlin/Compose 版本重新编译；版本不一致只禁用对应 Compose 绑定。

## 安全模型

- **同进程、同 UID、无沙箱**：外部插件代码与 App 代码同等权限，能读写 App 私有数据、访问已授权的系统能力（日历、无障碍、ADB/Root 桥）、反射宿主内部类、起线程、结束进程。同进程没有可信的权限收窄机制，`requiresPrivilege` 只是提示，不是边界。
- **安装即信任**：首装确认页必须让用户明确「插件拥有 App 全部权限」；签名只解决「包有没有被篡改、是不是同一个发布者」，不解决「作者是否可信」。
- **适用来源**：可信的第一方/个人插件包；第三方工具源用 MCP，不走 `.ddp`。
- **完整性**：安装记录钉死整包 sha256，每次装载前复核；canonical digest 验签防篡改；更新必须同 `keyId`；失败保留上一可用版本。
- **宿主不暴露的东西**：Provider 凭据/OAuth、ADB 配对流程、宿主内部加密密钥、pi SDK 内部类型。插件拿到的是 `PluginHost` 契约与 `host.context`。
- **插件作者的义务**：不读与本插件无关的用户数据；不把凭据写日志；删除/覆盖用户数据的动作要可逆或在 UI 里明确确认；不要用 `System.exit`/`Runtime.halt` 结束进程。
- **崩溃**：工具/回调/槽位异常会被宿主隔离成 error/失败记录；但插件在主线程、Compose 槽或工具执行中把进程搞崩仍会带崩 App（没有进程级隔离），槽渲染有崩溃标记保护。

## 调试与常见错误

构建/打包/签名（主仓库内或模板目录）：

```bash
./scripts/gradlew-locked :plugin-sample:assemblePlugin :plugin-sample:signPlugin
cd plugin-template && ../gradlew assemblePlugin signPlugin verifyPlugin
```

排查路径：

- **安装前自查**（`verifyPlugin` 的 `Signature OK` 只验证签名与 digest 一致，**不代表能被安装**；导入前逐项确认）：
  - `id` 满足外部插件正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`（至少两段，如 `com.example.myplugin`）；
  - `version` 是 `major.minor.patch[后缀]`；`apiLevel` ≤ 宿主 `PLUGIN_API_LEVEL`；`minHostVersion` 不高于宿主版本；
  - `entryClass` 类名合法、类在某个根 `classes*.dex` 里且有无参构造；
  - `provides` 与实际贡献一致（tools/skills/settings/actions/uiSlots/pages），工具名不与宿主保留名冲突；
  - 槽位 id 过 `SlotIds.matches`（`setup.content`、`plugin.detail.section:<id>`、`tool.card.content:<tool>`、`chat.message:<customType>`、`plugin.page:<pageId>`）；
  - 包内 `assets/plugin.json` 路径正确、至少一个根 `classes*.dex`。
- **安装被拒**：确认页或插件页会给出原因；常见为清单校验码（`INVALID_ID`/`INVALID_VERSION`/`INVALID_PROVIDES` 等）、`SIGNATURE_INVALID`（包被改动/公钥与 keyId 不匹配/缺签名文件）、`PUBLISHER_CONFLICT`（升级换了密钥）、`TOOL_CONFLICT`（manifest `provides.tools` 与宿主或已装插件冲突）、`VERSION_CONFLICT`（版本不比已装新）。
- **装载失败**：`ClassNotFoundException` → `entryClass` 写错或被 minify；`NoSuchMethodException` → 缺无参构造；`NoClassDefFoundError` → 宿主版本不一致或把宿主依赖打进了包。
- **行为不符**：`host.logs()` 会进宿主日志（logcat tag `DeepDeepPlugin/<pluginId>`）；插件抛出的异常宿主会隔离并记录到 `lastError`，详情页可见。
- **文件行不显示**：`ToolBlock.FileRef` 的 path 按插件给出的绝对路径原样使用；文件行由宿主从 details 保留键 `deepdeep.fileRefs` 还原，不要自己写这个键；若在 `afterToolExecution` 里 `Replace` 结果并丢掉了 details，文件行会随之消失。
- **hooks 没生效 / 停用后仍收到事件**：hooks 在 `contributions()` 或 `contributionsFor()` 返回都生效；只写 `contributionsFor` 且返回默认 `PluginHooks.None` 时沿用全局 hooks；会话级贡献每个会话只物化一次，插件启停/设置在安全点重建会话（本轮结束后）才重新物化；当前 run 内按冻结集合派发，停用/卸载的插件仍会收到本轮剩余事件，属预期行为。
- **技能没出现**：插件必须启用且 `availability()` 为 `Available`（不可用时技能不启用）；SKILL.md 必须有 frontmatter，`PluginSkill.id` 与 `name` 一致；`files` 路径必须相对且安全。
- **槽位不显示**：槽位 id 必须过 `SlotIds.matches`；`provides.uiSlots` 建议与 `ui()` 返回的绑定一致（用于安装预检与详情展示，运行期以 `ui()` 为准）；检查 `uiApiLevel` 与清单 `ui` 字段是否与宿主版本匹配；看插件详情页「插件槽位」里的禁用原因。
- **设置不生效**：`onSettingsChanged` 只在保存后收到归一化值；`requiresRestart` 的项在重启后生效；非法值会被丢弃回默认。
- **更新失败**：确认签名 `keyId` 与首装一致、整包 sha256 与记录一致；换密钥只能当新发布者重装。

## 限制（当前明确不做）

- 不做第三方插件商店/审核/沙箱：同进程插件无隔离，只能靠来源可信 + 安装确认 + 签名/TOFU。
- 不给插件注册 Activity/Service/权限：平台不允许运行期组件注册；需要独立 UI 只能另装独立 APK。
- 外部插件不能用 `res/` 资源 id；文案/图标/技能都走清单与 assets。
- 不提供 Provider 凭据/OAuth、ADB 配对流程、宿主内部加密密钥等服务；插件可以直接用 `host.context` 调 Android API，宿主不做拦截。
- 不承诺插件崩溃的进程级隔离；进程被系统强杀时不保证调用插件的 `close()`。
- 原生库不加载（`lib/**` 仅进入摘要与解包）。
- 不提供声明式 UI 节点（`UiNode` 已删除）；UI 扩展只有 Compose。

## 参考

- 契约源码：`plugin-api/src/main/kotlin/io/github/whywood/deepdeep/plugin/api/`
- 示例：`plugin-sample/`（最小）、`plugin-template/`（完整模板与签名流程）
- 插件系统取舍：[DECISIONS.md](DECISIONS.md) 的 #68–#85；产品能力边界：[GUIDE.md](GUIDE.md)
