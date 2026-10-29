# DeepDeep 生产级 Android / Jetpack Compose 实践全景调研

> 目标：回答“除了页面管理，一个生产级 Android + Compose App 还要补齐什么”，并把每项落到 DeepDeep 的真实代码与数据上。个人项目、本地 AI 助手、release 用 debug 签名侧载安装，不以上架 Play 为前提；Play 政策只作为“如果将来分发/上架”的检查项，不照搬企业流程。
>
> 快照：2026-10-30，仓库 HEAD `5fa3f21`，工作树另有少量未提交改动（`DeepDeepApp.kt`、`AppScreenTransition.kt`）。版本：AGP 9.4.1 / Gradle 9.8.2 / Kotlin 2.4.21 / Compose BOM 2026.03.00 / minSdk 29 / target & compileSdk 36 / 模块 `:app :runtime :shell :design`。
>
> 证据口径：`docs/STATUS.md`（最近一轮真机验证与 995 unit tests / lint 数字）、`app/build.gradle.kts`、模块 build 文件与 `gradle/*`、`AndroidManifest.xml`、关键源码行号、`app/build/outputs/` 现成产物、`artifacts/validation/round41/perf-baseline.md`。DeepDeep 数字均为本地实测，链接全部为官方/一手来源。

## 结论先行

1. **DeepDeep 的“工程底子”不差**：手动 DI、模块依赖校验任务、995 个 JVM 单测、Compose 稳定性配置 + 编译器报告、库级 baseline profile、自定义设计系统、中英字符串完全对齐、`start/end` 布局、Keystore 加密机密、关闭备份、工坊 WebView 加固、`ApplicationExitInfo` 进程退出日志、详尽的真机验收文档——这些已超过多数个人项目。
2. **最大的系统性缺口是“发布与线上质量”而不是功能**：release 仍用 debug keystore 签名、`isMinifyEnabled=false`、`versionCode=1`、无崩溃/ANR 捕获、无 CI/静态分析门禁。对一个侧载的个人助手，这些直接关系到数据安全、可维护性与升级不中断。
3. **性能优化已进入“细节收益”阶段，但仍有明确大头**：release 冷启动（预热后）637–741ms、debug 中位 934ms（基线 967ms）；release APK 39MB 中 dex 约 31MB（R8 未开）；长会话首屏受全量 JSONL 解析限制；流式 markdown 期间仍有约 24% janky frames。启动/Baseline Profile/R8 是剩下性价比最高的三项。
4. **状态恢复是体验短板**：只有 `rememberSaveable` 与 ViewModel，没有 `SavedStateHandle`；进程被系统杀死后，输入草稿/引用/当前视图丢失，会在冷启动时跳到“最近更新”的会话而不是“上次浏览”的会话。
5. **可靠性缺口在“进程死亡之后”**：无 `BOOT_COMPLETED`、无 WorkManager/AlarmManager，提醒与定时任务依赖前台服务常驻；进程被杀/重启后要等用户再次打开 App 才补投。这是产品级“提醒类助手”必须决定的产品取舍。
6. **测试结构偏科**：JVM 单测很厚，但 Compose UI/截图/宏基准为 0；AGP 9.4.1 已内置 Compose 截图测试，补关键屏成本很低。
7. **安全上有两处具体风险**：MiCloud WebView 的 `addJavascriptInterface` 未做 origin/导航收敛（工坊的 origin-checked listener 是正确范式）；OAuth 自定义 scheme `deepdeep://oauth` 可被第三方 App 争抢（loopback 回调已是主路径，应把自定义 scheme 当兜底并校验 state）。
8. **个人开发者的减法**：不上架就不需要 Play 签名、Data safety、隐私政策、灰度、pre-launch report、Play Vitals；单人项目也不需要 Hilt、feature 级多模块、CI 设备矩阵、全链路 APM。但签名、版本号、崩溃日志、R8、lint 门禁、关键 UI 测试属于“数据安全 + 可维护性”，不随是否上架而消失。

## 立即行动（5 件，按性价比排序）

| # | 动作 | 成本 | 收益 | 落点 |
|---|---|---|---|---|
| 1 | release 改用专用 keystore + 固定 versionCode/Name 规则 | 0.5–1h | 避免换签名机后无法覆盖安装；为将来迁移留路 | `app/build.gradle.kts:37-40`（现 debug 签名、minify 关）、`:14-15`（versionCode=1） |
| 2 | 本地崩溃/ANR 可观测：debug 开 StrictMode、装 `UncaughtExceptionHandler` 落盘、把已有 `ProcessExitLog` 汇总成可分享的诊断文件 | 2–4h | 无第三方 SDK、隐私友好；崩溃不再只靠 logcat | `service/ProcessExitLog.kt`、`DeepDeepApplication.kt` |
| 3 | 开 R8 full mode + resource shrinking 做一次 release 冒烟 | 0.5–1d | release dex ~31MB、APK 39MB 有明确下降空间；启动也会受益 | `app/build.gradle.kts:40`；`app/build/outputs/apk/release/app-release.apk` |
| 4 | 基础质量门禁：lint baseline + 单测 + `verifyModuleBoundaries` 一条命令/一个 GitHub Actions workflow，并试开 Gradle configuration cache | 0.5–1d | 把现有 995 单测和模块边界变成自动防回归 | 根 `build.gradle.kts` 的 `verifyModuleBoundaries`；`gradle.properties` 无 configuration-cache |
| 5 | Compose 截图测试 + `@Preview`：先用 AGP 9.4.1 内置截图测试覆盖聊天/抽屉/设置 3 个屏 | 1d | UI 回归从“装机肉眼看”变成测试；设计系统可验证 | 当前 `@Preview` 数为 0、Compose UI 测试为 0（`gradle/libs.versions.toml:38-39` 已声明 compose-test 依赖但未使用） |

---

## 1. 架构分层与依赖规则

**为什么重要**：单模块/少量模块时，成本最低的架构收益是“依赖方向正确 + 状态与业务逻辑可测试”，而不是层数。层数错配会让重构、测试、AI 生成代码都变慢。

**业界做法**：官方推荐 UI 层（Compose + 状态持有者）/ 可选 domain 层（use case）/ data 层三段式，UI 层单向数据流（事件向上、状态向下），模块化按“功能 + 层次”切，依赖只能朝内。Now in Android 是官方完整示例：多模块 + 每 feature 的 UI/domain/data + 手动 DI 容器。

**DeepDeep 现状**：4 模块（`settings.gradle.kts`），`:app` 依赖 `:runtime :shell :design`，`:runtime/:shell/:design` 互不依赖；根 `build.gradle.kts` 有 `verifyModuleBoundaries` 任务做方向校验（这是很多个人项目没有的）。`:runtime` 承担 pi2kotlin 适配、事件、Provider 目录、凭据、MCP 配置；`:app` 承担 Compose UI、插件、前台服务与装配。UI 层基本是“无状态 composable + 参数”，聊天状态集中在单个 `ChatViewModel`（1562 行，`:ui/chat/ChatViewModel.kt`），装配集中在 `AppGraph`（403 行，service-locator 风格）。没有显式 domain 层，ViewModel 直接依赖 runtime host/store。

