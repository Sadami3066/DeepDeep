# 工程化 / 构建 / 测试 / 仓库卫生评审

评审范围：构建与工程配置、测试体系、跨模块反模式、文档-代码漂移、仓库卫生、i18n 工程化。不涉及单模块业务逻辑（由其他 4 位评审员覆盖）。
基线：`:app` 37,248 / `:runtime` 10,772 / `:shell` 956 / `:design` 1,940 主行；测试 20,087 行。

## 概览（工程配置现状一览表）

| 维度 | 现状（已核实） | 判断 |
|---|---|---|
| 工具链 | AGP 9.4.1、Kotlin 2.4.21、Gradle 9.8.2（wrapper `validateDistributionUrl=true`，未配 `distributionSha256Sum`）〔复核修正〕、Gradle 运行在 JDK 21，Java/Kotlin 目标 17 四模块一致 | 健康 |
| SDK 目标 | compileSdk 36 全模块一致；minSdk 29（runtime/shell/design 均同）；targetSdk 36 仅 :app | 健康 |
| 版本目录 | `gradle/libs.versions.toml` 集中 44 个条目（15 versions + 25 libraries + 4 plugins）〔复核修正〕，Compose 全走 BOM，无 BOM+显式版本混用；无重复 module 声明 | 健康 |
| 仓库声明 | `FAIL_ON_PROJECT_REPOS`；`mavenLocal` 仅通过 content filter 放行 `dev.pi` 组 | 良好 |
| 唯一外部 SDK 依赖 | `dev.pi:pi-coding-agent-android:0.2.4` 只存在于本机 `~/.m2`（README 要求先在 `../pi2kotlin` 执行 `publishToMavenLocal`） | 可复现性缺口 |
| 模块边界 | root 有 `verifyModuleBoundaries` 任务，但**不在任何生命周期任务图上**（`check --dry-run` 0 次命中） | 形同虚设 |
| release | `isMinifyEnabled=false`、`signingConfig = debug`、无 `proguard-rules.pro`、无应用级 baseline profile | 有意取舍 + 工程债 |
| 打包 | `noCompress += "rootfs"`（生效：APK 内 Stored 0%）、`jniLibs.useLegacyPackaging=true`（proot 需解压执行）、无 `abiFilters`/`splits` | 有意取舍 |
| 产物 | `app-release.apk` 40.5 MB（DEX 31.8 MB）+ `app-debug.apk` 62.7 MB；APK 含 `assets/dexopt/baseline.prof` 9,746 B（仅依赖库合并的 profile） | 体积偏大 |
| 测试 | 1,022 个 `@Test` / 104 个测试类 / 20,087 行；单测 : 主源码 ≈ 0.39；`androidTest` 仅 2 个文件；`:design` 无测试依赖、0 测试 | 金字塔底厚、顶层空缺 |
| 静态检查 | 仅 AGP Lint（手跑 `:app:lintDebug` 通过：**0 errors / 160 warnings / 5 hints**，即 165 条 issue）〔复核修正〕；无 detekt/ktlint/spotless；无 CI | 无门禁 |
| 构建性能配置 | `-Xmx1536m`、`workers.max=2`、`parallel=true`、`caching=true`；未开 configuration cache（Gradle 自身在输出里建议开启）；本机 14 GiB / 24 核，评审时 free 1.8 GiB 且 swap 已用 3.5 GiB | 防御性配置，合理但可优化 |
| 仓库规模 | tracked 635 文件 / 12.48 MB；`.git` 51 MB（5,163 个松散对象、**0 个 pack**）；`build/` 与 `artifacts/validation/`（238 MB）已忽略 | 干净但缺 gc |
| 未跟踪 | `assets/` 22 MB（品牌/吉祥物/图标源文件）、`rs.html` 4.7 MB（误落的 Android 官方 API 文档页），`.gitignore` 均未覆盖 | 卫生问题 |
| i18n | `values/`（中文）+ `values-en/`（英文），933 个 string key **100% 对齐**；但仍有硬编码中文：UI/设计层 7 处（`DdComponents.kt:380`、`McpEditor.kt:85/110/117/143` 等，见 SC-007），插件/工具层约 200 处模型错误文案（部分经插件状态显示给用户） | 良好（有例外） |

## 评分

| 维度 | 分数 | 理由 |
|---|---|---|
| 构建配置 | **6 / 10** | 版本/目标/BOM/仓库收敛都正确，打包选项与 16 KB 对齐也是对的；但边界校验未接线、死配置（`kotlin.android` alias 从未应用）、release 用 debug 签名、无应用级 baseline profile、无 ABI 收敛，构建不可脱离本机复现 |
| 测试体系 | **7 / 10** | 1,022 个用例全部 JVM 单测，手写 fake + `StandardTestDispatcher` + `MutableTestClock`，断言密度高（1,022 用例仅 1 个无断言），迁移/并发/持久化/协议层都有覆盖；但启动装配、Compose UI、WebView 真机路径零自动化，无覆盖率工具，2 个 device test 不在任何流水线里 |
| 工程卫生 | **5 / 10** | 主仓库仅 12.48 MB、无构建产物入库、`.gitignore` 覆盖主流产物；但 22 MB 源码资产未入库、4.7 MB 垃圾文件躺在根目录、`.git` 从未 gc、无根 LICENSE、作者身份拼写不一致 |
| 静态质量保障 | **3 / 10** | 只有 AGP 自带 Lint 且没人跑；无 detekt/ktlint、无 CI、无依赖校验/锁文件、无覆盖率门槛；lint 的 160 条 warning（另 5 条 hint，合计 165 条 issue；含 WebView 渲染进程崩溃未处理）对构建完全透明〔复核修正〕 |

