# DeepDeep 开发指南

面向要修改 App、运行时或构建流程的开发者。插件作者先读 [插件开发](PLUGIN_DEV.md)。

## 环境

- JDK 17；构建固定使用 Java 17（`jvmToolchain` 17）。
- Android SDK Platform 36、build-tools 36.0.0（约 5.6 GB）。
- 内存 ≥ 8 GB、空闲磁盘 ≥ 10 GB；冷缓存首次构建约 15–30 分钟（含 JitPack 现场编译 SDK 与依赖下载）。
- 仓库自带 Gradle wrapper；日常构建走 `scripts/build.sh`，底层统一 `scripts/gradlew-locked`，不要直接跑 `./gradlew`，避免本机并发构建 OOM。内存参数只在 `gradle.properties` 维护，`gradlew-locked` 会忽略命令行覆盖，防止 daemon 增殖。
- Android SDK 默认位置：macOS `~/Library/Android/sdk`，Linux 常见 `~/Android/Sdk`。可用 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 指定。
- Windows 不原生支持：脚本依赖 bash 与 flock，请在 WSL2、Linux 或 macOS 上构建。

首次环境：

```bash
./scripts/dev-setup.sh
```

它会检查 `java` 与 `javac`（JDK 17，必须有编译器），按环境变量 → 已有 `local.properties` → 常见路径定位 Android SDK，必要时用 `sdkmanager` 安装 Platform 36 与 build-tools 36.0.0，保留已有 `local.properties`，然后构建 debug APK；不碰签名材料。

Agent SDK 依赖经 JitPack 获取：`com.github.Sadami3066.DeepDeep-KotlinSDK:pi-coding-agent-android`，版本在 `gradle/libs.versions.toml` 的 `pi`。首次构建可能等待 JitPack 现场编译 SDK（几分钟，也可能超时）；依赖缓存后可以离线构建。断网环境请先在联网环境完整构建一次。

## 构建与验证

```bash
./scripts/build.sh quick        # 日常编译
./scripts/build.sh test         # 定向单测
./scripts/build.sh check        # 完整验证（等价 check.sh --full）
./scripts/build.sh release      # 完整验证后构建 release APK
./scripts/build.sh stop         # 回收 Gradle/Kotlin daemon 内存
./scripts/check.sh              # 编译 + 单测 + lint + 模块边界 + APK
./scripts/check.sh --full       # 交付前：再加插件 SDK ABI 基线
./scripts/check.sh --device     # 再加覆盖安装到手机并启动
./scripts/plugin-e2e.sh         # 插件端到端（设备或模拟器，无需 root）
./scripts/plugin-bundles-e2e.sh # 四个官方插件包回归（需先构建插件仓库）
./scripts/plugin-api-abi.sh     # 单独校验插件 SDK ABI 基线（--update 更新）
```

Gradle 与 Kotlin daemon 的内存固定在 `gradle.properties`（Gradle 3g / Kotlin 2g / workers 2）；`gradlew-locked` 用 flock 串行化构建并丢弃 `-Dorg.gradle.jvmargs`、`-Pkotlin.daemon.jvmargs` 覆盖。长时间不构建时用 `build.sh stop` 回收内存。

只改文档不需要构建。

## 模块

| 模块 | 职责 |
|---|---|
| `app/` | Compose UI、聊天、文件、设置、插件页、小程序容器与各页面装配 |
| `runtime/` | Agent 运行时：会话、工具、记忆、任务、定时器、插件加载 |
| `runtime-api/` | 纯 JVM 契约，供 runtime 与宿主解耦 |
| `shell/` | proot 与 ADB/Root 系统访问后端 |
| `design/` | 主题、图标、通用组件与设计 token |
| `plugin-api/` | 插件契约与可发布 SDK |

## 运行时

- 会话以 pi JSONL 为唯一权威，保存在 `files/agent/sessions/`；置顶、归档、重命名只是本地元数据。
- 界面状态是会话事件流的投影；不要另存一份「当前消息」状态。
- 工具与文件都由当前系统访问环境（`HostEnvironmentProvider`）提供：普通、无线调试档的 `bash` 在 App 沙箱内，Root 档进原生 chroot；不再注册 `adb_shell` / `root_shell`，ADB/Root 档的系统命令统一由常驻的 `system_shell` 执行（执行时解析当前档位，没有系统 Shell 的档位会报错提示）。后台任务与插件 Linux 流优先走 `HostEnvironment.bashStreaming(cwd)`：普通/无线调试档复用 proot 流式输出，Root 档由 chroot 执行器的 su 输出回调逐行推送，环境不支持时回退为结束后一次性输出。
- 系统访问档位（普通 / 无线调试 / Root）由 `HostAccessController` 维护，只降级不自动升级；运行时只读 `HostEnvironment`，不自行判断 root。
- 会话不缓存环境快照：`PiSessionEngine` 用 `DynamicShellExecutor` / `DynamicFileSystem` 在每次执行时解析当前环境，会话创建后切换档位，工具与提示词会立即跟随。
- 目标与后台任务只在进程内存，重启清空。
- 普通、无线调试档的 Linux 是 proot 用户空间，不是安全沙箱；Root 档直接使用系统 root 环境。
- 定时任务绑定会话，五段 cron；投递失败或进程被杀时不保证执行。

