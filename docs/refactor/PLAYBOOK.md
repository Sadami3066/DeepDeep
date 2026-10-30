# agent 执行协议（DeepDeep 重构期）

读者：执行任务的人类或 AI agent。规则优先于任何文档里的数字；命令输出优先于文档。

## 1. 一个任务的定义

一个任务 = **一张卡（tasks.tsv 一行）= 一份简报 = 一个分支 = 一个 squash 提交 = 一次 revert**。

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
1. `./gradlew check --dry-run -q | grep -c verifyModuleBoundaries` ≥ 1
2. `./scripts/verify.sh` 退出码 0
3. 负向自测：临时加一条 `implementation(project(":shell"))` 到 `:runtime`，verify.sh 必须报红；验证后撤回

## 回滚
单个 squash 提交 → `git revert <sha>`；数据影响：无

## 禁止
改 settings.gradle.kts（除本卡声明）/ 签名 / 新增依赖 / 代码里写注释 / 改 res/values 文案 / 碰 4 个热点文件
```

## 2. 三道闸门

| 闸门 | 谁做 | 产物 | 作者是否读代码 |
|---|---|---|---|
| G1 机器 | 执行 agent | 编译/测试数/lint/边界/变更面 原始输出 | 否，只核数字 |
| G2 证伪 | **另一个只读 agent** | 三条结论：最可能在哪崩 / 哪些测试其实没覆盖 / 哪部分照做会变糟 | 否，只核「有没有找到东西」 |
| G3 人 | 作者 | 按语句条目录屏 + 走一次回滚 | 否，按条目打勾 |

**G2 硬规则**：禁止输出「看起来没问题」；必须至少产出 1 条「这条改动的假设可能不成立」，否则 G2 无效。理由：2026-10-30 的 128 条评审第二遍复核仍揪出 4 条错误子结论 + 15 条有害建议，其中一条（换 release keystore）照做会丢光全部会话与凭据。

## 3. DoD（反偷懒条款）

缺一条即退回，不允许「先留 TODO / 后面补测试」：

1. 编译通过
2. 单测 ≥ `BASELINE.md` 基线且 0 skip
3. 新增/修改行为有测试；**把修复回退后该测试必红**（贴红的那次输出）
4. `lintDebug` 0 error，警告数不超基线
5. `./scripts/verify.sh` 退出码 0
6. 变更文件 ⊆ 白名单；`git diff --stat` ≤400 行 / ≤15 文件 / ≤2 模块（超了先在卡里说明理由）
7. 文档数字由命令生成，不手写
8. 一个 squash 提交，标题 `type: 摘要 [ID]`
9. 回滚演练：`git revert --no-commit <sha> && ./scripts/verify.sh` 干净通过
10. 涉 UI/行为：真机条目 + 截图落 `docs/evidence/<date>-<id>/`

## 4. 验证闭环（顺序固定，任一红即停）

```bash
./scripts/verify.sh      # 编译 + 单测 + lint + 边界（内含串行锁）
git diff --name-only     # 对照白名单
git diff --stat          # 对照预算
```

本机可用内存约 3G：`verify.sh` 用 `flock` 全局串行，**禁止两个 agent 同时跑构建**。OOM 不是风格问题。

## 5. 协作规则

- **同一文件同一时间只有一个 owner**；并行任务必须写 scope 不相交。
- **一个 PR 只碰一个 ownership zone**（或 zone + 契约）。
- **ChatViewModel 7 步为串行链**：由单 owner 执行；期间其他人只改 `ChatScreen`/`ChatTurn`/`ChatMarkdown`/`MarkdownParser`/新状态机文件。
- **构建串行**：见 §4。
- **大 diff**：agent 自行拆成 N 个可顺序合并的小 PR（每个独立编译、独立 verify），拆不开就退回重做；不允许「一次 36k 行」。
- **升级规则**：只在这三类停下来问，一次最多 3 条——① 外部副作用（发 Release、切 public、改 GitHub 设置）；② 产品取舍；③ 无法自证的阻塞。其余自己查（允许 websearch 查版本/配置，以实测为准）并继续。

## 6. 必读：禁止动作清单

来自评审复核（`docs/review/10-30/REVIEW_OF_REVIEW.md §4`），照做会让项目变糟：

1. 现在换 release keystore（现装 APK 是 debug 证书，换签无法覆盖安装，`allowBackup=false` → 卸载即丢全部会话与凭据）。正确做法：备份现有 `~/.android/debug.keystore` 到项目外，指向同证书副本；等愿意卸载重装再换。
2. 损坏 `plugins.json` 时「跳过一切写入」→ 7 个内部插件记录永久补不回。
3. 启动「只读快照、不探测插件」→ 首轮系统提示缺 `official_plugins` 段。
4. `RunLedger.begin` 改异步落盘 → 摧毁中断恢复的唯一价值。
5. `events` 改单消费者 Channel → 4 个订阅者丢广播。
6. 根组合「下沉」一次重写 1159 行 → 踩在冻结/预测返回/SaveableState 机制上且 UI 零测试。
7. 消息内嵌 LazyColumn 做块级懒加载 → 同轴无界高度，非法约束。
8. 去掉滚动跟随的两帧等待 → 用陈旧 layoutInfo 算 overflow，贴底失败。
9. 改用 `OAuthLoginState.waiting` 判断 → 语义不同（waiting 要求 url != null）。
10. 卸载时删除截图/录音目录 → 未告知的数据丢失。
11. 主题/字体/语言再加一份同步缓存 → 双写不一致。
12. 深链找不到会话「一次重试后 consume」→ 削弱冷启动正确语义。
13. 给 `AppGraph.scope` 加全局 `CoroutineExceptionHandler` → 吞掉未捕获异常。
14. ABI 只留 arm64 → 模拟器装不上，收益 0.05%。
15. 单人期就上 detekt/ktlint/Kover/LFS/CODEOWNERS 强制审批 → 成本真实、收益未证；等触发条件（见 README §3）。

## 7. 什么任务不该交给 agent（标 `human-only`）

1. 会删/改用户数据的（签名、损坏重建、schema 迁移、卸载清理）
2. 改所有权/并发模型的（拆全局锁、事件改 Channel、根组合重写、ViewModel 整拆）
3. 改构建与供应链的（依赖源、签名、ABI、R8、新工具链）
4. 以性能为名的数值优化（无 `gfxinfo` 前后对比一律不做）
5. 产品取舍（权限审批、root、云服务范围、发布形态）

判据一句话：**失败代价是「丢数据 / 全 App 回归」或「无法肉眼验收」的，agent 只能出方案不能动手。**

## 8. PR 模板

```markdown
## 这是什么
（三行内人话；第一条写用户会看到什么变化）