## 亮点

- **协程纪律罕见地好**：全仓 `GlobalScope` 0 处、main 源码 `runBlocking` 0 处、显式 `CoroutineScope(` 10 处全部带 `SupervisorJob()` + 明确 dispatcher；`!=` 处用 `CancellationException` rethrow 的写法在 `SkillRepository`、`QuestionCenter`、`McpOAuthSession`、`PluginManager` 中一致（127 处引用）。
- **单测质量高**：`ChatViewModelTest` 1,662 行 / 51 用例用 fake runtime 驱动事件序列断言状态迁移（`sendShowsWorkingStateImmediatelyAndFinishes`、`ignoresStaleRunEvents`），时间全部走 `StandardTestDispatcher`/`advanceUntilIdle`；`ScheduleServiceTest` 注入 `MutableTestClock` 而非真实时钟；全仓 `@Ignore` 0 个。
- **i18n 工程化到位**：933 键中英一一对应（脚本比对 missing=0 / extra=0），UI 层零硬编码中文，插件层文案也走资源。〔复核修正：与本文第 24 行、`code-quality.md`、SC-007 自相矛盾——UI/design 层实测 ≥7 处用户可见硬编码中文（`DdComponents.kt:380`、`McpEditor.kt:85/110/117/143` 等，`ui/` 共 15 个中文字面量），插件目录 193 个中文字面量、仅 9 处走 `stringResource`。「插件层也走资源」不成立。〕
- **有意取舍执行得干净**：`noCompress += "rootfs"` 在 APK 里确实是 `Stored 0%`；`jniLibs.useLegacyPackaging=true` 与"proot 二进制需要解压后执行"一致；`libproot.so`/`libproot-loader.so` 的 ELF `p_align = 0x4000`，已满足 16 KB 页要求（比多数同规模项目领先）。
- **无 TODO 债**：main 源码真正的 `// TODO/FIXME/HACK` 0 处，`println` 0 处，`Log.d/v/w` 仅 22 处集中在 3 个文件。
- **迁移与恢复有测试**：`StorageMigrationTest`、`RunLedgerTest`、`SessionCommandPlaneTest`、`HandleBudgetTest` 覆盖了最容易出数据问题的几块；`docs/dev/DEVICE_TESTING.md` + `artifacts/validation/`（47 个证据目录、904 个文件）是有意的"人工真机验收"策略，不是遗漏。

## 问题清单

### P0

本次横向扫描未发现 P0 级问题（无数据丢失/崩溃/安全断言级缺陷）。以下 P1 是影响交付质量的主要工程债。

### P1

**[GL-001] 无 CI、无任何自动化门禁（P1）**
- 位置：仓库根（`.github/` 不存在）；`build.gradle.kts:7`
- 证据：`ls -d .github .gitlab-ci.yml` 无输出；`grep -rn "detekt\|ktlint\|spotless" --include=*.kts --include=*.toml .` 0 命中；`./gradlew check --dry-run` 输出 202 条 SKIPPED 行，全部是子模块任务，无 root 聚合任务。
- 影响：每次验证靠人手敲命令（本次评审即需手动 `:app:lintDebug`）；回归只能靠人工比对 `artifacts/validation/` 的 904 张真机截图，任何"改一处、坏另一处"不会在提交时暴露。
- 建议：根项目引入一个聚合任务（`check` 依赖 `:app:testDebugUnitTest :runtime:test :shell:test` + `lint` + `verifyModuleBoundaries`），并加最小 CI 或 pre-commit 钩子；不需要新依赖。
- 工作量：S

**[GL-002] 静态分析只有 Lint 且无门禁，160 warnings + 5 hints 对构建透明（P1）**〔复核修正〕
- 位置：`gradle/libs.versions.toml`（无 lint 插件依赖）；`app/build.gradle.kts`
- 证据：手动 `./gradlew :app:lintDebug` → `BUILD SUCCESSFUL in 1m 9s`，SARIF 统计 `total issues: 165`，最高频：`UnusedResources 79`、`IconDuplicates* 26`、`PluralsCandidate 12`、`MissingOnRenderProcessGone 6`、`ModifierParameter 6`、`AutoboxingStateCreation 5`；无 `lint.xml`/baseline、无 `detekt`/`ktlint`。
- 影响：真实缺陷被静音（例如 6 条 `MissingOnRenderProcessGone`，见 GL-009）；没有 Kotlin 风格/复杂度约束，约 7.1 万行 Kotlin（main 50,916 + test 20,087）只能靠自审〔复核修正〕。
- 建议：把 `lintDebug` 接入聚合 check；加 detekt（复杂度/长函数/魔法数）或 ktlint（`kotlin.code.style=official` 已声明但没有执行器）；对有意保留的规则写 `lint.xml` 忽略清单而不是全量静音。
- 工作量：M

