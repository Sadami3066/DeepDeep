# DeepDeep 重构总纲（M0–M4）

状态：M0–M3 已完成并合并 `master@706caa3`（PR #1–#4，tag `m0`–`m3`）；M4 不执行；M5 文档基线已完成（2026-10-30，见 §4）。
分支：`refactor/team-foundation` 已合并，本地与远端均保留（本地 `763c117`，落后 master 5 个 merge commit；实现提交均已进入 master）。
配套文件：活跃任务卡 `tasks.tsv`（已完成卡归档在 `tasks-archive-2026-10.tsv`）｜agent 执行协议 `PLAYBOOK.md`（T0/T1/T2 分级、模板与闸门）

## 1. 目标

不重写、不推倒。按顺序把仓库改造成四件事同时成立：

1. **任何机器 clone 即可构建**（不依赖作者本机 `~/.m2`）
2. **一条命令判真伪**（编译 + 单测 + lint + 模块边界 + 负向自测）
3. **多人/多 agent 可并行**（文件级不互踩，每步可独立验证）
4. **Apache-2.0 开源标准就绪**（许可、声明、文档、发布工程；暂不公开）

## 2. 成功判据

| # | 判据 | 验证方式 |
|---|---|---|
| 1 | `/tmp` 新目录 clone + 空 `~/.m2` + 无 `local.properties` 可构建 | `./scripts/verify.sh` 全绿 |
| 2 | 门禁真的有牙 | `./scripts/gradlew-locked check` 任务图含 `verifyModuleBoundaries`；故意加违规依赖必红 |
| 3 | 测试只增不减 | 单测数 ≥ `BASELINE.md` 记录值、0 skip；`lintDebug` 0 error；编译警告不增加 |
| 4 | agent 可独立工作 | 新 agent 只读 `AGENTS.md` 完成一个 S 级任务并交付完整证据包 |
| 5 | 可并行 | 两个 agent 并行改不相交 zone 零冲突（Wave 1 实测） |
| 6 | 合规就绪 | `LICENSE`/`NOTICE`/`THIRD_PARTY_NOTICES.md` + 5 类缺失文本 + 应用内许可页 |
| 7 | 可回滚 | 每任务单 squash 提交可单独 revert；每里程碑合并 master 并打内部 tag |

## 3. 为什么是这个顺序

- **先构建再拆代码**：拆完无法判断对错的重构是赌博。构建不可复现 → 第二个 agent 连编译都做不到；没有门禁 → 拆完只能靠肉眼。
- **先缝再墙**：模块化是「依赖方向的物理化」，而当前 `ui` 页面直连 Store、壳层 import 27 个具体成员，没有稳定依赖方向可固化。先有验证缝（fake/接口/测试），再谈模块。
- **先有测试网的文件先拆**：`PluginManager`/`MiCloudTools`/`MarkdownParser` 都有厚测试，是安全的练手对象；`DeepDeepApp`/`AgentRuntimeHost` 零直接测试，必须先补缝。
- **先 ChatViewModel 后其他 God class**：它测试网最厚（56 例）、碰撞最贵（32 次提交）、拆出的状态机可复用。

## 4. 阶段

### M0 可复现 + 机器判据（阻塞项，串行）

| 任务 | 交付 |
|---|---|
| M0-1 | pi2kotlin 从 HEAD `6cff93e` 重建并发 `v0.2.4` Release（`pi2kotlin-0.2.4-m2.zip`） |
| M0-2 | DeepDeep `syncPiSdk`：下载 → sha256 → 解到 `build/pi-sdk-m2` → 接 `preBuild` |
| M0-3 | 根门禁接线：`verifyModuleBoundaries` 进子项目 `check`；`scripts/verify.sh` |
| M0-4 | wrapper `distributionSha256Sum` + `.gitignore` 补项 |
| M0-5 | `scripts/dev-setup.sh`（JDK/SDK 校验、写 `local.properties`、出 APK） |
| M0-6 | 基线快照 `BASELINE.md`（测试数/lint/警告，命令生成） |
| M0-7 | 构建串行锁（`flock`；本机可用内存约 3G，防并发 OOM） |
| M0-8 | 干净 clone 演练验收 |

### M1 协作机制 + 合规基线

| 任务 | 交付 |
|---|---|
| M1-1 | `docs/dev/refactor/*` 三份文档入库 |
| M1-2 | `AGENTS.md`（命令、基线、硬边界、热点文件、交付物） |
| M1-3 | PR 模板 + 提交规范 + squash/回滚约定 |
| M1-4 | `findings.tsv` 入库 + `status` 列（可机器对账「128 条修了哪些」） |
| M1-5 | 真机验收稳定 ID `docs/dev/ACCEPTANCE.md` + 证据压缩进 `docs/dev/evidence/` |
| M1-6 | git 卫生：`.gitattributes`、`gc`、`.mailmap`、`assets/`+`rs.html` 处置 |
| M1-7 | CI（PR 触发 verify）+ master 分支保护 |
| M1-8 | 文档分层：README 重写、`DEVELOPMENT.md`、`ARCHITECTURE.md` 收敛、`STATUS.md`/`PLAN.md` 移出、ADR×7 |
| M1-9 | 合规基线：`LICENSE`/`NOTICE`/`THIRD_PARTY_NOTICES.md` + 5 类缺失文本 + proot 义务 + 应用内许可页 |
| M1-10 | 首个 agent 试跑（G1/G2/G3 + 回滚演练） |