## 关联
id: M0-3   类型: feat/fix/refactor/perf/docs/test/chore/build

## 变更面（自动填）
git diff --name-only <base>..HEAD
文件数 __ / 净增删 __ 行 / 涉及模块 __
是否 ⊆ 白名单：是 / 否（否 → 说明理由）

## G1 机器闸门（贴原始输出，不许概括）
- [ ] 编译通过
- [ ] 单测 __ 条全绿（基线 __，不得下降）
- [ ] lint 0 error（警告 __，基线 __）
- [ ] verifyModuleBoundaries 通过
- [ ] 新增行为测试：回退修复后必红（贴红输出）

## G2 证伪结论（第二个只读 agent 填）
- 最可能在哪类设备/状态下崩：
- 哪些测试其实没覆盖：
- 哪部分照做会变糟：
- 判定：成立 / 部分成立 / 不成立（不成立必须写理由）

## G3 作者验收
- [ ] 真机覆盖安装（不卸载）
- [ ] 验收条目：#___ ～ #___，逐条打勾，截图在 docs/evidence/
- [ ] 回滚演练通过
- [ ] 未覆盖风险：

## 回滚
git revert <sha> / adb install -r <上一 tag APK>
数据影响：无 / 涉及文件 ___ / 升级验证条目 #___
```

## 9. 提交规范

- 标题：`type: 祈使句摘要 [ID]`；`type ∈ feat|fix|refactor|perf|docs|test|chore|build|revert`
- squash 合并，PR 标题即最终提交标题；分支上碎提交随意，进 master 只能一条
- body 三行必填：`验收:`、`回滚:`、`不在范围:`
- 提交标题必须带 backlog ID——这是「128 条 findings 修了哪些」唯一可机器对账的入口

## 10. AGENTS.md 骨架（M1-2 落地，≤120 行）

```markdown
# AGENTS.md — DeepDeep 仓库工作规则

## 事实源
本文件命令与数字优先于 README/docs。文档数字会漂，一律以命令输出为准。

## 环境与命令（照抄）
./scripts/verify.sh
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
基线见 docs/refactor/BASELINE.md。

## 硬性边界
- 模块依赖只允许 :app → {:runtime,:shell,:design}
- 不改：settings.gradle.kts、签名配置、.gitignore、libs.versions.toml（新增依赖须批准）
- 不碰：artifacts/、字体 ttf、rootfs、任何 4MB 以上文件
- 只改任务卡白名单里的文件；需要额外文件先停下要授权
- 代码文件里不留注释；用户可见文案走 res/values*/strings_*.xml

## 已知热点（改它要单独任务 + 新增测试）
ChatViewModel.kt / DeepDeepApp.kt / AgentRuntimeHost.kt / MarkdownParser.kt

## 交付物（缺一不可）
变更文件清单 / 三条命令原始输出 / 红绿测试证据 / 回滚命令 / 真机条目与截图
```

## 11. 回滚演练（每任务必做一次）

```bash
git revert --no-commit <squash-sha>
./scripts/verify.sh          # 必须干净通过
git revert --abort           # 或提交这次 revert
```

## 12. 与评审 backlog 的关系

`docs/review/10-30/findings.tsv` 是 128 条问题地图，**不是施工图**；M0/M1 任务卡会引用其中 ID（如 RT-006、CH-004、GL-005），但必须叠加 `REVIEW_OF_REVIEW.md` 的修正（严重度降一档、15 条禁止动作、成本重估）后执行。已修条目必须在 tsv 里标 `status=已修 <sha>`。
