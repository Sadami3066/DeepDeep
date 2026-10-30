# agent 执行协议（DeepDeep）

读者：执行任务的人类或 AI agent。规则优先于任何文档里的数字；命令输出优先于文档。

## 读法

**本文按需读，不是开工必读。** 日常看 `AGENTS.md`（§1 任务类型表 + §6 UI 轻量模式）就够了；只有这些情况才往下读：

- 要写 / 改任务卡 → §1、§1.1、§1.2
- 要判「这改动要不要证伪 / 要不要真机证据」→ §2
- 交 PR、要完整模板 → §8、§9
- 要查「照做会变糟」的 15 条 → §6
- 要判「这活能不能交给 agent」→ §7
- 回滚演练 → §11

## 0. 验证档位（L0–L4 + 手动 CI）

**CI 不再自动跑；每次改动由你或 Lead 选一档，跑对应命令。** 对话里说「L0 / L1 / L2 / L3 / L4 / 用 CI」即可切换。

任务类型 → 档位 / 要不要卡 的速查表在 `AGENTS.md §1`；本节只说明每档具体跑什么、要求什么。

| 档 | 名字 | 命令 | 跑什么 | 本机实测 | 典型场景 |
|---|---|---|---|---|---|
| L0 | docs | `./scripts/gate.sh 0` | 文档断链检查 | ~1s | 只改文档/文案 |
| L1 | compile | `./scripts/gate.sh 1` | L0 + 五个模块编译 | ~5s（改 1 个文件） | 改代码先看能不能编过 |
| L2 | test | `./scripts/gate.sh 2` | L0 + 三个模块单测 | ~5s | 纯重构/测试改动，行为不变 |
| L3 | full | `./scripts/gate.sh 3` | L0 + `check`（单测/lint/模块边界）+ debug APK | ~75s | **改代码默认档** |
| L4 | device | `./scripts/gate.sh 4` | L3 + 覆盖安装到真机 | L3 + ~10s | 要上手看效果 |
| CI | cloud | `./scripts/gate.sh ci fast / full` | GitHub 上手动跑（4 核并行） | 另算 | 发版前、改门禁、怀疑"干净机器能构建" |

- 耗时是 2026-10-30 本机（改 1 个文件后）实测；冷启动全量约 1m20s。
- CI 只在手动触发和推 `v*` tag 时跑；PR 推送不触发、合并不等待。
- 改到持久化/权限/启动顺序/UI/构建/热点文件 → 至少 L3；要真机看 → L4；拿不准就升一档。
- 每档都先跑 L0，所以断链不会进 master。

### 0.1 档位 → 流程强度

| 档 | 任务卡 | G2 证伪 | 真机证据 | 提交 body 三行 |
|---|---|---|---|---|
| L0 | 免 | 免 | 免 | 可省 |
| L1 | 可免 | 免 | 免 | 可省 |
| L2 | 一卡 | 免 | 免 | 必填 |
| L3 | 一卡 | Lead 抽检 | 涉 UI 才要 | 必填 |
| L4 | 一卡 | 强制 | 强制（ACC + 截图） | 必填 |

- 任务卡粒度：**一张卡 = 一个可独立验收的功能/修复**，可含多个提交（每个仍要能独立 revert）。
- `tasks.tsv` 只放活跃卡；完成卡移到 `tasks-archive-<yyyy-mm>.tsv`，不长期堆积。
- 免卡不等于免记录：改到基线数字仍要同步 `BASELINE.md`。

## 1. 一个任务的定义

一个任务 = **一张卡（tasks.tsv 一行）= 一份简报 = 一个分支 = 一个 squash 提交 = 一次 revert**（L0/L1 可免卡；L2 以上适用）。

开工前把卡上的 `scope`（允许修改文件白名单）、`accept`（验收判据）、`verify`（验证命令）抄进简报；任一为空则退回补卡。

### 1.1 任务简报模板（agent 的完整上下文，缺段不许开工）

```markdown
# 任务：<一句话目标>            id: M0-3    依赖：M0-2

## 目标（可观察）
完成后：<外人一句话能判断做到没做到的变化>

## 允许修改的文件（白名单，超出即停）
- build.gradle.kts
- scripts/verify.sh

## 不在范围（明确禁止顺手做）
- 不重排无关文件、不格式化、不做「顺手优化」
- 不改本卡之外的任何构建配置

## 验收判据（每条都要给出证据）
1. `./scripts/gradlew-locked check --dry-run -q | grep -c verifyModuleBoundaries` ≥ 1
2. `./scripts/verify.sh` 退出码 0
3. 负向自测：临时加一条 `implementation(project(":shell"))` 到 `:runtime`，verify.sh 必须报红；验证后撤回

## 回滚
单个 squash 提交 → `git revert <sha>`；数据影响：无

## 禁止
改 settings.gradle.kts（除本卡声明）/ 签名 / 新增依赖 / 代码里写注释 / 改 res/values 文案 / 碰 4 个热点文件
```