**[GL-003] 关键路径零自动化：启动装配、Compose UI、WebView 全未测（P1）**
- 位置：`app/src/androidTest/kotlin/...`（仅 2 文件）；测试目录无 `AppGraph`/`MainActivity`/`DeepDeepApp` 引用
- 证据：`androidTest` 只有 `ArrangeDeviceTest.kt`、`ImageNormalizerDeviceTest.kt`；`grep -rn "createComposeRule\|ComposeTestRule" app/src/{test,androidTest}` 0 命中（`compose-test` 依赖白声明）；`AppGraph.kt` 403 行、`DeepDeepApp.kt` 1,159 行、`AgentRuntimeHost.kt` 1,083 行均无直接测试；`:design` 0 测试。
- 影响：带 1,083 行的 `AgentRuntimeHost` 与 403 行的 `AppGraph` 装配/迁移编排是当前最大的未覆盖面；Compose 层 23,444 行（`ui/`）只靠真机截图验收，无法在重构时保证不回归；`compose-test-manifest`、`compose-test` 依赖是死重量。
- 建议：至少补 3 个 Robolectric/unit 级冒烟：`AppGraph` 装配 + `DeepDeepApp` 初始路由 + `WorkshopWebView` bridge 注入；Compose UI test 可只覆盖导航栈与聊天流式渲染两条主链，跑在 `androidTest`（本轮不要求 CI）。
- 工作量：M

**[GL-004] 构建不可脱离本机复现：唯一 SDK 依赖只在 mavenLocal，且文档版本落后（P1）**
- 位置：`settings.gradle.kts:12-16`、`gradle/libs.versions.toml`（`pi = "0.2.4"`）、`README.md`（"pi2kotlin 0.2.2 已发布到 mavenLocal"）
- 证据：`mavenLocal { content { includeGroup("dev.pi") } }`；`~/.m2/repository/dev/pi/pi-coding-agent-android/` 有 0.1.0–0.2.4 共 9 个版本；README 要求 `cd ../pi2kotlin && ./gradlew publishToMavenLocal`；目录里的 pin 是 0.2.4，README 写 0.2.2。
- 影响：换机器/清 `~/.m2`/新同事克隆后 `./gradlew` 直接失败；0.2.4 与 0.2.2 的 API 差异意味着失败点不可预测；这也直接堵死了 GL-001 想加的 CI。
- 建议：二选一并写进 README：把 pi2kotlin 产物发布到私有仓库（哪怕是本地 file:// 目录 + 版本化），或把 `../pi2kotlin` 以 includeBuild 复合构建接入；同时修正 README 版本号，并在 `settings.gradle.kts` 加 `dependencySubstitution` 兜底。
- 工作量：M

### P2

**[GL-005] 模块边界校验任务从未执行，且新模块会静默漏检（P2）**
- 位置：`build.gradle.kts:8-41`
- 证据：`./gradlew check --dry-run -q | grep -c verifyModuleBoundaries` → `0`；显式 `./gradlew verifyModuleBoundaries` → `BUILD SUCCESSFUL`（说明逻辑本身能跑）。实现上 `allowed[module.path] ?: return@flatMap emptyList()` 使任何未登记的新模块完全跳过检查；第 28 行 `module.path == ":app" && target in setOf(":runtime", ":shell", ":design")` 是不可达分支（前一个 `target in targets` 已覆盖）。
- 影响：ARCHITECTURE 声明的依赖方向约束（`:runtime`/`:shell`/`:design` 不互相依赖）在无人记得手跑任务时不受保护；一旦新增 `:core` 模块，连"漏检"都不会提示。
- 建议：根项目 apply `base` plugin 并 `check.dependsOn(verifyModuleBoundaries)`；`allowed` 表由 `subprojects.map { it.path }` 驱动，未知模块直接报错而不是跳过；删掉不可达分支。
- 工作量：S

**[GL-006] release 用 debug 签名，签名密钥丢失 = 必须卸载 = 本地数据全丢（P2）**〔复核：风险描述正确，但「换专用 keystore」的**代价**被漏掉，见下方批注〕
- 位置：`app/build.gradle.kts:38-41`
- 证据：`signingConfig = signingConfigs.getByName("debug")`；`apksigner verify --print-certs app-release.apk` → `Signer #1 certificate DN: C=US, O=Android, CN=Android Debug`；`~/.android/debug.keystore` 是 SDK 自动生成的机器本地文件。
- 影响：本地分发场景下这是有意简化，但代价是"应用身份"绑在可以随手删除/重建的 debug keystore 上——换机、清 SDK、`rm -rf ~/.android` 后重签的 APK 无法覆盖安装，只能卸载，`files/` 下的会话、schedule/goal/job、DataStore 凭据全部丢失。
- 建议：生成一次性 `release-local.keystore`（`keytool -genkey`）放进项目外受控位置，`signingConfigs` 读取它；仍然本地分发，不引入额外流程。
- 工作量：S

**[GL-007] minify 关闭导致 40.5 MB APK / 31.8 MB DEX（P2，有意取舍的代价）**
- 位置：`app/build.gradle.kts:40`
- 证据：`unzip -l app-release.apk` → `classes.dex 14.2 MB + classes2.dex 10.1 MB + classes3.dex 7.5 MB`；另有 `res/zB.ttf 4.08 MB`、`assets/alpine-...rootfs 4.03 MB`、`resources.arsc 0.91 MB`。
- 影响：本地安装可接受，但 DEX 占包体 79%，其中大量是未使用的 SDK/AndroidX 代码；安装体积与冷启动 dexopt 时间都被放大，多 dex 也需要额外 optimize 成本。
- 建议：不改分发方式也可以开 R8（`isMinifyEnabled=true`）+ 默认 keep（pi SDK 可能有反射，先 `-dontwarn` 试跑），预计 DEX 降到 12–16 MB〔复核：这是猜测，无 PoC 数据；此外 pi SDK 无 consumer proguard 规则、依赖 kotlinx.serialization、`UnixShellExecutor` 含 `Class.forName` 平台类反射；项目 androidTest 仅 2 文件，R8 的真实回归成本远高于 M。建议先只开 `isShrinkResources` 做一次真机冒烟再决定〕；若不改，至少在 ARCHITECTURE 里把"未开 R8 是本地分发取舍"写明，避免新评审员反复提问。
- 工作量：M

