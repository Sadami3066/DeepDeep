# 开发手册

面向人类协作者。AI agent 先读 `AGENTS.md`（规则优先），再回来看这里。

## 环境

- JDK 17+（Gradle 9.8.2、AGP 9.4.1、Kotlin 2.4.21；字节码目标 17）
- Android SDK Platform 36 + build-tools 36.0.0
- 本机可用内存较少时**不要并发跑 Gradle**：`verify.sh` 用 `flock` 串行，构建约 1–5 分钟

## 首次搭建

```bash
./scripts/dev-setup.sh
```

它做四件事：校验 JDK/SDK、必要时用 `sdkmanager` 装 Platform 36、写 `local.properties`、拉 SDK 并产出 debug APK。

SDK 依赖（`dev.pi:*`）由根项目的 `syncPiSdk` 任务从 pi2kotlin `v0.2.4` GitHub Release 下载 `pi2kotlin-0.2.4-m2.zip`、校验 `gradle/pi-sdk.sha256`、解包到 `build/pi-sdk-m2`，再作为 Maven 仓库使用。因此**新机器只需要本仓库**，不再需要 `../pi2kotlin`。

离线或本地调试 SDK：

```bash
./scripts/gradlew-locked :app:assembleDebug -PpiSdkLocalZip=/path/to/pi2kotlin-0.2.4-m2.zip
```

## 日常命令

```bash
./scripts/gate.sh 3                      # 默认档：文档链接 + check（单测+lint+模块边界）+ assembleDebug
./scripts/gate.sh 0|1|2|4                  # L0 文档 / L1 编译 / L2 单测 / L4 真机（见 PLAYBOOK §0）
./scripts/verify.sh                        # 等价于 gate.sh 3（保留兼容）
./scripts/gradlew-locked :app:assembleDebug              # 只出 APK
./scripts/gradlew-locked :app:testDebugUnitTest          # 只跑 app 单测
./scripts/gradlew-locked :runtime:testDebugUnitTest      # 只跑 runtime 单测
./scripts/gradlew-locked :app:lintDebug                  # 只跑 lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`verify.sh` 的基线（不得下降）：1165 单测 / 0 失败 / 0 skip、0 lint error、156 warning、13 编译警告。复算见 `docs/dev/refactor/BASELINE.md`。

## 模块

```
:app         Compose 界面、导航、前台服务、装配（依赖下面三个）
:runtime     pi2kotlin 适配、事件、会话元数据、凭据、MCP、调度/目标/任务/工坊
:runtime-api 模块间共享契约与类型（runtime 依赖它；app 不直接依赖）
:shell       Alpine/proot 与 ShellExecutor
:design      主题、字体、图标、motion tokens、共享组件
```

依赖方向由根项目 `verifyModuleBoundaries` 任务强制：`:app → {:runtime, :shell, :design}`、`:runtime → :runtime-api`，其余模块互不依赖。`./scripts/gradlew-locked check` 会执行 `verifyModuleBoundaries` 与 `verifyPackageBoundaries`。

## 代码约定

- 代码文件里不留注释（作者要求）；能靠命名和结构表达的就不要写解释
- 用户可见文案一律走 `res/values*/strings_*.xml`（中英两套；插件目录里的模型错误文案可先留在 Kotlin，但新增 UI 文案必须走资源）
- 协程：不要 `GlobalScope`；显式 `CoroutineScope` 必须带 `SupervisorJob` 与明确 dispatcher；`catch (CancellationException)` 必须重抛
- 不新增第三方依赖（需要时先写 ADR 或找 Lead 批准）
- **不写通配 import**（`import a.b.*`）：AGP 的 lint 在通配 import 上有已知崩溃（M3-2 实测 `AsyncExecutionService.getService must not return null`），一律显式 import
- 不重排、不格式化无关文件；不做「顺手优化」

## 测试

- 单测：`app/src/test`、`runtime/src/test`、`shell/src/test`，JUnit4 + `StandardTestDispatcher` + 手写 fake；时间相关用 `MutableTestClock`
- 共享 fake：每个模块一份（`runtime/src/test/.../testing/FakeAgentRuntime.kt`、`app/src/test/.../ui/chat/testing/FakeAgentRuntime.kt`）；新增跨测试 fake 一律进对应 `testing/` 包，禁止在生产代码加测试钩子
- 设备测试：`app/src/androidTest` 目前只有日历与图片规范化两个
- 行为验收靠真机：条目在 `docs/dev/ACCEPTANCE.md`（稳定 ID），截图用 `scripts/collect-evidence.sh <ACC-ID>` 落 `docs/dev/evidence/`

## 提交与 PR

见 `CONTRIBUTING.md`。要点：一个任务一个 squash 提交、标题带任务 ID、body 写清验收/回滚/不在范围。

## 回滚

```bash
git revert <sha>          # 一个任务一个提交，所以一行能回滚
adb install -r <上一 tag 的 APK>
```

`allowBackup=false`：**不要用「卸载重装」当修复手段**，会丢 `files/` 下的会话、提醒、凭据。

## 常见问题

**构建卡住/超时**：八成是另一个 Gradle 在跑。`verify.sh` 会等待锁；绕过脚本直接 `./gradlew` 不会，别绕过。

**`Could not find dev.pi:...`**：`syncPiSdk` 没跑或下载失败。手动 `./scripts/gradlew-locked syncPiSdk` 看日志；校验失败会报 checksum mismatch（说明归档被篡改或版本对不上）。

**lint 突然多出 error**：先跑 `./scripts/gradlew-locked :app:lintDebug`，报告在 `app/build/reports/lint-results-debug.html`；不要加 baseline 掩盖。

**文档里的数字不对**：以命令输出为准，先改文档再改代码（见 `AGENTS.md` 顶部）。
