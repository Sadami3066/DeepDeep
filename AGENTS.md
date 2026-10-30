# AGENTS.md — DeepDeep 工作规则

本文件是唯一常驻规则，只写「每个任务都要知道」的。标 **〔必须〕** 违反即退回；标 **〔应该〕** 可权衡但要说明；其余按需查（§7）。**不要预读 docs/ 全树**——需要哪篇再点开。

## 1. 先判断任务类型，再决定流程强度

| 改什么 | 档位 | 任务卡 | 交付证据 |
|---|---|---|---|
| 文档 / 文案 | L0 | 免 | 无 |
| **UI 布局、样式、动效、交互微调** | **L1–L2** | **免** | **编译过 + 一张真机截图**（上不了真机就写「未上真机」） |
| 纯重构 / 补测试（行为不变） | L2 | 一卡 | 单测输出 |
| 新功能 / 修 bug / 改行为 | L3 | 一卡 | gate 输出 + 测试红绿 |
| 数据、权限、启动顺序、跨模块契约、构建 | L3 / L4 + ADR | 一卡 + 数据影响说明 | 全部 + 迁移 / 真机证据 |

拿不准就升一档，但**不要因为「有规则」就给小事上全套流程**。UI 任务不要求单测——UI 没有测试网，硬凑出来的测试等于没测。

## 2. 命令只有两条路

```bash
./scripts/gate.sh 0|1|2|3|4     # 本机验证，按 §1 选档
./scripts/gradlew-locked ...    # 任何直接 Gradle；禁止 ./gradlew（本机会 OOM）
```

- 档位：L0 断链 / L1 编译 / L2 单测 / L3 全量（默认）/ L4 真机安装 / CI 手动（`gate.sh ci fast|full`）。
- 本机实测：L0 ~1s、L1 ~5s、L3 ~75s、L4 在 L3 上多 ~10s。
- 首次环境：`./scripts/dev-setup.sh`（JDK 17+、SDK 36、写 `local.properties`、出 APK）。
- `dev.pi` SDK 由 `syncPiSdk` 从 pi2kotlin `v0.2.4` Release 下载并校验 sha256；离线用 `-PpiSdkLocalZip=<zip>`。

## 3. 基线（L2 起才核对，不得下降）

| 指标 | 值 |
|---|---|
| 单测 | 1223（app 784 / runtime 392 / shell 47），0 失败、0 skip |
| Lint | 0 error；156 warning |
| 编译警告 | 13 条 Kotlin `w:` |

以 `docs/dev/refactor/BASELINE.md` 的复算命令为准。

## 4. 〔必须〕7 条

1. 模块依赖与包边界不动：`:app → {:runtime,:shell,:design}`、`:runtime → :runtime-api`；新增依赖边先写 ADR。
2. 不擅自改 `settings.gradle.kts`、根 `build.gradle.kts`、`gradle/libs.versions.toml`、签名配置、`.gitignore`——要改先说。
3. 不碰 `artifacts/`、`*.ttf`、`*.rootfs`、`*.so`、任何 >4MB 文件、`docs/dev/review/` 原始数据。
4. 代码文件不留注释；用户可见文案走 `res/values*/strings_*.xml`。
5. 不新增第三方依赖。
6. 提交标题带 ID：`type: 摘要 [ID]`（无卡用 `[L0]`/`[L1]`）；一个任务一个提交，`git revert <sha>` 能一行回滚。
7. 外部副作用先问：发 Release、切 public、改 GitHub 设置、动签名。

## 5. 〔应该〕（可权衡，做了在 PR 里写一句）

- 单文件超过 400 行先说明；热点文件不加**新职责**：`ChatViewModel.kt`(799)、`DeepDeepApp.kt`(644)、`AgentRuntimeHost.kt`(1122)、`PluginSetupSession.kt`(319)。
- 不夹带无关改动；必要的顺手清理单独列出，不混进逻辑改动。
- 新增 / 修改**行为**要补测试；不写只验证「没崩」的空测试。
- 测试数、lint、编译警告不下降；确需下降先说明理由。

## 6. 改 UI 的轻量模式（专门针对「改个界面」）

只读四处：目标 `Screen.kt`、它引用的 `design/` 组件（`Dd*.kt`、`DeepDeepTheme.kt`）、本页 `strings_*.xml`、`docs/dev/UI.md` 的对应小节（顶部有目录）。**不用读** `ACCEPTANCE.md`、`TEXTS.md`、架构与 ADR。

- 免卡、免 ACC 条目、免 G2；可在该 Screen 及其状态类内自由拆分 composable、抽组件、改布局与动效——**这不算越界**。
- 只是接线时需要动 `DeepDeepApp.kt`（≤30 行）可直接改，提交里写明。
- 验收：`gate.sh 2` 编译过 + `gate.sh 4` 截一张真机图（落 `docs/dev/evidence/<date>-ui/`）。
- 不适用本模式：数据读写、持久化、导航结构、跨模块契约——按 §1 最后一行走。

## 7. 按需查（卡住或要越界时才点开）

| 你遇到 | 读 |
|---|---|
| 还有哪些任务、里程碑到哪 | `docs/dev/refactor/README.md`、`tasks.tsv` |
| 要写 / 改卡、完整验收模板 | `docs/dev/refactor/PLAYBOOK.md` |
| 新代码放哪、数据谁管 | `docs/dev/ARCHITECTURE.md` |
| 为什么这么设计、能不能推翻 | `docs/dev/adr/` |
| 用户功能边界 | `docs/user/GUIDE.md` |
| 验收条目（只有 L4 用） | `docs/dev/ACCEPTANCE.md` |
| 文档地图 | `docs/README.md` |
| 照做会变糟的 15 条 | `docs/dev/refactor/PLAYBOOK.md §6` |

## 8. 卡住只问三类，结束只看三条

值得停：① 外部副作用；② 产品取舍；③ 真阻塞（查过仍无证据）。其余自己决定，在 PR 写「假设：…」。一次最多 3 个问题，**不许把「待确认」当交付**。

做到这些就算完，不要加戏：目标行为实现且可观察；`gate.sh` 对应档通过；工作区干净、提交在分支、能一行 revert；交付 = 变更清单 + gate 输出 + 回滚命令（L4 再加截图 / 证据）。