**[GL-008] 无应用级 baseline profile / 无 macrobenchmark（P3）**〔复核降级：「冷启动 15–30% 改善」无依据（`performance.md:59`），手写 profile 的收益未验证；另外 APK 内已含依赖合并的 `baseline.prof`/`baseline.profm`，缺的只是应用自有 profile，表述需与 `performance.md` P-01 的修正一致〕
- 位置：仓库根（无 `baseline-prof.txt`；`grep -rn profileinstaller` 0 命中）
- 证据：`find . -name 'baseline-prof*' -not -path '*build*'` 空；release APK 里只有 `assets/dexopt/baseline.prof 9,746 B` + `META-INF/androidx.profileinstaller_profileinstaller.version`，是依赖 AAR 自带 profile 的合并结果（有 `baselineProfiles/*.dm` 但无应用自身热点）。
- 影响：本项目是 Compose + 大量单例装配（AppGraph 懒加载链），首帧与首轮对话是主要体感路径，但应用自身代码完全没进 profile；本地安装没有 Play 的云 profile 兜底。
- 建议：补 `app/src/main/baseline-prof.txt` 覆盖 `AppGraph.prepare`/ChatScreen 首帧/`MarkdownParser`，或用 `androidx.benchmark:benchmark-macro-junit4` 生成；这一步不需要新依赖之外的复杂度，且不依赖商店分发。
- 工作量：M

**[GL-009] 3 处 WebViewClient 未实现 onRenderProcessGone，渲染进程 OOM 后白屏/僵尸视图（P2）**
- 位置：`app/src/main/kotlin/.../ui/chat/PresentCard.kt:428`、`plugin/micloud/MiCloudLoginActivity.kt:292`、`plugin/micloud/MiCloudWebWriter.kt:177`
- 证据：lint SARIF `MissingOnRenderProcessGone` 6 条（3 个文件，各 2 个变体）；同仓库 `ui/workshop/WorkshopWebView.kt:271` 已正确实现 → 这是遗漏而非取舍。项目同时声明 `android:hardwareAccelerated` 默认、大量 WebView 页面（工坊/HTML 交付/小米云登录）。
- 影响：Android 上 WebView 渲染进程被杀是常见事件（尤其本项目 WebView 与长会话共存；`docs/archive/STATUS.md:53` 记录页面稳态 PSS 251–283 MB，内存压力真实存在），未实现时返回值未定义、WebView 状态失效，表现为交付卡白屏或后续 `destroy()` 崩溃。
- 建议：把 `WorkshopWebView` 的实现抽成共用 `WebViewClient` 基类或扩展，三个点复用；顺手接入 lint 到 check 防复发。
- 工作量：S

**[GL-010] ABI 打包未收敛：只有 arm64 proot，APK 却携带 4 个 ABI 的依赖库（P3）**〔复核降级：非 arm64 三个 ABI 的 so 压缩后合计仅 22,053 B ≈ APK 的 0.05%，收益≈0；而 `abiFilters arm64` 会让 x86_64 模拟器直接装不上（现在能装，只是 bash 不可用），风险大于收益。〕
- 位置：`shell/src/main/jniLibs/arm64-v8a/{libproot.so,libproot-loader.so}`；`app/build.gradle.kts`（无 `abiFilters`/`splits`）
- 证据：`unzip -l app-release.apk | grep lib/` → `arm64-v8a 4 个、armeabi-v7a/x86/x86_64 各 2 个`（后者全是依赖库，无 proot）；`grep -rn "abiFilters\|splits\|ndk" --include=*.kts .` 0 命中。
- 影响：x86_64 模拟器/32 位设备能装能开，但一切 `bash`/Linux 能力运行期才失败；同时 universal APK 白带 3 个 ABI 的 so 体积。
- 建议：`defaultConfig.ndk.abiFilters += "arm64-v8a"`（或将 proot 的 armeabi-v7a 版本补齐）；若保留 universal，至少在启动时对非 arm64 明确提示"Linux 能力不可用"。
- 工作量：S

**[GL-011] 22 MB 设计源资产与 4.7 MB 垃圾文件未纳入版本管理（P2）**
- 位置：`assets/`（22 MB：`mascot/` 13 MB、`ui-kit/` 4.1 MB、`pi-provider-logos/` 3.8 MB、`vendor-logos/` 680 KB）；`rs.html`（4.7 MB）
- 证据：`git status --short` → `?? assets/`、`?? rs.html`；`git check-ignore -v assets/ rs.html` 无输出（均未被忽略）；`rs.html` 是保存下来的 Android Developers `R.styleable` API 参考页（`<title>R.styleable | API reference</title>`）。
- 影响：干净克隆拿不到图标/吉祥物源文件（而 `res/drawable-nodpi` 的产物已入库，二者会漂移）；`rs.html` 是误提交候选，评审时 4.7 MB 出现在 `git status` 里掩盖真实改动。
- 建议：`assets/` 按用途决定——设计源仓库单独放或进 Git LFS/忽略并在 `docs` 写明再生成路径；`rs.html` 直接删除或放进 `docs/research/` 并进 `.gitignore` 之外的明确位置。
- 工作量：S