## 插件

- 契约在 `plugin-api/`，发布到公开 Maven 仓库。
- 插件是签名的 `.ddp`，由宿主在 App 进程内加载；没有沙箱。
- 新增或修改契约时同步更新 ABI 基线，并跑 `./scripts/check.sh --full`。
- 完整的清单、签名、生命周期、工具、技能、hooks、UI 扩展与排错见 [PLUGIN_DEV.md](PLUGIN_DEV.md)。

## 设计系统

- 统一使用 `design/` 里的组件、颜色、字号与间距；不要在页面里另造一套。
- 用户可见文案放在 `app/src/main/res/values*/strings_*.xml`，默认中文，`values-en` 同步；代码里不拼文案。
- 新增图标先查 `design/icons/` 与素材说明，避免重复绘制。
- 改 UI 后至少在真机看一遍：边距、对齐、层级、留白和主次关系。

## 测试

- 新行为加测试；不删测试、不改断言凑绿。
- 单元测试分模块运行；`check.sh` 会跑完整集合。
- 插件相关改动至少跑一次 `plugin-e2e.sh`；涉及官方插件包时再跑 `plugin-bundles-e2e.sh`。
- 可能删除真实数据、不可逆迁移、换签名或改变关键产品取舍时，先和用户确认。

**真机与模拟器**

- x86_64 AVD 可以完成单测、编译、安装启动、插件 e2e 和绝大多数 UI 检查。
- 以下必须用 arm64 真机：Linux 环境（内置 rootfs 只有 aarch64）、屏幕操作/虚拟屏与 Root 档系统访问。
- 插件 e2e 不需要 root：脚本用 `adb install` 装 debug 包，再把 `.ddp` 夹具推进 App 外部私有目录（`/sdcard/Android/data/<pkg>/files/`），普通设备与模拟器都能跑；需要 root 的只是 App 的 Root 档系统访问功能。
- 模拟器与完整构建不要同时跑：Gradle/Kotlin daemon 加模拟器容易吃满内存导致系统 OOM；跑 `check.sh --full` 前先关模拟器（`adb -s <serial> emu kill`）。

**设备脚本**

- 需要 `adb`。多设备在线时用 `ANDROID_SERIAL` 指定；脚本优先用它，其次是唯一在线的设备，多台在线且未指定会报错。
- 插件 e2e 需要插件仓库：默认在仓库同级或上一级目录找 `plugins` / `DeepDeep-Official-Plugins`（`plugin-e2e.sh` 对应 DeepDeep-Plugins，`plugin-bundles-e2e.sh` 对应官方插件仓库），找不到或换位置时用 `PLUGINS_DIR` 指定。`plugin-e2e.sh` 还需要插件仓库里的 `sample` 测试夹具；缺夹具时脚本会提示用 `E2E_DDP` 指定已签名的包。
- `check.sh --device` 与两个 e2e 都覆盖安装 debug 包：设备上已有同包名但签名不同的构建（例如 release APK）时会安装失败，debug 与 release 签名不能互换，需先卸载（会清 App 数据）或换设备/模拟器；e2e 失败会以非 0 退出码结束。
- `plugin-api-abi.sh` 需要 `unzip`、`jar`、`javap`；后两者由 JDK 提供，脚本会自检并提示缺什么。

## 发布

签名材料与换签流程见 [RELEASE.md](RELEASE.md)。

要点：

- `app/release.keystore` 与 `keystore.properties` 不进 git。
- 换签名后 Android 不允许覆盖安装，必须先卸载；`allowBackup=false`，卸载会丢本机会话与凭据。
- 正式分发前确认 `applicationId` 一致，并保留旧会话导出。

## 代码约定

- 一次改动只做一件事，一个提交；标题用 `type: 摘要`，能一行 `git revert` 撤回。
- 重构时直接删除旧实现，不保留兼容层、废弃入口或死代码。
- 代码文件不写注释；必要信息放在提交、文档或测试名里。
- 不擅自改模块依赖图、`settings.gradle.kts`、签名配置和 `.gitignore`。
- 新增第三方依赖前先判断是否必要；引入时记录用途、许可与体积。
- 目录职责变化时同步更新对应的 `AGENTS.md`。

## 文档

- [用户指南](USER_GUIDE.md)
- [插件开发](PLUGIN_DEV.md)
- [发布](RELEASE.md)
- [变更记录](../CHANGELOG.md)
- 内部设计记录（`docs/DECISIONS.md`、`docs/GUIDE.md`）不随仓库分发，托管在私有仓库 `Sadami3066/DeepDeep-Private`；新机器用其中的 `bootstrap.sh` 落位到本地 `docs/`。
