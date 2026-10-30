# 重构报告（M0–M3 完成，M4 待决策）

日期：2026-10-30 ｜ 里程碑 tag：`m0` `m1` `m2` `m3`（已推远端） ｜ master：`706caa3`（PR #1–#4 已合并） ｜ 分支 `refactor/team-foundation`：已合并、本地/远端保留（本地 763c117，落后 master 5 个 merge commit）

## 1. 目标与结果

| 目标 | 结果 | 证据 |
|---|---|---|
| 任何机器 clone 即可构建 | ✅ 不再依赖作者本机 `~/.m2`；SDK 由 `syncPiSdk` 从 pi2kotlin `v0.2.4` Release 下载并校验 sha256 | GitHub Actions 在干净 runner 上 `checkout + verify` 绿；`M0` 时本机空 `~/.m2` 的 clean-clone 演练也绿 |
| 一条命令判真伪 | ✅ `./scripts/verify.sh` = 编译 + 单测 + lint + 模块边界 + 包边界 + assembleDebug | `BUILD SUCCESSFUL`，1165 单测 0 失败 0 skip，lint 0 error / 156 warning，编译警告 13 |
| 多 agent 可并行协作 | ✅ 任务卡/简报/闸门/回滚协议 + ownership zones + 全局构建锁 | `docs/dev/refactor/PLAYBOOK.md`、`tasks.tsv` + `tasks-archive-2026-10.tsv`（50+ 卡）、`scripts/gradlew-locked`；M2/M3 多轮 3–4 agent 并行完成 |
| Apache-2.0 开源标准就绪（暂不公开） | ✅ LICENSE/NOTICE/第三方声明 + 社区文件 + 就绪清单 + 发布流程（未启用） | `LICENSE`、`NOTICE`、`THIRD_PARTY_NOTICES.md`、`SECURITY.md`、`CODE_OF_CONDUCT.md`、`.github/ISSUE_TEMPLATE/*`、`docs/dev/OPEN_SOURCE_CHECKLIST.md`、`docs/dev/RELEASING.md` |

## 2. 里程碑

| 阶段 | 内容 | 验证 |
|---|---|---|
| M0 | pi2kotlin v0.2.4 Release；`syncPiSdk`；根 `check` 接入边界校验；`verify.sh`；dev-setup；基线；构建串行锁 | clean clone + 空 `~/.m2` 全绿；负向测试（违规模块依赖必红） |
| M1 | AGENTS.md、PR 模板、CONTRIBUTING；findings.tsv 入库（M1 结束时 124 open / 4 fixed；M1-10 修 RT-004 后现为 123 open / 5 fixed）；191 条验收改稳定 ID + 证据脚本；CI + master 分支保护；文档分层 + ADR×7；LICENSE/NOTICE/第三方声明；首个 agent 试跑（RT-004） | 关掉 `verifyModuleBoundaries` 前它从未执行 → 现在 `check` 必跑；CI 首跑 7m44s 绿 |
| M2 | 共享 fake；AppDependencies；SessionHandle/SessionEngine + host 6 场景；AgentRuntime 5 角色；包边界校验；PluginManager→3 类；MiCloudTools→门面 82 行 + 6 个工具文件（提交 +988/−926）；MarkdownParser→4 文件；`:runtime-api` 纯 JVM 契约模块 | 1165 单测；`runtime-api` classpath 无 Android/SDK（负向 import 必失败） |
| M3 | 5 屏 state holder（Skills/MCP/Plugins/Schedule/Workshop）+ 错误不再被吞；壳层拆分（路由纯函数/OverlayHost/抽屉/intake/命令，DApp 1213→644）；ChatViewModel 7 步拆分（1660→799），ChatScreen 参数 55→10，双模型删除 | verify 全绿；CI 绿；每步单独 squash 可 revert |
| M4 | **只准备不发布**：社区文件、发布流程、开源就绪清单、release workflow（仅 `workflow_dispatch`） | 未公开、未发版、未换签名 |

### 2.1 里程碑与提交/tag 对账

| 里程碑 | tag（远端） | 合并 PR | 收口提交（`git log --grep`） |
|---|---|---|---|
| M0 | `m0` = `05bab08` | 本地 merge `05bab08` | `1086958` [M0-8] |
| M1 | `m1` = `972d4b8` | #1 | `d7bb610` [M1-10] |
| M2 | `m2` = `a8f632f` | #2 | `d02e716` [M2-10] |
| M3 | `m3` = `27a702b` | #3（M3-17 收口提交 `763c117` 随 PR #4 合并） | `763c117` [M3-17] |

> 证据：`git tag --contains <收口提交>`（M0-8 ⊂ m0–m3、M1-10 ⊂ m1–m3、M2-10 ⊂ m2–m3；M3-17 收口晚于 `m3` tag，见 `git log --oneline 27a702b..763c117`）。