**[GL-012] `.git` 从未 gc（5,163 个松散对象、0 个 pack），且无 `.gitattributes`（P3）**〔复核：数字正确，但「3 次字体/rootfs 提交」实为 4 次，tracked ttf 合计 4,458,200 B（非 6.4MB）；普通 `git gc` 即可，`--aggressive`/LFS/剥历史对 12.5MB 单人仓过度〕
- 位置：`.git/`、`.gitattributes`（不存在）
- 证据：`git count-objects -vH` → `count: 5163 / size: 48.79 MiB / in-pack: 0 / packs: 0`；`du -sh .git` 51 MB；99 个提交里含 3 次字体/rootfs 二进制。
- 影响：clone/checkout 与 `git log -p` 明显变慢；二进制 diffs 无处理策略；纯二进制仓库（4 MB rootfs + 6.4 MB 字体）每次改动都会新增整份对象。
- 建议：`git gc --aggressive` 一次；加 `.gitattributes`：`*.ttf binary`、`*.rootfs binary`、`*.so binary`、`*.apk binary`；大二进制后续考虑 LFS 或从历史中剥离。
- 工作量：S

**[GL-013] 测试依赖有死项、覆盖工具缺失、设计模块零测试（P2）**
- 位置：`app/build.gradle.kts:80-84`；`design/build.gradle.kts:30-37`；无 JaCoCo/Kover 配置
- 证据：`compose-test`、`compose-test-manifest` 已声明但 `createComposeRule` 全仓 0 次；`:design` 无 `testImplementation`、`src/test` 不存在；`grep -rn jacoco\|kover` 0 命中。
- 影响：依赖白拿（APK debug 变体和测试 APK 变大都无产出），且 20,087 行测试没有任何覆盖率门槛，无法回答"哪些路径没测"。
- 建议：删掉未用测试依赖或补 1 个 Compose UI 冒烟；为 :app/:runtime 加 Kover 报告（只做 report 不做强制阈值），用数据驱动后续补测。
- 工作量：S

### P3

**[GL-014] 死资源 79 条 + 英文单复数未用 plurals（P3）**
- 位置：`app/src/main/res/values*/strings_*.xml`（`mcp_*` 系列最集中）
- 证据：lint `UnusedResources 79`（示例 `R.string.mcp_new_title`、`mcp_name_hint`、`mcp_error_save_failed`、`drawer_search_placeholder`）；`PluralsCandidate 12`（`values-en/strings_chat.xml:36` "thoughts"、`strings_settings.xml:66` "models" 等）。
- 影响：中英两套共 933 键，约 8% 已死；英文界面会出现 "1 thoughts" 这类语法错误，属 i18n 质量而非功能问题。
- 建议：按 lint 清单删除死键（两边同步删，保持 933 对等）；数量类文案改 `<plurals>`。
- 工作量：S

**[GL-015] 2 处非 lifecycle-aware 的 `collectAsState()`（P3）**
- 位置：`app/src/main/kotlin/.../ui/plugins/PluginsScreen.kt:68`、`ui/plugins/PluginDetailScreen.kt:68`
- 证据：全仓 `collectAsStateWithLifecycle` 31 处、`.collectAsState()` 2 处；ARCHITECTURE「功能接线」第 5 条要求前者。
- 影响：页面退到后台仍继续收集 `PluginManager` 状态流，插件状态探测是 120 s 周期任务，后台多做无意义重组；与文档约定不一致容易被后续代码模仿。
- 建议：两处替换为 `collectAsStateWithLifecycle()`（lifecycle-runtime-compose 已依赖）。
- 工作量：S

**[GL-016] Compose API 约定问题 6 处 Modifier 位置 + 5 处装箱 State（P3）**
- 位置：`ui/settings/SettingsScreen.kt:92`、`ui/chat/ChatTopBar.kt:35`、`ui/workshop/WorkshopWebView.kt:86`、`ui/settings/ProviderDetailScreen.kt:63`、`ui/settings/ChatSettingsScreen.kt:43`、`ui/workshop/WorkshopViewerScreen.kt:62`；`ui/schedules/ScheduleTimePicker.kt:55-56`、`ui/jobs/JobsSheet.kt:65` 等
- 证据：lint `ModifierParameter` / `AutoboxingStateCreation`。
- 影响：可组合项外部无法从统一入口定制样式；`mutableStateOf(Int)` 在高频滚动/滑动场景产生装箱。
- 建议：`modifier` 提为第一个可选参数；数值状态改 `mutableIntStateOf`/`mutableLongStateOf`。
- 工作量：S

**[GL-017] 死配置与重复声明（P3）**
- 位置：`build.gradle.kts:4`（`kotlin.android` alias 从未被任何模块 apply）；`app/build.gradle.kts:29-31` 与 `shell/build.gradle.kts:18-20` 重复声明 `noCompress += "rootfs"`；`runtime/build.gradle.kts:26` 与 `shell/build.gradle.kts:30` 双重 `api(libs.pi.coding.agent.android)`
- 证据：`grep -rn "kotlin.android" --include=*.kts` 只有 root 的 `apply false`；两边 `androidResources.noCompress`；两个库模块都把同一 AAR 以 `api` 暴露。
- 影响：读构建脚本的人会误以为 `org.jetbrains.kotlin.android` 参与编译（实际由 AGP 9 内置 Kotlin 提供）；库模块的 `noCompress` 不参与 app 打包，真正生效的是 app 那份，容易被误改。
- 建议：删除 root 的 `kotlin.android` alias 与目录条目；`noCompress` 只留 app；pi 依赖只在 `:runtime` 用 `api`，`:shell` 改 `implementation` 并在 app 需要处显式声明（或用 constraints 统一版本）。
- 工作量：S

