# 插件 API 兼容契约

本文件定义 `plugin-api` 与 `design` 的公开 API 承诺。改这两个模块的公开类型或签名前先读本文件；工具门禁是 `./scripts/api-compat.sh`。

## 1. surface 分类

| 类别 | 含义 | 例子 | 兼容承诺 |
|---|---|---|---|
| Plugin Surface | 插件实现、调用或构造的 API | `DeepDeepPlugin`、`PluginHooks`、工厂函数、请求 DTO | 冻结；同一 `PLUGIN_API_LEVEL` 内只允许二进制兼容的加法 |
| Host Surface | 宿主实现、插件只读消费的 API | 服务接口、事件接口、结果 DTO | 可新增属性/方法，不删不改 |
| Host Only | 宿主内部实现 | manifest 解析与校验、注册表、安装记录 | 不在 SDK 内，不受承诺 |

`apiLevel` 是契约等级：**同一个 apiLevel = 二进制兼容**；插件不重新编译也要能跑。

## 2. 允许的改动（不必升 level）

- 新增可选接口（例如新的 `PromptOverridePlugin`），插件不实现就继续走旧路径；
- 给 `DeepDeepPlugin`、`PluginHooks`、`PluginUiContributions` 这类由插件实现的接口新增**带默认实现**的方法或属性；
- 给 `PluginHost` 新增服务访问器或方法（插件是调用方）；
- 给 Host Surface 的接口新增只读属性/方法，给结果 data class 新增字段（插件不构造它们）；
- manifest 新增可选字段（例如 `provides.themes`）、`PluginCapability` 新增常量；
- 给服务接口新增带默认实现的方法/属性（例如 `UiService.openPageSheet`、`PluginPageContext.presentation`）；
- 新增工厂函数、新增 value class 常量、新增 `@PluginExperimentalApi` 实验 API。

`plugin-api` 的接口默认方法依赖 Kotlin 的 JVM default 输出（当前工具链默认 `jvm-default=enable`，产物里同时保留 `DefaultImpls`）；不要关闭该行为。

## 3. 不兼容的改动（必须升 apiLevel）

- 删除或改名任何公开类、接口、方法、属性、构造器、枚举/value class 常量；
- 修改公开方法签名、参数默认值语义、返回类型、异常契约；
- 给插件**实现的接口**新增抽象（无默认实现）成员；
- 修改公开 data class 的构造器参数（含新增字段）或 `copy` 签名——插件会直接构造的类型必须走工厂；
- 把 sealed 层次扩成新子类型、给结果枚举加值（旧插件的穷尽 `when` 会抛 `NoWhenBranchMatchedException`）；结果状态请用 value class + 常量；
- 修改运行时语义：hook 超时/顺序、`availability` 不可用时的行为、错误码含义、默认值。

破坏性改动需同时：升 `PLUGIN_API_LEVEL`、更新 `MIN_SUPPORTED_API_LEVEL` 策略、更新 `plugin-api/compat/level-*.api` 基线、写 `plugin-api/CHANGELOG.md` 与宿主 release notes。

## 4. 实验 API

不确定或不承诺稳定的新能力先标 `@PluginExperimentalApi`（`@RequiresOptIn`）。插件需要显式 opt-in；实验期间允许改名或调整。转正时只做"去注解 + 纳入 ABI 基线"，不要改签名。

## 5. 弃用

- 先用 `@Deprecated(level = WARNING)` 标记，保留至少一个宿主发布周期；
- 到下一个 `PLUGIN_API_LEVEL` 才可删除；
- 删除前在兼容矩阵和 release notes 写明迁移方式。

## 6. 工具与流程

- `./scripts/api-compat.sh`：生成 `plugin-api` 与 `design` 的公开 ABI dump，并与 `plugin-api/compat/`、`design/compat/` 基线比对；出现删除/签名变化时 CI 失败，新增行允许；
- `--update` 仅用于升 level 或首次建基线；
- `check.sh --full` 包含该检查；`release.yml` 在签名打包前运行 `check.sh --full`，发布构建同样受门禁约束；
- 旧插件二进制回归：`scripts/plugin-binary-compat.sh`（设备侧，按需执行，不纳入 `check.sh`），拿市场已发布的 `.ddp` 在设备上走安装/启用/停用/升级/卸载；
- 兼容矩阵：

| apiLevel | SDK（plugin-api / design） | 宿主版本 | 说明 |
|---|---|---|---|
| 5 | 0.8.1 / 0.6.1 | ≤1.0.3 | 历史契约，1.0.4 起拒绝 |
| 6 | 1.0.x / 0.6.1 | 1.0.4+ | 工厂式构造、事件接口、prompt override 实验能力 |
| 6 | 1.1.x / 0.7.x | 1.0.6+ | 加法扩展：设备/权限/包管理/高级系统访问/Intent/独立窗口/后台任务/剪贴板/定位/联系人/短信/日历/媒体/语音/网页/宿主凭据/无障碍/设置存储、页面参数与交互式选择器、通知监听与动作、系统事件 hooks、design 新组件；插件可使用 `permissions`/`components` 清单字段 |
| 6 | 1.2.x / 0.9.x | 1.0.8+ | 主题化与插件页 sheet：manifest `provides.themes`、`UiService.openPageSheet`、`PluginPageContext.presentation`/`PluginPagePresentation`；design 主题颜色重载、`DdTheme.lightDeepDeep()` 与状态/提示/进度组件 |