### 1.2 写卡人自检（M1-10 试跑补的四条）

1. **目标句必须可判定**：能对应一条命令、一个计数或一个可见现象；「不要再频繁读盘」这种句子不合格，要写成「注入 50 个 TextDelta 后 storeReadCount 不变」。
2. **目标句不得与「不在范围」矛盾**：如果达成目标必须动白名单外的文件，说明卡写错了，先改卡再开工，不要让执行 agent 自行扩张。
3. **写明验收 seam**：允许新增 `internal` 计数/测试替身，还是禁止改生产代码接口。不写清楚，执行 agent 会在「无法测」处卡住或偷偷加测试钩子。
4. **指定 G2 执行者**：L4 必须由 Lead 指定另一个只读 agent 做，执行者不许自己给自己证伪；L3 由 Lead 抽检；L0–L2 不做。

## 2. 三道闸门

| 闸门 | 谁做 | 产物 | 作者是否读代码 |
|---|---|---|---|
| G1 机器 | 执行 agent | 编译/测试数/lint/边界/变更面 原始输出 | 否，只核数字 |
| G2 证伪 | **另一个只读 agent**（Lead 指定，执行者不参与） | 三条结论：最可能在哪崩 / 哪些测试其实没覆盖 / 哪部分照做会变糟 | 否，只核「有没有找到东西」 |
| G3 人 | 作者 | 按语句条目录屏 + 走一次回滚 | 否，按条目打勾 |

**G2 硬规则**：禁止输出「看起来没问题」；必须至少产出 1 条「这条改动的假设可能不成立」，否则 G2 无效。理由：2026-10-30 的 128 条评审第二遍复核仍揪出 4 条错误子结论 + 15 条有害建议，其中一条（换 release keystore）照做会丢光全部会话与凭据。

**各档要求**：L4 三道全做；L3 做 G1，G2 由 Lead 抽检、G3 仅在涉 UI 时做；L0–L2 免 G2/G3（L0 由 `scripts/check-docs.sh` 兜底）。

## 3. DoD（反偷懒条款）

缺一条即退回，不允许「先留 TODO / 后面补测试」（L0/L1 不适用本 DoD；L2 要求 1/2/8；L3 要求 1–9；L4 全量，另加第 10 条证据）：

1. 编译通过
2. 单测 ≥ `BASELINE.md` 基线且 0 skip
3. 新增/修改行为有测试；**把修复回退后该测试必红**（贴红的那次输出）
4. `lintDebug` 0 error，警告数不超基线
5. `./scripts/verify.sh` 退出码 0
6. 变更文件 ⊆ 白名单；`git diff --stat` ≤400 行 / ≤15 文件 / ≤2 模块（超了先在卡里说明理由）
7. 文档数字由命令生成，不手写
8. 一个 squash 提交，标题 `type: 摘要 [ID]`
9. 回滚演练：**执行 agent 不提交**，所以演练是「把本次改动回退到工作区（`git stash` 或手工反改）→ `./scripts/verify.sh` 干净通过 → 恢复」；已提交后由 Lead 用 `git revert --no-commit <sha>` 复核
10. 涉 UI/行为：真机条目 + 截图落 `docs/dev/evidence/<date>-<id>/`

## 4. 验证闭环（顺序固定，任一红即停）

```bash
./scripts/verify.sh      # 编译 + 单测 + lint + 边界（内含串行锁）
git diff --name-only     # 对照白名单
git diff --stat          # 对照预算
```

本机可用内存有限：**所有 Gradle 调用必须走 `./scripts/gradlew-locked`**（`verify.sh` 已封装），它用 `flock` 全局串行并默认 `--max-workers=1`。并发构建在 2026-10-30 已造成整机 OOM 死机一次——禁止直接 `./gradlew`，也不要绕过 `verify.sh` 直接跑构建。定向测试同样走 `./scripts/gradlew-locked <task>`。

## 5. 协作规则