## 3. 关键数字（重构前 → 后）

| 指标 | 前 | 后 |
|---|---|---|
| 主代码行数 | 50,916 | 53,869（+5.8%，新增契约/接口/state holder 层；God class 同步下降） |
| 测试行数 | 20,087 | 23,792 |
| 单测 | 1,024 | **1,165**（+141，0 失败 0 skip） |
| 模块 | 4 | 5（+`:runtime-api`） |
| 提交 | 103 | 173（master；M0–M3 期间 +70；分支实现 tip 168） |
| 最长文件 | ChatViewModel 1,660 | ChatViewModel 799（原 1,660） |
| DeepDeepApp | 1,213 | 644 |
| PluginManager | 676 | 309（+3 个类共 519） |
| MiCloudTools | 1,008 | 82 门面（+6 文件 988） |
| MarkdownParser | 1,193 | 142 门面（+3 文件 1,055） |
| 构建可复现 | ❌ 只在本机 | ✅ 干净机器可构建（CI 证明） |
| 门禁 | 无（边界任务从未执行） | `verify.sh` 一条命令；CI 强制；master 分支保护 |
| 合规 | 无 LICENSE，GPL 义务半套 | Apache-2.0 + 第三方声明齐全（自检 0 MISSING） |
| 文档 | 功能清单 + 602 行 changelog | 用户/开发/agent/决策四层 + 191 条验收 ID + 7 篇 ADR |

## 4. 这次重构真正改变了什么

1. **可验证**：以前只有作者肉眼真机判断；现在机器能判断编译/测试/lint/边界，人只看验收条目与截图。
2. **可并行**：以前两个 agent 必踩同一批 God 文件；现在壳层/聊天/五屏各自有契约与 state holder，任务卡写死白名单，构建全局串行。
3. **可复现**：以前换机器/清缓存即失败；现在新机器只需本仓库。
4. **可交接**：以前知识在作者脑内与 changelog 里；现在规则（AGENTS/PLAYBOOK）、决策（ADR）、现状（ARCHITECTURE）都在仓库里。
5. **可回滚**：一任务一 squash 提交，`git revert` 一行；里程碑 tag 可整体回退。

## 5. 需要你决策（M4，均未执行）

| 项 | 决策点 | 影响 |
|---|---|---|
| 公开发布 | 何时把仓库从 private 转 public | **不可撤回**（fork/缓存/爬虫）；按 `docs/dev/OPEN_SOURCE_CHECKLIST.md` 逐项过 |
| 正式签名 | 是否换正式 keystore | 换签后现装设备必须卸载，`allowBackup=false` → 会话/凭据/提醒全丢（ADR-0001） |
| 品牌素材 | 3 个非开源 App Store 图标 / Radius / mascot 参考图 | 公开分发/商用前替换或取得授权（M4-4） |
| Maven Central | pi2kotlin v0.3 改 group `io.github.sadami3066` 发布 | 外部发布动作，需你点头（ADR-0007） |
| 社区治理 | 是否开 Discussions / CODEOWNERS 强制评审 | 出现第二个能批的人再开（ADR-0004） |

## 6. 后续怎么继续

- 任务从 `docs/dev/refactor/tasks.tsv` 取活跃卡（现只剩 M4 deferred；M0–M3 与 M5 的完成卡在 `tasks-archive-2026-10.tsv`）；新任务按 `docs/dev/refactor/PLAYBOOK.md` 写卡，并按 L0–L4 选档。
- 任何 agent 先读 `AGENTS.md`；构建只走 `./scripts/verify.sh` / `./scripts/gradlew-locked`。
- 真机验收用 `docs/dev/ACCEPTANCE.md` + `scripts/collect-evidence.sh`。
- 发版流程见 `docs/dev/RELEASING.md`（启用前需 keystore 与 Secrets）。

## 7. 已知风险与未做

- 本次未做真机 UI/性能回归（M3 的 UI 改动靠编译 + 1165 单测 + 源级等价证明）；建议在决定发版前跑一遍 `docs/dev/ACCEPTANCE.md` 的 CHAT/PROCESS/SETTINGS/PLUGIN 域。
- `AgentRuntimeHost.kt` 仍 1,119 行（已从 1,083 增长：接口适配 + 日志抽象），有 6 场景直接测试但未继续拆。
- `ConversationState` 提升为 public；`sessionModel` 在 conversation 与 chrome 仍有双表示（保留旧行为）。
- `:runtime-api` 自身无测试，靠 `:runtime` 377 个测试覆盖。
- `OPEN_SOURCE_CHECKLIST` 中未勾选项（截图、CHANGELOG 手写项、Maven Central、正式签名、品牌替换、CODEOWNERS）均为待决策/待执行项。