**差距与建议**：
- **不做**“为了 clean architecture 而加 domain 层”；但可把 `ChatViewModel` 的三个职责拆成协作对象：会话/历史加载、composer 与附件、run 事件状态机（可作为普通类 + 单测，ViewModel 只编排）。这是对 1562 行文件最直接的收益。
- 保持手动 DI；只补一条规则：UI 不直接依赖具体 store/host 实现，而是依赖现有 `AgentRuntime` 之类接口或 AppGraph 暴露的小接口，方便测试与后续替代。
- 不要把 `:app` 按 feature 拆成十来个模块；对单人项目，模块拆分的收益（并行编译/强制边界）低于跨模块样板成本。当前“4 模块 + 一个依赖校验任务”已够。
- 可选工具：`dependency-analysis-gradle-plugin`（检查未使用依赖/错误方向），但优先级低于下面的测试与发布项。

**参考**：
- [Android 应用架构指南](https://developer.android.com/topic/architecture)
- [UI 层](https://developer.android.com/topic/architecture/ui-layer) / [UI 事件](https://developer.android.com/topic/architecture/ui-layer/events) / [状态持有者](https://developer.android.com/topic/architecture/ui-layer/stateholders)
- [Data 层](https://developer.android.com/topic/architecture/data-layer) / [Domain 层](https://developer.android.com/topic/architecture/domain-layer)
- [Now in Android](https://github.com/android/nowinandroid) · [架构学习路线](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md) · [模块化学习路线](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)

## 2. 状态与数据流（ViewModel / UiState / 进程死亡）

**为什么重要**：Compose 的性能与正确性都由“状态从哪来、谁持有、怎么恢复”决定；进程死亡是 Android 独有场景，处理不好会丢用户输入/运行上下文。

**业界做法**：`ViewModel` 暴露不可变 UiState（`StateFlow`），UI 用 `collectAsStateWithLifecycle`；短暂 UI 状态用 `rememberSaveable`；需要跨进程死亡恢复的关键小状态用 `SavedStateHandle`；大块数据从持久层重读。事件用一次性 channel/SharedFlow 而非塞回 UiState；每条状态都有明确 owner。

**DeepDeep 现状**：单一 `ChatViewModel` + `mutableState`（`StateFlow`）+ 每会话 `conversationCache`；`collectAsStateWithLifecycle` 31 处、`collectAsState()` 2 处（基本正确）；`rememberSaveable` 40 处（抽屉开合、展开工作区、预览开关等）；**没有 `SavedStateHandle`**；冷启动/进程重建后 `restoreInitialSession()` 取“最近更新”的会话（`ChatViewModel.kt:976-1040`），`switchToSummary()` 把 `input`、`quote`、`attachments`、`queuedInputs` 清空（`:1025` 附近）——即草稿/引用/待发附件/排队消息在进程死亡或切会话后丢失。运行期可靠性另有 `RunLedger`、`QuestionPersistence`、goal 暂停等兜底。

**差距与建议**：
- 用 `SavedStateHandle` 存 3 类小状态：当前 `conversationId`、composer 草稿/引用（可限制长度）、抽屉/页面导航栈（若尚未 saveable）。大对象（消息列表）继续从 JSONL 恢复。
- 冷启动恢复“上次浏览会话”而不是“最近更新会话”：把 lastOpenedSessionId 落到 DataStore（`AppGraph` 已有类似概念），进程重建时优先恢复它。
- 排队输入/运行中插话属于运行时状态，进程死亡后无法恢复是合理取舍；但 UI 应明确提示“上次运行被中断”，现有 `RuntimeEvent`/interruptedRuns 已覆盖，保持即可。
- 建议在真机上验证“开发者选项 → 不保留活动”与 `adb shell am kill` 两条进程死亡路径，作为验收项（当前 `docs/DEVICE_TESTING.md` 未覆盖该场景）。

**参考**：
- [保存 UI 状态](https://developer.android.com/topic/libraries/architecture/saving-states)
- [ViewModel 的 SavedState 模块](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate) · [SavedStateHandle API](https://developer.android.com/reference/androidx/lifecycle/SavedStateHandle)
- [Compose 状态](https://developer.android.com/develop/ui/compose/state) · [在 Compose 中保存状态](https://developer.android.com/develop/ui/compose/state-saving)

## 3. 依赖注入

**为什么重要**：DI 的目标是“依赖显式 + 可替换测试”，不是框架本身。移动端启动路径短，过度 DI 会带来启动与学习成本。

**业界做法**：官方同时支持手动 DI（AppContainer/Application graph + 构造函数注入）与 Hilt；Now in Android 用手动容器 + 构造注入，Hilt 适合大型多模块团队。测试中用手动 fake 实现。

**DeepDeep 现状**：典型手动 DI：`AppGraph` 是 service locator + 生命周期容器，`viewModelFactory` 注入构造参数，模块内部大量构造注入；测试直接构造被测对象（`PluginManagerTest` 等），不依赖 Android 运行时。没有 Hilt/Dagger 依赖，也没有反射注入，R8 友好。

**差距与建议**：保持现状。若 `AppGraph` 继续膨胀，可按“会话/凭据/插件/UI 偏好”拆成多个 container 类，由 AppGraph 组合；不要引入 Hilt（对 4 模块 + 单人维护是负收益）。可在测试里补一个 `TestAppGraph` 或 fake graph 的约定，避免 UI 测试搭真 graph。

**参考**：
- [Android 依赖注入指南](https://developer.android.com/training/dependency-injection)
- [Hilt 与手动 DI 的对比](https://developer.android.com/training/dependency-injection/hilt-android)
- [Now in Android 手动 DI 容器](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md)

## 4. 测试金字塔、静态分析与 CI

**为什么重要**：测试与静态分析是“AI 生成代码/大重构”时代的安全网；DeepDeep 已经有很多行为逻辑（会话命令面、插件、工坊、多 provider），回归成本会随功能线性上升。

**业界做法**：大量快速 JVM 单测 → 少量 Compose UI 测试 → 更少端到端/设备测试；截图测试锁视觉；macrobenchmark 锁性能；lint 作为门禁（baseline 逐步清零）；CI 在每次推送跑单测 + lint + 构建。AGP 9 起官方支持 Compose 截图测试（Gradle 任务 + 测试套件），不再必须依赖 Paparazzi/Roborazzi。

**DeepDeep 现状**：995 个 JVM 单测（app 599 / runtime 364 / shell 32，`docs/STATUS.md:34`）；`androidTest` 仅 2 个设备测试（日历权限、图片归一化），Compose UI 测试 0、截图测试 0、macrobenchmark 0；测试依赖只有 JUnit4 + coroutines-test（`app/build.gradle.kts:77-83`）；lint 靠手动跑，结果 0 errors / 160 warnings（`docs/STATUS.md:34`），仓库里没有 lint baseline、没有 ktlint/detekt/spotless、没有 `.github/` CI。设备验收做得很扎实（`docs/DEVICE_TESTING.md` 242 行 + `artifacts/validation/` 多轮证据），但全靠人肉。

**差距与建议**：
- **P1：Compose 截图测试**（AGP 9.4.1 已满足前提）：先覆盖设计系统（`Dd*` 组件）与 3 个关键屏（Chat、Drawer、Settings），用固定主题/语言/字体跑在 JVM 或设备上；比“装机肉眼看”便宜且能锁住字体/CJK/深浅色回归。
- **P1：CI 骨架**：一个 workflow（或本地 `./gradlew check` 别名）跑 `testDebugUnitTest` + `lintDebug` + `verifyModuleBoundaries` + `assembleDebug`；托管型 CI 对个人项目可选，但即使只做“提交前脚本”也比没有好。
- **P2：lint baseline 提交**：先把现有 160 warnings 固化，再禁止新增；`abortOnError` 默认即可。未使用字符串/复数建议这类 warning 可逐步清。
- **P2：3–5 个 Compose UI 测试**：发送、busy 时插话、停止回填、插件设置步进、附件导入（用语义查询，不 sleep）。当前 compose-test 依赖已经声明，成本主要是写测试。
- **不做**：Robolectric 全量迁移、设备矩阵、全屏截图 golden 覆盖、覆盖率硬指标（对个人项目 ROI 低）。

**参考**：
- [测试基础](https://developer.android.com/training/testing/fundamentals) · [Compose 测试](https://developer.android.com/develop/ui/compose/testing)
- [Compose 截图测试（AGP 9+）](https://developer.android.com/studio/preview/compose-screenshot-testing) · [基于测试套件的截图测试](https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites)
- [Macrobenchmark 概览](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) · [指标说明](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- [Baseline Profiles 概览](https://developer.android.com/topic/performance/baselineprofiles/overview) · [生成 Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)
- [GitHub Actions 文档](https://docs.github.com/en/actions) · [官方 Android Skills（供 AI 编码助手用）](https://github.com/android/skills)

## 5. 性能：启动、Baseline Profile、Jank、内存、体积与 R8

### 5.1 启动与主线程

**为什么重要**：冷启动是用户对“助手类 App”的第一印象；主线程 IO/Keystore/WebView 初始化是 ANR 与卡顿的常见根因。

**业界做法**：Application 只做最小初始化，重活延迟到首个界面或后台；`App Startup` 统一初始化顺序；`reportFullyDrawn` 标记内容可见；Baseline Profile 预热 dex；Perfetto 看启动阶段；debug 下 StrictMode 抓主线程 IO。

**DeepDeep 现状**：`DeepDeepApplication.onCreate` 直接 `AppGraph(this)`（大量对象构造，`:DeepDeepApplication.kt`），`MainActivity.onCreate` 同步读 `fontPreferences.read()`（`:MainActivity.kt:53`）并在启动即 `startForegroundService`；`StorageMigration` 已移到 IO 且有版本标记（`AppGraph.kt:98-100`、`data/StorageMigration.kt`），字体预热在 IO，`prepare()` 的磁盘工作在 IO（`AppGraph.kt:377-390`）。实测：release 冷启动（预热后）637/638/674/741ms；debug 中位 934ms、基线 967ms（`docs/STATUS.md:35`）；round41 审计里 debug 包 force-stop 后 `am start -W` 冷启动 967ms（`artifacts/validation/round41/perf-baseline.md`）。没有启动 trace、`reportFullyDrawn` 或 Startup 库。

**差距与建议**：用 Perfetto/`am trace` 抓一次冷启动，确认 `AppGraph` 构造与 FGS 启动各占多少；把可延迟的 store（插件、MCP 探测、技能仓库同步等）改为首屏后懒加载；给首屏加 `reportFullyDrawn`（低风险）；debug 加 StrictMode 主线程 IO 检查。

### 5.2 Baseline Profile 与宏基准

**业界做法**：macrobenchmark 模块启动 App、生成 app 级 `baseline-prof.txt`，随 release 打包；库里自带的 profile 只是底噪。

**DeepDeep 现状**：release APK 已含**库级**合并 profile（`assets/dexopt/baseline.prof` 约 9.7KB、`META-INF/androidx.profileinstaller_profileinstaller.version`、`output-metadata.json` 里 `baselineProfiles` dex metadata），但仓库无 macrobenchmark 模块、无 app 级 profile 源，也没有性能回归测试。release 冷启动（预热后）已到 637–741ms，继续优化需要 app 级 profile（尤其首屏 Composable/JSON 解析/字体）。

**建议**：P2 加 `:benchmark`（com.android.test + macrobenchmark），先只保留“冷启动到首帧 + 抽屉开合 + 聊天滚动”3 个用例，生成 app 级 profile；把结果与 `artifacts/validation/` 对比。单人项目可以只在要做版本时跑。

### 5.3 Compose 稳定性与 Jank

**业界做法**：稳定性配置 + 编译器报告；避免不稳定参数与主线程大计算；`JankStats`/Perfetto 定位丢帧；流式场景控制每帧工作量。

**DeepDeep 现状**：已有 `compose-stability.conf`（runtime 模型、`ChatUiState`、`AttachmentRef`、`File`）与编译器报告输出（`app/build.gradle.kts:51-53`）；STATUS 记录编译器报告无 unstable ChatScreen 参数。真机 gfxinfo：输入 3/72 janky、抽屉 9/565、覆盖层冻结后后台流式 10s 帧数 1178→10；**流式 markdown 仍有约 23.76% janky frames**（`docs/STATUS.md:499`），长会话首屏受全量 JSONL 解析 + 历史映射限制（`docs/STATUS.md:25`）。无 JankStats/persistent metrics。

**建议**：把流式路径的“每帧工作”继续拆（markdown 解析分块已有，但列表测量/代码高亮需确认在 settle 后）；加一个 debug-only JankStats 页面；长会话首屏用“先渲染尾部 N 条 + 向上分页”替代全量解析（与产品确认，当前曾按用户要求移除分页）。

### 5.4 内存与 WebView

**业界做法**：按屏幕/会话控制持有量；WebView 按需创建销毁；LeakCanary debug 检测。

**DeepDeep 现状**：页面进出 5 轮 PSS 218MB→283MB 后平台化，线程稳定 86（无明显泄漏，`artifacts/validation/round41/perf-baseline.md`）；WebView 有 120s 空闲销毁（工坊/小米云）与 `onRelease` 清理；多会话引擎按需创建并有空闲预算。无 LeakCanary。

**建议**：debug 加 LeakCanary（仅 debugImplementation，低风险）；重点看 Activity/Compose 根与 WebView 引用。

### 5.5 体积与 R8

**业界做法**：release 开 R8 full mode + resource shrinking；APK Analyzer 看组成；大资产（字体/rootfs）单独决策。

**DeepDeep 现状**：release `isMinifyEnabled=false`（`app/build.gradle.kts:40`）；现成 release APK 39MB，dex 约 31MB（classes 14.2+10.1+7.4MB），CJK 字体约 4MB，Alpine rootfs 约 4MB，资源 ~0.9MB；debug APK 60MB。库级 baseline profile 已包含。没有 proguard 规则文件，也没有 R8 冒烟记录。

**建议（P1，性价比最高之一）**：新增 release R8 full mode 试验（`isMinifyEnabled=true` + `isShrinkResources=true` + `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`），先跑一轮冒烟：启动、登录/凭据读取、MCP、插件、工坊 WebView、JSON 序列化、图片流程。App 基本无反射/无 Hilt，预期落地顺利；如遇到 SDK/序列化问题按错误补 keep 规则。顺带把 release 的 Log 剥离（R8 自动去 `Log.d` 需配置）。

**参考**：
- [Compose 性能](https://developer.android.com/develop/ui/compose/performance) · [稳定性](https://developer.android.com/develop/ui/compose/performance/stability) · [诊断稳定性问题](https://developer.android.com/develop/ui/compose/performance/stability/diagnose)
- [应用启动时间](https://developer.android.com/topic/performance/vitals/launch-time) · [JankStats](https://developer.android.com/topic/performance/jankstats) · [内存](https://developer.android.com/topic/performance/memory) · [Android Studio Profiler](https://developer.android.com/studio/profile) · [Perfetto](https://perfetto.dev/docs)
- [R8 缩减/混淆/优化](https://developer.android.com/build/shrink-code) · [R8 full mode](https://developer.android.com/topic/performance/app-optimization/full-mode) · [逐步采用优化](https://developer.android.com/topic/performance/app-optimization/adopt-optimizations-incrementally) · [官方博客：用 R8 瘦身](https://android-developers.googleblog.com/2025/11/use-r8-to-shrink-optimize-and-fast.html)
- [APK Analyzer](https://developer.android.com/tools/apkanalyzer)

## 6. 发布工程（签名、版本、渠道、Play 政策、隐私合规）

**为什么重要**：签名决定“能不能覆盖安装/升级”，版本号决定“能否回滚与定位”；即使不上架，数据丢失与升级中断也直接伤害用户（就是你自己）。

**业界做法**：release 用独立 upload/app signing key（Play App Signing 时 upload key 与 app signing key 分离），keystore 不进仓库；`versionCode` 单调递增、`versionName` 语义化；AAB + 分阶段灰度 + pre-launch report；Play 政策（target API、Data safety、隐私政策、敏感权限声明）在发版前检查。

**DeepDeep 现状**：release 复用 debug keystore（`app/build.gradle.kts:39`）、`versionCode = 1`、`versionName = "0.2.0-dev"`（`:14-15`）、`isMinifyEnabled=false`；release 侧载安装（无 AAB、无 Play）。targetSdk 36 已达到近期 Play 对 target API 的要求（以[官方要求页](https://developer.android.com/google/play/requirements/target-sdk)为准），且已处理 edge-to-edge（`MainActivity.kt:45`、`MiCloudLoginActivity.kt:78`）。

**差距与建议**：
- **P1（即使只侧载）**：生成专用 keystore（如 `~/.android-keys/deepdeep-release.jks`，口令放本机密码管理器/环境变量），`signingConfig` 从本机私有 properties 或环境变量注入，不提交；固定 versionCode 规则（每次发布 +1，或用日期/构建号）；release 包与 debug 包可并存（applicationIdSuffix 或不同包名）。
- **P2（如果将来上架）**：AAB + Play App Signing；`targetSdk` 跟官方要求（[target API 要求](https://developer.android.com/google/play/requirements/target-sdk)）；Data safety 表（[官方申报指南](https://developer.android.com/privacy-and-security/declare-data-use)、[Data safety 表单说明](https://support.google.com/googleplay/android-developer/answer/10787469)）；隐私政策 URL；FGS `specialUse` 与全屏 intent 声明（[Play 要求](https://support.google.com/googleplay/android-developer/answer/13392821)）；特殊权限声明（`MANAGE_EXTERNAL_STORAGE`：[Play 政策](https://support.google.com/googleplay/android-developer/answer/10467955)；无障碍 API 用途）；分阶段发布（[staged rollout](https://support.google.com/googleplay/android-developer/answer/6346149)）与 [pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757)；Play Vitals 观察崩溃/ANR（[Android vitals](https://developer.android.com/google/play/vitals)）。
- **版本升级数据迁移**：已有 `StorageMigration` 版本标记模式，建议把“schemas 版本 + 一次性迁移”作为固定做法（插件 skills/settings、信念等已有部分），并在 release 前用旧版本数据回归一次。

**参考**：
- [应用签名](https://developer.android.com/studio/publish/app-signing) · [版本管理](https://developer.android.com/studio/publish/versioning) · [App Bundle](https://developer.android.com/guide/app-bundle)
- [Play 政策中心](https://play.google.com/about/developer-content-policy/) · [隐私政策要求](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Android 16 行为变更（target 36）](https://developer.android.com/about/versions/16/behavior-changes-16) · [Android 15 行为变更（edge-to-edge 等）](https://developer.android.com/about/versions/15/behavior-changes-15)

## 7. 可观测性与线上质量

**为什么重要**：侧载 App 没有 Play Vitals/崩溃后台，问题只能靠用户（你）复现；本地可观测性直接决定“出了问题能不能 5 分钟定位”。

**业界做法**：崩溃/ANR 上报（Crashlytics/Sentry/自建）、结构化日志与事件、ANR trace 收集、性能指标（启动/jank/内存）、诊断包一键导出；隐私合规下最小化采集。

**DeepDeep 现状**：有 `ProcessExitLog`（Android 11+ `ApplicationExitInfo`，记录 reason/importance/PSS/RSS/description，`service/ProcessExitLog.kt`），运行记录 `RunLedger`、中断问题/运行提示、`logcat` 日志（app+runtime 约 22 处 Log）。**没有**全局未捕获异常处理器、没有 ANR 归因、没有事件/指标上报、没有诊断导出 UI。

**差距与建议**：
- P1：debug/release 都装 `Thread.setDefaultUncaughtExceptionHandler`，崩溃栈写到 `files/diagnostics/crash-<ts>.txt`（限制大小/数量），下次启动提示并可分享；ANR 用 `ActivityManager.getHistoricalProcessExitReasons` 的 `REASON_ANR`（已有 API 基础）+ 引导用户用 `adb bugreport`（个人场景可接受）。
- P2：把现有 `ProcessExitLog`、崩溃文件、最近运行记录打包成“导出诊断”入口（隐私敏感，需用户确认）。
- 不做：Crashlytics/Firebase 等第三方 SDK（侧载 + 隐私取向 + 包体/初始化成本）；若将来上架再评估。

**参考**：
- [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo) · [ANR 调试](https://developer.android.com/topic/performance/issues/anr)
- [Android vitals](https://developer.android.com/google/play/vitals) · [StrictMode](https://developer.android.com/reference/android/os/StrictMode)
- [Firebase Crashlytics（若上架再考虑）](https://firebase.google.com/docs/crashlytics)

## 8. 无障碍

**为什么重要**：TalkBack/字体放大/对比度/触控目标是“真实生产质量”的一部分；自定义设计系统尤其容易漏。

**业界做法**：语义化控件（role/stateDescription/contentDescription）、图标按钮必须有描述、装饰图 `null`、触控目标 ≥48dp（Material）/44dp（本 App 现有规范）、颜色对比、动态字体、Compose 无障碍测试、Accessibility Scanner/TalkBack 手工回归。

**DeepDeep 现状**：`contentDescription` 92 处、`semantics {}` 9 处；`DdIconButton` 44dp 且强制传 `contentDescription`；`DdPressable`/`DdListRow` 有 role/stateDescription；大部分文字有语义标签；`DdSwitch` 有 stateDescription。**没有**无障碍测试、没有 TalkBack 手工验收记录、字体缩放（200%）未验证，Markdown 里的可点击文件链接/引用语义未专项检查。

**差距与建议**：P2 用 Compose 无障碍测试（`semantics` 查询 + 触控目标断言）覆盖 3 个关键屏；做一轮 TalkBack + 200% 字号 + 深色模式人工 pass；对 Markdown 链接/代码复制按钮补 role/label。GUI 插件的无障碍服务是功能，不是本 App 的无障碍，别混淆。

**参考**：
- [Compose 无障碍](https://developer.android.com/develop/ui/compose/accessibility) · [无障碍测试](https://developer.android.com/develop/ui/compose/accessibility/testing)
- [Android 无障碍指南](https://developer.android.com/guide/topics/ui/accessibility)

## 9. 国际化 / RTL / 字体

**为什么重要**：本 App 有中英双语文案 + 英文默认字体 + 内置 CJK 子集，i18n/RTL 做错会直接破坏界面。

**业界做法**：所有 UI 字符串走资源；`values-<lang>` 完整对齐；`start/end` 而非 `left/right`；数量用 plurals；日期/数字用 locale formatter；Android 13 起用 `localeConfig` + `LocaleManager` 接入系统 per-app language；RTL 用 pseudo-locale 验证。

**DeepDeep 现状**：24 个字符串文件 zh/en 数量完全一致（如 strings_chat 130/130、strings_settings 156/156）；自研语言切换（`LanguagePreferences.wrap` 用 `createConfigurationContext` + `setLayoutDirection`，`MainActivity.kt:39-43`），`supportsRtl=true`；布局用 `start/end`/`horizontal` 181 处、`left/right` 0 处（RTL-ready）；插件对模型可见的中文（系统提示/技能）集中在 `plugin/` 目录（13 个文件），UI 层无硬编码中文。**没有** `<plurals>`、没有 `android:localeConfig`/系统 per-app language 集成、没有 RTL locale/截图验证、没有英文单复数处理。

**差距与建议**：P2 加 plurals（文件数/会话数/附件数这类可数文案）；加 `localeConfig` 并在 API 33+ 调 `LocaleManager.setApplicationLocales`，让系统设置里也能改 App 语言（与现有自研切换并存需一次迁移）；用 pseudo-locale + RTL（如阿拉伯语）跑一轮截图/布局检查；字体缩放与 CJK 子集在 200% 字号下验证。

**参考**：
- [本地化指南](https://developer.android.com/guide/topics/resources/localization) · [按应用设置语言](https://developer.android.com/guide/topics/resources/app-languages) · [语言与文化支持（start/end）](https://developer.android.com/training/basics/supporting-devices/languages)
- [字符串资源（plurals）](https://developer.android.com/guide/topics/resources/string-resource#Plurals) · [Android 13 per-app language](https://developer.android.com/about/versions/13/features/app-languages)

## 10. 大屏 / 折叠屏 / 横竖屏

**为什么重要**：DeepDeep 面向个人手机，但横屏（输入法/看代码）与折叠屏/平板迟早会遇到；不做适配至少不要崩、不要锁死。

**业界做法**：Window Size Classes 决定 compact/medium/expanded 布局；列表-详情双栏；折叠屏铰链/姿态；横竖屏不重建丢状态（Compose + ViewModel）；键盘/IME 适配；不要固定方向。

**DeepDeep 现状**：无 `androidx.window`/adaptive 依赖、无 `values-sw600dp`、无 WindowSizeClass；Manifest 未锁方向（好事）；聊天页是单栏手机布局；`enableEdgeToEdge` 已处理系统栏。没有大屏验收项。

**差距与建议**：P2 先做“低成本三件事”：横屏 + 平板模拟器跑一遍关键页（聊天/设置/抽屉），确认无裁剪/无崩溃；字号 200% + 大屏一起看；将来若要支持平板，再引入 WindowSizeClass 做“会话列表 + 聊天”双栏（Compose adaptive layouts 已有 1.2 beta/稳定能力）。

**参考**：
- [Window Size Classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [折叠屏适配](https://developer.android.com/guide/topics/large-screens/learn-about-foldables)
- [Compose Adaptive Layouts 1.2（官方博客）](https://android-developers.googleblog.com/2025/09/unfold-new-possibilities-with-compose-adaptive-layouts-1-2-beta.html)

## 11. 设计系统

**为什么重要**：DeepDeep 的视觉是差异点，也是最容易被“随手改”破坏的部分；设计系统 + 预览/截图测试能把审美固化成资产。

**业界做法**：设计 token（颜色/字号/间距/圆角/motion）集中；组件库只暴露少量 API；深浅色、字体缩放、RTL 同时验证；用 `@Preview` 覆盖状态矩阵，截图测试锁回归；Material3 可做基线，也可像 DeepDeep 一样自研。

**DeepDeep 现状**：`:design` 模块已有 `deepDeepColors`、`DdType`、`DdMotion`、`Dd*` 一整套自研组件（`DdSwitch/DdDropdown/DdTextAction/DdListRow/DdDialog/DdSearchField/DdSegmented` 等）、深浅色、Tabler 图标 + 许可、Lora/Manrope + 内置 Noto Sans SC 子集复合字体、图标/品牌素材许可清单。**0 个 `@Preview`**、无设计系统截图测试、无组件目录页；`compose-stability.conf` 只覆盖数据模型；`Compose` 编译器报告已有输出但未进 CI。

**差距与建议**：P2 给核心组件 + 关键屏加 `@Preview`（浅/深、zh/en、字重、错误态）并在 AGP 9 截图测试里跑；建立 `design` 的 golden 截图；把许可证/字体子集生成脚本（`scripts/subset-cjk-font.py`）纳入发布检查；可选考虑 dynamic color（个人审美驱动，不做也行）。

**参考**：
- [Compose Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3) · [Material 3 设计规范](https://m3.material.io/)
- [Compose 预览](https://developer.android.com/develop/ui/compose/tooling/previews)
- [Compose 截图测试](https://developer.android.com/studio/preview/compose-screenshot-testing)

## 12. 安全与隐私

**为什么重要**：这个 App 持有 API Key/OAuth token、会话全文、日历/联系人/小米云数据与无障碍能力；侧载不代表没有攻击面（恶意 App、WebView 内容、OAuth 回调、备份/日志）。

**业界做法**：最小权限与导出面；Secrets 用 Keystore 加密；`network_security_config` 默认禁明文、debug 单独放开；WebView 禁 file/content 访问、混合内容禁、JS bridge 做 origin 校验（优先 `addWebMessageListener`）；OAuth 用 loopback/App Links 并校验 state；备份策略明确；关 debug 特性；依赖固定版本 + 校验。

**DeepDeep 现状（做得好的）**：`EncryptedSecretStore` 用 AndroidKeyStore + AES/GCM（`runtime/.../EncryptedSecretStore.kt:108-124`）；`allowBackup=false` + `data_extraction_rules` 全排除（`AndroidManifest.xml:34-36`、`res/xml/data_extraction_rules.xml`）；`usesCleartextTraffic=false`（`:42`）；导出组件很少（FileProvider `exported=false`；MiCloud/Arrange Activity `exported=false`；Accessibility service 有 `BIND_ACCESSIBILITY_SERVICE` 系统权限）；工坊 WebView 加固到位：JS 必须但 `allowFileAccess=false`/`allowContentAccess=false`/混合内容禁止、私有 origin + `addWebMessageListener` origin 白名单、外链走系统浏览器、`WebView` 调试仅 debug（`WorkshopWebView.kt:205-215`、`WorkshopBridgeHost.kt:65`）；debug 明文 Key 导入仅 `BuildConfig.DEBUG`。

**具体风险与建议**：
- **P1：MiCloud WebView 的 `addJavascriptInterface`**（`plugin/micloud/MiCloudWebWriter.kt:176`）未限制导航/origin；`addJavascriptInterface` 对 WebView 内任意页面暴露 Java 对象。建议改用 `WebViewCompat.addWebMessageListener` + origin 白名单（照工坊范式），至少加 `shouldOverrideUrlLoading` 只允许 `i.mi.com`/`account.xiaomi.com` 域；顺带对登录/写入 WebView 加 `FLAG_SECURE`（防止截屏/最近任务泄露）。
- **P1：OAuth 自定义 scheme**：`deepdeep://oauth` 在 Manifest 导出（`AndroidManifest.xml:99-104`），任意 App 可注册同 scheme 抢回调；当前主路径是 loopback + 自定义回调页。建议把自定义 scheme 视作 best-effort，OAuth 全流程校验 `state` 且不接受无 state 的 code；长期可换 HTTPS App Links（需要域名）或只用 loopback。
- **P2：MANAGE_EXTERNAL_STORAGE + REQUEST_IGNORE_BATTERY_OPTIMIZATIONS**（`:16`、`:25`）：侧载无碍，但都很敏感；确认只在用户显式开启“共享目录/后台保活”时申请（当前应是），并在文案说明用途。
- **P2：network_security_config**：显式配置 `cleartextTrafficPermitted=false`、debug-only 放开（本地调试/自定义 provider 需要时），比只靠 `usesCleartextTraffic` 更可审计；不建议证书固定（多 provider 会自找麻烦）。
- **P2：日志与诊断**：22 处 Log 未发现明显密钥/Token 输出，但 release 无 R8 时日志仍在；开 R8 后剥离日志，crash/诊断文件默认不含作者/凭据，分享前提示。
- **P3：依赖供应链**：仓库固定 + 版本目录（44 项）+ `mavenLocal` 限定 `dev.pi` 组，方向对；离线构建下可不做 dependency verification，但如果联网恢复构建，建议至少锁定版本（禁用动态版本）。

**参考**：
- [安全最佳实践（security tips）](https://developer.android.com/privacy-and-security/security-tips) · [网络安全配置](https://developer.android.com/privacy-and-security/security-config)
- [Keystore](https://developer.android.com/privacy-and-security/keystore) · [自动备份](https://developer.android.com/identity/data/autobackup)
- [WebView 指南](https://developer.android.com/guide/webapps/webview) · [JS 接口注入风险（Google 安全 FAQ）](https://support.google.com/faqs/answer/9095419)
- [Play 敏感权限/API（预览）](https://support.google.com/googleplay/android-developer/answer/16909972) · [FGS/全屏 Intent 要求](https://support.google.com/googleplay/android-developer/answer/13392821) · [所有文件访问政策](https://support.google.com/googleplay/android-developer/answer/10467955)

## 13. 构建体验

**为什么重要**：单人开发的迭代速度 = 构建 + 测试 + 安装 + 验证；DeepDeep 已有 4 模块与大量单测，构建体验会越来越贵。

**业界做法**：Gradle configuration cache/build cache/parallel；version catalog；声明 Java toolchain；模块边界/依赖分析；CI 缓存 Gradle；Build Scan；避免 float 版本。

**DeepDeep 现状**：Gradle 9.8.2、AGP 9.4.1（内置 Kotlin 支持）、version catalog（44 项）、“离线 + Maven Local”构建、`org.gradle.parallel/caching=true`、workers=2（内存 1.5G 限制）、`kotlin.code.style=official`、`android.nonTransitiveRClass=true`、根 `verifyModuleBoundaries` 任务；**未开 configuration cache**、未声明 Java toolchain（README 只写 JDK 17+）、无 CI、无依赖分析/格式检查。

**差距与建议**：P1 试开 `org.gradle.configuration-cache=true`（AGP 9 已支持，先 `--configuration-cache` 验证一次；DeepDeep 的 AppGraph/构建脚本不含动态配置，风险低）；声明 `jvmToolchain(17)`（或 `java.toolchain`）保证换机一致；保留 workers=2（App 内存有限）；把 `verifyModuleBoundaries` 与单测并进“提交前脚本”；可选 Build Scan（个人项目可选）。

**参考**：
- [优化构建速度](https://developer.android.com/build/optimize-your-build) · [Gradle Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html) · [Version Catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html)

## 14. 生命周期与进程恢复

**为什么重要**：Android 会在任意时刻回收进程；助手类 App 有“运行中任务/提醒/目标/提问”等跨进程语义，恢复策略直接影响信任。

**业界做法**：生命周期感知收集（`collectAsStateWithLifecycle`）；ViewModel 作用域；`SavedStateHandle`/`rememberSaveable`；持久化进行中的业务状态（Room/DataStore/文件）；进程死亡可重现的恢复流程；`ApplicationExitInfo` 归因。

**DeepDeep 现状**：31 处 lifecycle-aware collect（基本正确）；单 Activity + Compose 导航；抽屉/工作区展开等 UI 状态 `rememberSaveable`；会话/历史/目标/提醒/提问都有持久化与恢复逻辑（`SessionIndex`、`RunLedger`、`QuestionPersistence`、`delivery-intents`），`ProcessExitLog` 用 `ApplicationExitInfo` 记录退出原因；缺少 `SavedStateHandle`（见 §2）、缺少进程死亡专项验收；无 `BOOT_COMPLETED`（见 §15）。

**差距与建议**：补 `SavedStateHandle`（草稿/当前会话/引用/预览），把“不保留活动”与 `am kill` 纳入 `docs/DEVICE_TESTING.md`；进程重启后明确 UI 提示（已有 interrupted 机制）；提醒/目标恢复策略与产品确认（§15）。

**参考**：
- [Activity 生命周期](https://developer.android.com/guide/components/activities/activity-lifecycle) · [Lifecycle 库](https://developer.android.com/topic/libraries/architecture/lifecycle)
- [保存 UI 状态](https://developer.android.com/topic/libraries/architecture/saving-states) · [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo)

## 15. 通知 / 前台服务 / 后台任务 / 电量

**为什么重要**：DeepDeep 的价值一半在“后台继续跑/到点提醒”；Android 对后台的限制逐年收紧，错误模型会导致提醒不响、任务被杀、耗电投诉。

**业界做法**：可延迟工作用 WorkManager（含约束/重试/重启恢复）；精确提醒用 AlarmManager exact（声明 `SCHEDULE_EXACT_ALARM`）或 WorkManager 近似；长时间用户可感知的任务用 FGS 并声明正确类型（Android 14+ 必填）+ 通知权限；Doze/电池优化说明；`BOOT_COMPLETED` 恢复提醒；上线 Play 时 FGS 类型要申报用途。

**DeepDeep 现状**：`AgentService` 是 `specialUse` 前台服务（`:46`，`PROPERTY_SPECIAL_USE_FGS_SUBTYPE=personal_agent`），`START_STICKY`、`stopWithTask=false`，App 启动即拉起并常驻；通知 3 频道（runtime LOW / downloads LOW / results HIGH，`NotificationCenter.kt:329-350`）；`POST_NOTIFICATIONS` 在首启无解释直接申请（`MainActivity.kt:165-173`）；提醒在进程存活时投递回原对话，进程不在时写 `delivery-intents`、下次打开补投一次（`docs/ARCHITECTURE.md`）；无 `BOOT_COMPLETED`、无 WorkManager/AlarmManager；电池优化豁免仅 GUI 插件动作里申请；`REORDER_TASKS`/`MANAGE_EXTERNAL_STORAGE` 等权限见 Manifest。

**差距与建议**：
- P2（体验关键）：若“到点提醒”是承诺能力，补 `BOOT_COMPLETED` receiver + WorkManager（或 AlarmManager exact）作为“App 不在前台时的兜底投递”，把 FGS 只用于“正在运行的任务”。这是从“App 开着才提醒”变成“系统会提醒”的唯一路径。
- P2：通知权限加一次轻量前置说明（为什么需要通知：后台任务完成/提醒），降低拒绝率；`results` 频道 IMPORTANCE_HIGH 会弹 heads-up，确认是否必要。
- P3：给常驻 FGS 增加“空闲时停服”策略（例如无运行任务/无活跃目标 N 分钟后降级），降低耗电与通知栏噪音——需产品权衡（提醒依赖服务存活）。
- 如果上架：FGS `specialUse` 与电池优化豁免都需要 Play 声明，见 §6/§12。

**参考**：
- [前台服务](https://developer.android.com/develop/background-work/services/foreground-services) · [FGS 声明](https://developer.android.com/develop/background-work/services/fgs/declare) · [Android 14 FGS 类型必填](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [通知渠道](https://developer.android.com/develop/ui/views/notifications/channels) · [通知运行时权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) · [Doze 与待机](https://developer.android.com/training/monitoring-device-state/doze-standby)

---

## 16. 按性价比排序的行动清单

### 现在就做（≤1 周，低成本高收益）

| 优先级 | 动作 | 成本 | 收益 | 依赖/注意 |
|---|---|---|---|---|
| P1 | 专用 release keystore + versionCode 规则 | 0.5–1h | 升级不中断；换机/重装安全 | 密钥离线备份两处 |
| P1 | 本地崩溃捕获 + StrictMode(debug) + 诊断导出 | 2–4h | 出问题可定位；无第三方 SDK | 注意日志不含凭据 |
| P1 | R8 full mode + shrinkResources 冒烟 | 0.5–1d | 体积/启动明显收益 | 需一轮功能冒烟；保留 keep 规则文件 |
| P1 | 质量门禁脚本/CI（单测+lint+边界校验） | 0.5–1d | 995 单测真正防回归 | 先提交 lint baseline |
| P1 | Compose 截图测试 + `@Preview`（3 屏+核心组件） | 1d | UI 回归自动化；字体/深浅色/双语可验证 | AGP 9.4.1 已满足 |
| P2 | `SavedStateHandle` 存草稿/当前会话 | 0.5–1d | 进程死亡不再丢输入 | 草稿长度上限 |
| P2 | 试开 configuration cache + Java toolchain | 0.5d | 构建更快、换机一致 | 一次全量验证 |
| P2 | MiCloud WebView 改 origin-checked listener + FLAG_SECURE；OAuth state 校验 | 0.5–1d | 关闭两个具体安全风险 | 改动集中、可回归 |

### 以后做（按需求触发）

- **App 级 Baseline Profile + `:benchmark` macrobenchmark**：先做冷启动 + 滚动 3 个用例；版本发布前跑一次。
- **后台提醒兜底**：`BOOT_COMPLETED` + WorkManager/AlarmManager，把“进程死亡后提醒”变成承诺能力（需产品决策）。
- **无障碍专项**：Compose 无障碍测试 + TalkBack/200% 字号人工 pass。
- **i18n 增强**：`<plurals>`、`localeConfig`+系统 per-app language、RTL/pseudo-locale 验证。
- **大屏/折叠**：先做横屏/平板冒烟；需要时引入 WindowSizeClass 双栏。
- **可观测性增强**：诊断导出 UI、ANR trace 收集、debug JankStats 页面。
- **发布流程**：如果上架，再补 AAB/Play App Signing/Data safety/隐私政策/灰度/pre-launch。
- **架构细化**：拆分 `ChatViewModel` 协作对象；`:app` 内按 feature 分包（不急于拆模块）。

### 现在不需要（个人开发者减法）

- **Hilt/Dagger**：手动 DI + 4 模块足够；引入只会增加构建与学习成本。
- **feature 级多模块拆分**：单人维护，跨模块样板 > 并行编译收益；保持 4 模块 + 边界校验。
- **Play 全套**（Data safety、隐私政策、灰度、pre-launch、Play Vitals）：不上架就不适用；未来上架前一次性补齐即可。
- **Crashlytics/Sentry/Firebase**：侧载 + 隐私取向 + 包体/初始化成本；本地崩溃文件已够用。
- **证书固定（pinning）**：多 provider + 长期维护成本高，错误配置会直接断网；不做。
- **Robolectric/全量 UI 测试/覆盖率硬指标/CI 设备矩阵**：ROI 低；用关键截图 + 少量 UI 测试替代。
- **动态配色 / 全面 Material3 迁移**：DeepDeep 的视觉是差异化资产，自研设计系统已稳定；不为了“官方推荐”改视觉。
- **dependency verification/lockfiles**：离线个人构建收益有限；若恢复联网 CI 再考虑固定版本即可。

> **减法的判断标准**：这件事是否直接保护“用户数据/可升级性/可回归性”？是 → 做（签名、崩溃日志、R8、lint/单测门禁、关键截图）；只服务“企业流程/多人协作/上架合规” → 不做或等触发条件。

## 17. 参考链接（按主题）

**架构/状态/DI**
- [Android 应用架构指南](https://developer.android.com/topic/architecture) · [UI 层](https://developer.android.com/topic/architecture/ui-layer) · [状态持有者](https://developer.android.com/topic/architecture/ui-layer/stateholders) · [Data 层](https://developer.android.com/topic/architecture/data-layer) · [Domain 层](https://developer.android.com/topic/architecture/domain-layer)
- [保存 UI 状态](https://developer.android.com/topic/libraries/architecture/saving-states) · [ViewModel SavedState](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate) · [SavedStateHandle](https://developer.android.com/reference/androidx/lifecycle/SavedStateHandle) · [Compose 状态](https://developer.android.com/develop/ui/compose/state) · [Compose 状态保存](https://developer.android.com/develop/ui/compose/state-saving)
- [依赖注入](https://developer.android.com/training/dependency-injection) · [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- [Now in Android](https://github.com/android/nowinandroid) · [架构学习路线](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md) · [模块化学习路线](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md) · [Compose 官方示例集](https://github.com/android/compose-samples)

**测试/性能/构建**
- [测试基础](https://developer.android.com/training/testing/fundamentals) · [Compose 测试](https://developer.android.com/develop/ui/compose/testing) · [Compose 截图测试](https://developer.android.com/studio/preview/compose-screenshot-testing) · [测试套件截图测试](https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites)
- [Compose 性能](https://developer.android.com/develop/ui/compose/performance) · [稳定性](https://developer.android.com/develop/ui/compose/performance/stability) · [诊断稳定性问题](https://developer.android.com/develop/ui/compose/performance/stability/diagnose)
- [启动时间](https://developer.android.com/topic/performance/vitals/launch-time) · [JankStats](https://developer.android.com/topic/performance/jankstats) · [内存](https://developer.android.com/topic/performance/memory) · [Profiler](https://developer.android.com/studio/profile) · [Perfetto](https://perfetto.dev/docs)
- [Baseline Profiles 概览](https://developer.android.com/topic/performance/baselineprofiles/overview) · [生成 Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile) · [Macrobenchmark 概览](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) · [Macrobenchmark 指标](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- [R8 缩减/混淆/优化](https://developer.android.com/build/shrink-code) · [R8 full mode](https://developer.android.com/topic/performance/app-optimization/full-mode) · [逐步采用优化](https://developer.android.com/topic/performance/app-optimization/adopt-optimizations-incrementally) · [用 R8 瘦身（博客）](https://android-developers.googleblog.com/2025/11/use-r8-to-shrink-optimize-and-fast.html) · [APK Analyzer](https://developer.android.com/tools/apkanalyzer)
- [优化构建速度](https://developer.android.com/build/optimize-your-build) · [Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html) · [Version Catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html) · [官方 Android Skills（AI 编码助手）](https://github.com/android/skills)

**发布/可观测/合规**
- [应用签名](https://developer.android.com/studio/publish/app-signing) · [版本管理](https://developer.android.com/studio/publish/versioning) · [App Bundle](https://developer.android.com/guide/app-bundle) · [Play target API 要求](https://developer.android.com/google/play/requirements/target-sdk)
- [Android vitals](https://developer.android.com/google/play/vitals) · [ANR](https://developer.android.com/topic/performance/issues/anr) · [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo) · [StrictMode](https://developer.android.com/reference/android/os/StrictMode)
- [Play 政策中心](https://play.google.com/about/developer-content-policy/) · [数据安全表单](https://support.google.com/googleplay/android-developer/answer/10787469) · [申报数据使用](https://developer.android.com/privacy-and-security/declare-data-use) · [隐私政策要求](https://support.google.com/googleplay/android-developer/answer/9859455) · [pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757) · [分阶段发布](https://support.google.com/googleplay/android-developer/answer/6346149) · [FGS/全屏 Intent 要求](https://support.google.com/googleplay/android-developer/answer/13392821) · [所有文件访问政策](https://support.google.com/googleplay/android-developer/answer/10467955) · [敏感权限/API（预览）](https://support.google.com/googleplay/android-developer/answer/16909972)
- [Android 16 行为变更](https://developer.android.com/about/versions/16/behavior-changes-16) · [Android 15 行为变更](https://developer.android.com/about/versions/15/behavior-changes-15) · [Predictive Back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)

**UI/无障碍/i18n/大屏/安全**
- [Compose 无障碍](https://developer.android.com/develop/ui/compose/accessibility) · [无障碍测试](https://developer.android.com/develop/ui/compose/accessibility/testing) · [Android 无障碍](https://developer.android.com/guide/topics/ui/accessibility)
- [本地化](https://developer.android.com/guide/topics/resources/localization) · [per-app language](https://developer.android.com/guide/topics/resources/app-languages) · [语言/文化支持](https://developer.android.com/training/basics/supporting-devices/languages) · [字符串资源（plurals）](https://developer.android.com/guide/topics/resources/string-resource#Plurals) · [Android 13 per-app language](https://developer.android.com/about/versions/13/features/app-languages)
- [Window Size Classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes) · [折叠屏](https://developer.android.com/guide/topics/large-screens/learn-about-foldables) · [Compose Adaptive Layouts 1.2（博客）](https://android-developers.googleblog.com/2025/09/unfold-new-possibilities-with-compose-adaptive-layouts-1-2-beta.html)
- [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3) · [Material 3 规范](https://m3.material.io/) · [Compose 预览](https://developer.android.com/develop/ui/compose/tooling/previews)
- [安全提示](https://developer.android.com/privacy-and-security/security-tips) · [网络安全配置](https://developer.android.com/privacy-and-security/security-config) · [Keystore](https://developer.android.com/privacy-and-security/keystore) · [自动备份](https://developer.android.com/identity/data/autobackup) · [WebView](https://developer.android.com/guide/webapps/webview) · [JS 接口注入风险](https://support.google.com/faqs/answer/9095419)

**通知/后台/生命周期**
- [前台服务](https://developer.android.com/develop/background-work/services/foreground-services) · [FGS 声明](https://developer.android.com/develop/background-work/services/fgs/declare) · [Android 14 FGS 类型必填](https://developer.android.com/about/versions/14/changes/fgs-types-required) · [通知渠道](https://developer.android.com/develop/ui/views/notifications/channels) · [通知权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) · [Doze/待机](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Activity 生命周期](https://developer.android.com/guide/components/activities/activity-lifecycle) · [Lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle)