### M2 验证缝 → 契约 → 机械拆分

| 任务 | 交付 |
|---|---|
| M2-1 | 共享 fake（3 份 `FakeAgentRuntime` 合一） |
| M2-2 | CH-011 三条 characterization |
| M2-3 | `AppDependencies` 接口（27 成员） |
| M2-4 | `SessionHandle`/`SessionEngine` 接口 + host 6 场景测试（RT-006） |
| M2-5 | `AgentRuntime` 角色切分（5 个窄接口） |
| M2-6 | package boundary 规则 |
| M2-7 | `PluginManager` → 3 类 |
| M2-8 | `MiCloudTools` → 4 文件 |
| M2-9 | `MarkdownParser` → 4 文件 |
| M2-10 | `:runtime-api` 纯 JVM 契约模块 |

### M3 结构拆分

| 任务 | 交付 |
|---|---|
| M3-1 | 路由表抽纯函数 + 单测 |
| M3-2 | `OverlayHost`（4 Sheet + 3 Dialog） |
| M3-3 | 命令/分享/深链 intake，`DeepDeepApp` ≤500 行 |
| M3-4…M3-10 | ChatViewModel 7 步串行：1660 → 799 |
| M3-11…M3-16 | 每屏 state holder（Skills/MCP/Plugins/Schedule/Workshop） |
| M3-17 | 收口验收（判据 3、5） |

### M6 工程效率（按需，不排期）

流程减负与 CI 提速：`scripts/gate.sh` 五档（L0 文档 / L1 编译 / L2 单测 / L3 全量 / L4 真机）+ CI 改手动触发（`gate.sh ci`）。后续按需追加，不设完成判据。

### M4 开源就绪（**只准备，不发布**；用户点头才执行）

Maven Central 收口（group `io.github.sadami3066`、v0.3）｜正式签名 + tag 驱动 release workflow｜仓库切 public + CONTRIBUTING/CoC/SECURITY + issue 模板｜品牌素材替换决策｜发行验收清单。

### M5 文档基线（2026-10-30，完成）

文档三层重组：`docs/user/`（产品）、`docs/dev/`（开发）、`docs/archive/`（不再维护），`docs/README.md` 为总索引；逐篇按代码校准事实与路径。共 9 张卡（`tasks.tsv` M5-1…M5-9）。九张卡并行改同一份文档集，按文件无法拆成独立回滚单元，因此落在**一条提交**里，回滚用一次 `git revert`。M0–M3、M5 的完成卡已归档到 `tasks-archive-2026-10.tsv`，`tasks.tsv` 只留活跃卡。

## 5. 已定决策

| 项 | 结论 |
|---|---|
| 许可证 | Apache-2.0（仅覆盖自有代码；第三方按各自许可） |
| SDK 分发 | 两步走：现在 GitHub Release m2.zip；v0.3 改 group `io.github.sadami3066` 上 Maven Central |
| 仓库归属 | `Sadami3066`（私有仓，ADMIN） |
| 开源时点 | 暂不 public、不发 APK；M4 不执行 |
| 不做的 | 不拆 `:feature:*`/`:core:*`；不引 Hilt/Koin/detekt/Kover 门槛/Git Flow/LFS；不重写；不做无实测的性能优化 |

## 6. 回滚

| 级别 | 手段 |
|---|---|
| L0 单任务 | `git revert <squash-sha>`（每任务一个提交） |
| L1 里程碑 | `git switch master && git reset --hard <内部tag>`（`m0`/`m1`/…） |
| L2 应用 | 安装上一 tag 的 APK（不卸载，`allowBackup=false` 时卸载即丢数据） |
| L3 数据 | 依赖 `.corrupt` 备份与升级验证条目；**绝不**用「清空数据重装」当修复 |

## 7. 进度

| 里程碑 | 状态 |
|---|---|
| M0 | 完成（2026-10-30，判据 1、2 通过） |
| M1 | 完成（2026-10-30，判据 3、4、6 通过；CI 首跑 7m44s 绿） |
| M2 | 完成（2026-10-30，10/10；host 首次可单测、契约模块独立） |
| M3 | 完成（2026-10-30，17/17；ChatViewModel 1660→799、ChatScreen 参数 55→10、5 屏 state holder） |
| M4 | 未开始（不执行） |
| M5 | 完成（2026-10-30，9/9；文档三层重组 + 逐篇校准，单提交可一次 revert） |
| M6 | 进行中（按需；M6-1 分级流程与 CI 文档快车道） |