**[GL-018] 构建性能配置偏保守，configuration cache 未启用（P3）**
- 位置：`gradle.properties:1-3`
- 证据：`-Xmx1536m -XX:MaxMetaspaceSize=512m`、`org.gradle.workers.max=2`、`parallel=true`、`caching=true`；本机 `free -h` → 14 GiB 内存、24 核，但已用 9.3 GiB + 3.5 GiB swap；`./gradlew` 每次结束都打印 "Consider enabling configuration cache"，当前 `:app:lintDebug` 配置阶段约 1 分钟。
- 影响：在内存压力的机器上这套保守值是合理的（防止 OOM 拖死整机），但 configuration cache 是纯收益项，没有开启说明没人核算过构建时间。
- 建议：加 `org.gradle.configuration-cache=true`（AGP 9 + Gradle 9 已支持；如遇不兼容先 `problems` 报告定位），`workers.max` 保持或按 free 内存动态；在 README 记一行"为什么是 1.5G/2 workers"。
- 工作量：S

**[GL-019] 无 StrictMode，主线程 IO 无护栏（P3）**
- 位置：`app/src/main/kotlin/.../DeepDeepApplication.kt`
- 证据：`grep -rn StrictMode --include=*.kt` 0 命中；目前主线程文件 IO 未发现（`ui/` 只有 1 处 `context.assets.open(...)` 在 `remember {}` 内读 1 KB 的 bridge.js，可接受）。
- 影响：迁移/索引/迁移逻辑都在 `AppGraph.prepare()` 的 IO 上下文里，一旦后续有人在主线程加一个 `readText()`，没有任何运行时提示；这类问题在真机上直接表现为掉帧或 ANR。
- 建议：debug 变体 `Application.onCreate` 打开 `StrictMode.ThreadPolicy`（detectDiskReads/detectDiskWrites penaltyLog）与 `VmPolicy`，这是零成本的长期护栏。
- 工作量：S

**[GL-020] `!!` 3 处、`lateinit` 1 处（P3）**
- 位置：`ui/chat/ChatUiStrings.kt:118`（`code!!`，由 `showCode` 守卫）、`runtime/PiSessionEngine.kt:65-66`（`restoredProviderId!!`/`restoredModelId!!`，由 `restoredModel` 守卫）、`[lateinit]` 全仓 1 处
- 证据：`grep -rn '!!'` 主源码 3 命中；均在同一 `if` 分支内成对守卫，当前不构成崩溃点。
- 影响：`PiSessionEngine` 那两处依赖"同 flag 同时设置"的不变式，跨文件改动时容易破；属于可读性问题。
- 建议：`:65-66` 改为 `restoredProviderId?.let { ... }` 或 `requireNotNull` 带消息；`ChatUiStrings` 改 `code.orEmpty()`。
- 工作量：S

**[GL-021] `runCatching` 323 处 / `catch (_: Exception)` 18 处（P3）**
- 位置：代表：`app/.../data/SkillRepository.kt:46-70`（好范例）、`runtime/.../QuestionCenter.kt:143-147`、`app/.../ui/mcp/McpOAuthSession.kt:78-85`
- 证据：`runCatching` 323 命中；`catch (_: Exception)` 18、`catch (_: Throwable)` 2；抽查的 suspend 位置都先 `catch (cancelled: CancellationException) { throw cancelled }` 再降级，未发现真正的取消被吞。
- 影响：当前是有纪律的"降级式"写法，但 323 处意味着任何一个新代码点都可能漏掉取消 rethrow；`catch (_: Exception)` 静默时排障缺日志。
- 建议：引入内部 `suspendRunCatching`/`ignoreCancellation { }` 工具（rethrow CancellationException），至少让"忘记 rethrow"从 18 处降到 0；静默分支补 `Log.w` 或计数。
- 工作量：M

**[GL-022] 根 LICENSE 缺失、提交作者身份不一致（P3）**
- 位置：仓库根（无 LICENSE）；`git shortlog -sn HEAD` → `whywwood 92 / whywood 7`
- 证据：`git ls-files | grep -i licen` 只有 `design/src/main/assets/licenses/*` 与 `shell/src/main/assets/licenses/PRoot-GPL-2.0.txt`；设置页有「开源许可」入口（`SettingsScreen.kt:260`），第三方许可已覆盖，但项目自身无许可证声明；92:7 的作者拼写差异会让 blame/统计失真。
- 影响：本地项目无伤，但若将来开源/分享，proot GPL-2.0 与 OFL 的合规基础仅在应用内说明；作者身份分裂影响仓库历史可信度。
- 建议：加根 `LICENSE`（自选）；统一 git author（`git config user.name`），历史可选 `filter-branch` 或保留但此后一致。
- 工作量：S

**[GL-023] versionCode 恒为 1、versionName 0.2.0-dev（P3）**
- 位置：`app/build.gradle.kts:14-15`
- 证据：`versionCode = 1`、`versionName = "0.2.0-dev"`；release/debug 同值，`BuildConfig.VERSION_NAME` 已在 `DeepDeepApp.kt:687` 用于关于页。
- 影响：本地 `adb install -r` 可覆盖，但多设备/深浅版本回退时无法区分；安装包版本与 README/STATUS 记录的轮次（round42 等）脱节。
- 建议：把 versionCode 与交付轮次挂钩（每次 release 递增），或在关于页显示 git describe；低成本。
- 工作量：S