- **同一文件同一时间只有一个 owner**；并行任务必须写 scope 不相交。
- **一个 PR 只碰一个 ownership zone**（或 zone + 契约）。
- **ChatViewModel 7 步为串行链**：由单 owner 执行；期间其他人只改 `ChatScreen`/`ChatTurn`/`ChatMarkdown`/`MarkdownParser`/新状态机文件。
- **构建串行**：见 §4。
- **大 diff**：agent 自行拆成 N 个可顺序合并的小 PR（每个独立编译、独立 verify），拆不开就退回重做；不允许「一次 36k 行」。
- **升级规则**：只在这三类停下来问，一次最多 3 条——① 外部副作用（发 Release、切 public、改 GitHub 设置）；② 产品取舍；③ 无法自证的阻塞。其余自己查（允许 websearch 查版本/配置，以实测为准）并继续。

## 6. 必读：禁止动作清单

来自评审复核（`docs/dev/review/10-30/REVIEW_OF_REVIEW.md §4`），照做会让项目变糟：

1. 现在换 release keystore（现装 APK 是 debug 证书，换签无法覆盖安装，`allowBackup=false` → 卸载即丢全部会话与凭据）。正确做法：备份现有 `~/.android/debug.keystore` 到项目外，指向同证书副本；等愿意卸载重装再换。
2. 损坏 `plugins.json` 时「跳过一切写入」→ 7 个内部插件记录永久补不回。
3. 启动「只读快照、不探测插件」→ 首轮系统提示缺 `official_plugins` 段。
4. `RunLedger.begin` 改异步落盘 → 摧毁中断恢复的唯一价值。
5. `events` 改单消费者 Channel → 4 个订阅者丢广播。
6. 根组合「下沉」一次重写 `DeepDeepApp.kt`（当前 644 行）→ 踩在冻结/预测返回/SaveableState 机制上且 UI 零测试。
7. 消息内嵌 LazyColumn 做块级懒加载 → 同轴无界高度，非法约束。
8. 去掉滚动跟随的两帧等待 → 用陈旧 layoutInfo 算 overflow，贴底失败。
9. 改用 `OAuthLoginState.waiting` 判断 → 语义不同（waiting 要求 url != null）。
10. 卸载时删除截图/录音目录 → 未告知的数据丢失。
11. 主题/字体/语言再加一份同步缓存 → 双写不一致。
12. 深链找不到会话「一次重试后 consume」→ 削弱冷启动正确语义。
13. 给 `AppGraph.scope` 加全局 `CoroutineExceptionHandler` → 吞掉未捕获异常。
14. ABI 只留 arm64 → 模拟器装不上，收益 0.05%。
15. 单人期就上 detekt/ktlint/Kover/LFS/CODEOWNERS 强制审批 → 成本真实、收益未证；等触发条件（见 `docs/dev/adr/0004-trunk-based-one-task-one-revert.md` 的「推翻条件」）。

## 7. 什么任务不该交给 agent（标 `human-only`）

1. 会删/改用户数据的（签名、损坏重建、schema 迁移、卸载清理）
2. 改所有权/并发模型的（拆全局锁、事件改 Channel、根组合重写、ViewModel 整拆）
3. 改构建与供应链的（依赖源、签名、ABI、R8、新工具链）
4. 以性能为名的数值优化（无 `gfxinfo` 前后对比一律不做）
5. 产品取舍（权限审批、root、云服务范围、发布形态）

判据一句话：**失败代价是「丢数据 / 全 App 回归」或「无法肉眼验收」的，agent 只能出方案不能动手。**

## 8. PR 模板

唯一来源是 `.github/pull_request_template.md`（按档位删减填写）。正文别在两处维护。

## 9. 提交规范

- 标题：`type: 祈使句摘要 [ID]`；`type ∈ feat|fix|refactor|perf|docs|test|chore|build|revert`
- squash 合并，PR 标题即最终提交标题；分支上碎提交随意，进 master 只能一条
- body 三行（`验收:`、`回滚:`、`不在范围:`）——L2–L4 必填；L0/L1 可只写标题
- 提交标题必须带 backlog ID——这是「128 条 findings 修了哪些」唯一可机器对账的入口；L0/L1 无卡时用 `[L0]`/`[L1]`

## 10. 规则入口

常驻规则只有 `AGENTS.md` 一处，本文不复制它的内容。

## 11. 回滚演练（L3/L4 每卡一次；L0–L2 不适用）

```bash
git revert --no-commit <squash-sha>
./scripts/verify.sh          # 必须干净通过
git revert --abort           # 或提交这次 revert
```

## 12. 与评审 backlog 的关系

`docs/dev/review/10-30/findings.tsv` 是 128 条问题地图，**不是施工图**；M0/M1 任务卡会引用其中 ID（如 RT-006、CH-004、GL-005），但必须叠加 `REVIEW_OF_REVIEW.md` 的修正（严重度降一档、15 条禁止动作、成本重估）后执行。已修条目必须在 tsv 里标 `status=已修 <sha>`。
