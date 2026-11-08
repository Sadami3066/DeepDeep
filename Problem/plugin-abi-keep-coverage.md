# 插件 ABI 与 R8 keep 覆盖问题

> 状态：待讨论（发布前 + 对外插件 API 文档定稿前必须拍板）
> 记录时间：2026-11-09

## 1. 问题是什么

`plugin-api` 是 compileOnly 契约：插件在编译期依赖它，运行期直接复用宿主 APK 里的 Kotlin、协程、Compose 等实现。
宿主 release 构建开启 R8 后，这些符号可能被改名或裁剪，插件在真机上就会缺类/缺方法。

已经实际发生过的两类问题：

| 现象 | 根因 | 修复 |
|---|---|---|
| 安装/导入 `.ddp` 报「插件 ID 与市场条目不匹配」 | 验签失败被 `inspectExternal` 的 `pluginId=""` 掩盖；真实原因是 R8 混淆 BouncyCastle（Android API 30 的 JCA 没有 Ed25519，`DdpSigning.kt` 依赖 BouncyCastle 兜底） | keep `org.bouncycastle.**`；并把 `inspection.error` 原样透传 |
| 插件启用/打开插件页崩溃 | 宿主 ABI 被裁剪：`NoClassDefFoundError: kotlinx.coroutines.flow.StateFlowKt`、`NoSuchMethodError: checkNotNullParameter`、`SizeKt`、`ComposableLambdaImpl` 等 | `plugin-api/consumer-rules.pro` + 宿主 proguard keep：`plugin.api.**`、`design.**`、`kotlin.**`、`kotlinx.**`、`androidx.compose.**` |

## 2. 当前状态

- `plugin-api` 通过 `consumerProguardFiles("consumer-rules.pro")` 随 AAR 下发 keep 规则，宿主 `app/proguard-rules.pro` 也保留同名规则。
- `plugin-api` 的 `api()` 依赖：Compose（ui/foundation/material3）、kotlinx-coroutines、kotlinx-serialization-json、`:design`——全部落在当前 keep 范围内。
- 现有四个插件（飞书/教务/小米云/企微）对宿主的引用只有 `plugin.api.*`、`design.*`、Compose、协程、serialization.json，真机 release 已验证：安装、导入、启用、插件页渲染均正常，无缺类错误。
- APK 因 keep 从 33.6MB 增至约 47.6MB；体积可接受（上限 100MB 内），keep 不损运行性能。

**结论：当前已暴露的插件 API 已被保住。**

## 3. 仍然存在的缺口

1. **keep 清单是静态的**：`plugin-api` 未来新增 `api()` 依赖时，consumer-rules 不会自动跟随，release 会静默漏掉新依赖。
2. **未承诺的 androidx 库**：插件作者很容易使用 Compose 生态常用件——`androidx.activity.compose.BackHandler`、`androidx.lifecycle.compose.collectAsStateWithLifecycle`、`androidx.savedstate.*`——这些类在宿主里存在，但不在 keep 范围，release 下可能崩溃，debug 正常。
3. **keep 保名字，不保版本兼容**：宿主升级 Kotlin/Compose/plugin-api 后，方法签名变化仍可能 `NoSuchMethodError`。
4. **插件自身的 R8**：插件若开启混淆，入口类与回调需插件作者自行 keep，宿主规则覆盖不到。
5. **加载失败缺诊断**：目前只显示 failed to load，用户/作者无法判断是版本不兼容、缺库还是插件自身问题。

## 4. 建议方案

**短期（下一次发布前可做）**

- 在 consumer-rules 补 Compose 生态常用包：`androidx.lifecycle.**`、`androidx.activity.**`、`androidx.savedstate.**`（体积再增几 MB，可接受）。
- 加载失败捕获 `NoClassDefFoundError`/`NoSuchMethodError`，在插件页显示「插件与当前版本不兼容（缺少 X）」。
- 发布 checklist 增加：release 混淆包下四个官方插件安装/启用/页面冒烟。

**中期（定稿对外插件 API 时）**

- 定义插件可用依赖白名单：`plugin-api`、`design`、Compose、Kotlin、协程、serialization；白名单之外不承诺 ABI。
- 建立 keep 与依赖联动：CI 解析 `plugin-api` 的 `api()` 依赖，自动生成/校验 consumer-rules，新增依赖必须同步。
- 插件模板默认带自己的 `consumer-rules.pro`（保入口、保回调、保反射用到的类）。
- 发布链路自动校验 `.ddp` 的 id/签名/market registry 一致性，避免再出现「ID 不匹配」这类误导报错。

**长期（生态）**

- 约定插件 ABI 兼容策略：`plugin-api` 只增不减；破坏性变更升 major，加载时校验 `pluginApiVersion` 并给明确提示。
- 提供「插件兼容性检查」工具（或 `verifyPlugin` 子命令）：在混淆构建/目标宿主版本下做一次加载冒烟。
- 对外文档明确：允许用哪些库、哪些 API 属于稳定契约、插件混淆要求、错误码与诊断文案。

## 5. 对外插件 API 文档必须回答的问题

1. 插件可以依赖哪些宿主库？是否允许 `androidx.lifecycle/activity/savedstate`？
2. `plugin-api` 的版本策略与兼容承诺（只增不减？major/minor？）
3. 插件自身混淆时，哪些类必须 keep？
4. 加载失败时，宿主会给出哪些可诊断信息？
5. 官方插件的构建配置（模板、verifyPlugin、release 冒烟）是否强制？
6. keep 规则由谁维护：手写清单，还是随依赖自动生成？

## 6. 待拍板

- [ ] 是否把 `lifecycle/activity/savedstate` 补进 keep 并纳入正式契约？
- [ ] keep 采用「手写清单」还是「按 plugin-api 依赖自动生成 + CI 校验」？
- [ ] 插件 ABI 的版本策略（只增不减 / 按 semver / 加载时校验）？
- [ ] 是否把「release 混淆包下插件加载冒烟」纳入发布 checklist？
- [ ] 加载失败诊断的文案与错误码怎么定？