## 反模式统计表

| 模式 | 命中数 | 代表性位置 | 判断 |
|---|---|---|---|
| `GlobalScope` | 0 | — | 优秀，无需处理 |
| 主源码 `runBlocking` | 0 | — | 优秀 |
| 显式 `CoroutineScope(`（非 remember） | 10 | `MicCloudPreferences.kt:133`、`NotificationCenter.kt:45`、`AppGraph.kt:91`、`QuestionCenter.kt:54` | 全部带 `SupervisorJob` + dispatcher，其中 `QuestionCenter` 还限并发 1，合格 |
| `catch (_: Exception)` | 18 | `QuestionCenter.kt:144`、`McpOAuthSession.kt:82`、`MiCloudPlugin.kt:222` | 抽查处均先 rethrow `CancellationException`，可接受；建议工具化防漏（GL-021） |
| `catch (_: Throwable)` | 2 | `data/ImageNormalizer.kt:37`、`runtime/DeepDeepOAuthCallbackServerFactory.kt:91` | 两者都是"降级不崩"的边界（图片解码、回调服务器），合理 |
| `runCatching` | 323 | `data/ShareStaging.kt`、`data/SkillRepository.kt`、`plugin/` 全包 | 数量大但有取消处理纪律；属风格风险而非缺陷 |
| `Thread.sleep`（主源码） | 1 | `shell/PRootExecutor.kt:351`（读 pid 文件，最多 50×10 ms 有界轮询） | 有界且在启动路径，可接受 |
| `Dispatchers.Main` | 9 | `MiCloudWebWriter.kt:120`、`MiCloudPlugin.kt:242` | 全部是 WebView/无障碍 API 的硬性主线程要求，正确 |
| 主线程文件 IO（未切 dispatcher） | 0 发现 | `ui/workshop/WorkshopWebView.kt:106`（`remember{}` 内读 1 KB bridge.js） | 无系统性问题；缺 StrictMode 护栏（GL-019） |
| `collectAsState()`（非 lifecycle-aware） | 2 | `ui/plugins/PluginsScreen.kt:68`、`PluginDetailScreen.kt:68` | 少量偏离项目自身规范（GL-015） |
| 顶层预编译 `Regex` | 19 | `ui/chat/MarkdownParser.kt:64-76` | 全部 `private val` 编译一次，热路径正确 |
| `String.format` | 17 | 分散；未见于 Compose 重组路径 | 无系统性问题 |
| `!!` | 3 | `ChatUiStrings.kt:118`、`PiSessionEngine.kt:65-66` | 均有同分支守卫；属可读性（GL-020） |
| `lateinit var` | 1 | 单点 | 无竞态发现 |
| `println` / 真 `TODO·FIXME·HACK` | 0 / 0 | — | 优秀 |
| `Log.d/v/w` | 22（3 个文件） | 集中在插件/服务 | 无泛滥 |
| WebView `onRenderProcessGone` 覆盖 | 1 / 4 客户端 | `WorkshopWebView.kt:271` 有；`PresentCard.kt:428`、`MiCloudLoginActivity.kt:292`、`MiCloudWebWriter.kt:177` 无 | 真实缺陷（GL-009） |
| Cursor / InputStream 泄漏 | 0 | `ArrangeCalendar.kt:273-289`（`?.use {}`）、`AttachmentStore.kt:54`（`input.use {}`） | 资源释放纪律良好 |
| 未使用资源 | 79 | `res/values*/strings_mcp.xml` 等 | 死债（GL-014） |
| 未使用测试依赖 | 2（`compose-test`、`compose-test-manifest`） | `app/build.gradle.kts:81,84` | 死重量（GL-013） |

补充说明（不计入反模式表，但属"取舍的代价"）：

- **最低 API 29 + 全 ABI universal APK**：本机只有 arm64 proot，却打出 4 ABI（GL-010）。
- **release 关闭 minify**：本地安装是明确取舍；代价是 31.8 MB DEX（GL-007）。
- **`jniLibs.useLegacyPackaging=true`**：proot 需要解压后的可执行文件，正确选择；代价是安装后磁盘占用大于 APK 内体积。

## 文档-代码漂移（抽查表）

抽查 `docs/dev/ARCHITECTURE.md` 11 条可验证声明（另含 1 条 README 交叉核对）：

| # | 文档声明 | 代码核实 | 结论 |
|---|---|---|---|
| 1 | 「字体 5 套（Instrument Sans / Geist / IBM Plex Sans / Newsreader / Lora）随 APK，默认 Newsreader；中文回退系统」（ARCHITECTURE「Provider 图标与字体」） | `DdFontStyle` 只有 `LORA`/`MANROPE`；`design/res/font/` 仅 3 个文件；`DdCompositeFont` 内置 `noto_sans_sc_subset` 作 CJK fallback（非系统回退）；README 描述（Lora 默认 + 内置思源黑体子集）才与代码一致 | **漂移（过时）** |
| 2 | 「插件状态探测与缓存 60 秒 TTL」，写了 6 处〔复核修正：`PLUGINS.md:69,130,225,227` + `ARCHITECTURE.md:83,89`；另漏一条同类漂移——文档称「App 恢复/一轮运行结束按 TTL 刷新」，代码只在 `PluginsScreen`/`PluginDetailScreen` 调 `refreshIfStale`，`AppGraph` 的 RunFinished 只同步技能，`MainActivity.onStart` 也没接刷新〕 | `PluginManager.kt:653 REFRESH_STALE_INTERVAL_MS = 120_000L`；`docs/archive/STATUS.md:53` 也写 120 s | **漂移（过时，且两份文档互相矛盾）** |
| 3 | 「打开技能页和每轮 RunFinished 后，`SkillRepository.syncActiveSkills()` 扫描」 | `AppGraph.kt:370` RunFinished 后调用的是 `syncIfChanged()`（fingerprint 门控，非 force）；`syncActiveSkills()` 只在 `SkillsScreen.kt:110` | 轻微不精确（行为一致，API 名不同） |
| 4 | 功能接线第 5 条「UI 用 `collectAsStateWithLifecycle` 读状态」 | 31 处 lifecycle-aware，2 处仍是 `collectAsState()`（`PluginsScreen.kt:68`、`PluginDetailScreen.kt:68`） | 小漂移（GL-015） |
| 5 | 「每次命令前的安装检查是 O(1)：只看 rootfs 目录与 `.deepdeep-rootfs` 标记（读版本行），不遍历文件」 | `LinuxRootfsInstaller.kt:119-137 quickState()` 只有 `isDirectory`+`isFile`+`marker.readText()`；`Files.walk` 只出现在 `directorySizeBytes`（进页面时算一次） | **一致 ✓** |
| 6 | 「`session-index.json` 以会话文件路径为 key 缓存 size+mtime」 | `SessionIndex.kt:273 INDEX_FILE_NAME = "session-index.json"`，`SessionIndexTest` 存在 | 一致 ✓ |
| 7 | 「事件是快速通道而非唯一真相；`activeRuns: StateFlow` 为准」 | `RuntimeTypes.kt:316` + `AgentRuntimeHost.kt:86` 均有 `activeRuns` StateFlow | 一致 ✓ |
| 8 | 「会话缓存 LRU（8 个）」 | `ChatViewModel.kt:56 MAX_CACHED_SESSIONS = 8`；`conversationCache` 是 accessOrder `LinkedHashMap` | 一致 ✓ |
| 9 | 「自动重试最多 5 次、间隔 2 秒」 | `ChatViewModel.kt:1636-1637 MAX_AUTO_RETRIES = 5, AUTO_RETRY_DELAY_MS = 2_000L` | 一致 ✓ |
| 10 | 「PredictiveBack 由 `AnimatedScreenHost` 用 `PredictiveBackHandler`，manifest 已开 `enableOnBackInvokedCallback`」 | `AppScreenTransition.kt:94` 使用 `PredictiveBackHandler`；`AndroidManifest.xml:36 enableOnBackInvokedCallback="true"` | 一致 ✓ |
| 11 | 「全进程共享一个 OkHttp `HttpTransport`（`AppGraph` 构造）」 | `AppGraph.kt:146` 唯一 `okHttpTransport()`，其余类均通过构造注入 `HttpTransport` | 一致 ✓ |
| 12 | 「ProviderCatalog 从 SDK 读 42 家 Provider」 | `ProviderCatalogTest.kt:39 assertEquals(42, catalog.summaries.size)` | 一致 ✓ |
| 13 | README「pi2kotlin 0.2.2 已发布到 mavenLocal」 | `libs.versions.toml` 中 `pi = "0.2.4"` | 漂移（GL-004 顺带修正） |

未纳入表格的不可验证项：「会话 JSONL 半条损坏尾行备份为 `.jsonl.corrupt`」——`.corrupt` 逻辑在本仓库只覆盖 `questions/jobs/runs`（`QuestionPersistence.kt:63`、`JobPersistence.kt:91`、`RunLedger.kt:92`），JSONL 截断由 SDK 负责，仓库内无对应代码，无法核实。

## 优先修复建议（Top 5）

1. **先补门禁再补测试**：根 `check` 聚合 `test + lint + verifyModuleBoundaries`（GL-001/002/005，S+S+S）。这是所有其它问题的放大器——目前约 160 条 lint warning + 5 hints 和"边界任务从未运行"都说明没有人在看结果。
2. **把构建从"这台机器"里解耦**：`includeBuild("../pi2kotlin")` 或私有 maven 仓 + 修 README 版本（GL-004，M）。不做这一步，CI 和任何自动化都无从谈起。
3. **给三点 WebView 补 `onRenderProcessGone`（S）+ 收敛 ABI（S）**：真实运行期故障，实现已有一份可复用（GL-009/010）。
4. **release 身份固定**：专用 `release-local.keystore` 替代 debug 签名（GL-006，S）——成本极低，避免一次"换机/清 SDK 就必须卸载丢数据"。

> 〔复核勿做，按原文顺序执行会丢数据〕现在已安装的 release APK 用的是 debug 证书（`CN=Android Debug`），换新 keystore 后**无法覆盖安装**，而 `android:allowBackup=false`，卸载即丢 `files/` 下的会话、schedule/goal/job、DataStore 凭据。更便宜且零迁移的做法：把现有的 `~/.android/debug.keystore` 备份到项目外受控位置，并让 `signingConfig` 指向这份**同证书**副本；等到某次愿意卸载重装时再切换新 keystore。本条也与 `performance.md:125`「签名继续用 debug keystore（覆盖安装）」互斥。
5. **一次 R8 + baseline profile 实验**：若 DEX 从 31.8 MB 降到 15 MB 级别且 pi SDK 无反射炸点，就把 release 构建升级为"可审计产物"；否则把"为什么不开"写进 ARCHITECTURE（GL-007/008，M）。
