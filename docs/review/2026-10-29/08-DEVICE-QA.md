# 真机验收（round38，2026-10-29）

设备：Redmi 22101316UCP / Android 13 / MIUI 14 / arm64-v8a（`jbizr44lkbnbromb`，adb USB）。
App：`io.github.whywood.deepdeep` 0.2.0-dev（debug，pid 7733 全程未变，无崩溃重启）。
安装包构建时间 `2026-10-29 16:24`，DEX 含 round37 修复符号（`clearAutoRetry`/`maybeAutoRetry`），即 round37 代码级构建。
界面语言为英文，故「自动重试 n/5」在本次设备上对应文案 `Retrying n/5`。

本文件只记录真机结果；性能基线原始数据见 [`artifacts/validation/round38/perf-before.txt`](../../../artifacts/validation/round38/perf-before.txt)。

## 结论总览

| 项 | 结果 | 摘要 |
|---|---|---|
| B03 停止取消自动重试 | **部分通过** | 自动重试 5 次正常、停止后不再重试、UI 回可输入；但状态行**未显示** `Retrying n/5`（缺陷 D1） |
| B04 升级失败保留旧插件 | **失败** | 断网重装失败后旧 `lark-cli` 被删除、插件变为「需要重新安装」（缺陷 D2）；技能与安装记录仍在 |
| R02 运行中插件变更 | **通过** | 运行中关插件开关不中断本轮，结束后 deferred reconfigure 生效 |
| 聊天流性能基线 | **已完成** | 滚动 1216 帧 / Janky 19（1.56%）；流式 1866 帧 / Janky 158（8.47%） |
| 附带回归 | **通过** | 抽屉 `Just now` 排序正常、`.corrupt` 无新增、logcat 无 `FATAL EXCEPTION` |

## B03 停止取消自动重试

步骤：`su -c 'svc wifi disable'` + `su -c 'svc data disable'`（`Active default network: none`，ping `Network is unreachable`）→ 在聊天发送消息 → 观察状态行 → 点停止 → 观察约 14s → 恢复网络。

- 不点停止时：`run session=…` 后连续 5 次 `retry session=…`，间隔约 2.0–2.1s，第 5 次后停止（`MAX_AUTO_RETRIES=5`），符合自动重试策略 —— 证据 `B03-00-auto-retry-baseline.txt`、`B03-07-baseline-cycle-end.png`。
- **缺陷 D1：状态行未显示 `Retrying n/5`**。12s 屏幕录制（5fps，58 帧）逐帧 OCR：第 8–57 帧状态行全程为 `Thinking`（`••• Thinking`/`Thinking · 0s`），从未出现 `Retrying`；同一时间窗 logcat 证明正在自动重试。证据 `B03-05-auto-retry-screenrecord.mp4`、`B03-06-screenrecord-status-ocr.txt`、`B03-01-status-during-retry.png`。真机表现与 DEVICE_TESTING 第 136 条「进入自动重试 n/5 倒计时」不符。
- 停止：重试窗口内点红色停止按钮（坐标由截图像素检测得到 `972,2264`）→ 停止后 14s 采集 logcat：仅停止前 1 条 `run session=`、`retry session=` 0 条（对比未停止时会有 5 条 retry），UI 状态行消失、恢复输入框与发送按钮 —— 证据 `B03-02-after-stop.png`、`B03-03-after-4s.png`、`B03-04-runtime-log.txt`。停止取消重试的行为本身正确。
- 恢复网络（`svc wifi enable`/`svc data enable`，`Active default network: 147`）后发送消息，模型正常回答 —— 证据 `B03-08-network-restored.png`、`B03-09-restored-runtime-log.txt`。

## B04 升级失败保留旧插件（失败）

步骤：插件页 → Feishu 详情（Status `已连接 · 黄宇鹏`、Version/CLI version `1.0.98`、Skills `deepdeep-feishu`，证据 `B04-01-feishu-detail-before.png`）→ 断网 → 点 `Reinstall`。

- 失败提示出现：`Unable to resolve host "github.com": No address associated with hostname` —— 证据 `B04-02-reinstall-tapped.png`。
- **缺陷 D2：旧插件被破坏**。失败后：
  - 详情页 Status 变为「需要重新安装」，操作区出现 `Connect Feishu`（不再是 Reauthorize/Sign out）—— 证据 `B04-03-after-failure.png`。
  - guest 内 `/usr/local/bin/lark-cli` **被删除**（目录 mtime 即失败时刻，仅剩 `wecom-cli`）；`files/linux/rootfs/root/.local/share/lark-cli` 数据目录仍在 —— 证据 `B04-04-after-failure-files.txt`。
  - `plugins.json` 仍记录 `feishu 1.0.98 installed/enabled`；技能文件 `files/skills/deepdeep-feishu/SKILL.md` 与 `files/agent/skills/deepdeep-feishu/SKILL.md` 仍在且 md5 未变（`26956451…`）—— 同上证据。
  - 结论：安装记录与技能保留，但 CLI 二进制丢失导致插件实际不可用，即 round37「B04 升级失败保留旧版」在该路径下**未生效**。
- 根因（代码定位）：`app/.../plugin/feishu/FeishuPlugin.kt` 的 `install()` 在 catch 中调用 `restoreOrRemoveBinary()`；该函数在**下载阶段**失败（尚未执行安装步骤、无 `.lark-cli.old` 备份）时走 `else rm -f /usr/local/bin/lark-cli`，把已安装的旧二进制删掉。`PluginManager.installInternal` 的「仅首次安装才 uninstall」保护无法覆盖插件内部这段清理。
- 现场恢复：恢复网络后再次 `Reinstall` 成功（约 90s），Status 回到 `已连接 · 黄宇鹏`，`lark-cli` 二进制恢复且 md5 与失败前一致（`1763a724…`，45,678,754 字节），技能 md5 不变 —— 证据 `B04-06-reinstall-done.png`、`B04-07-after-repair-files.txt`。
- 修复后经会话 bash 复核 CLI：`lark-cli --version` → `lark-cli version 1.0.98` —— 证据 `B04-09-larkcli-version-bash.txt`、`B04-08-larkcli-bash.png`。

## R02 运行中插件变更（通过）

步骤：会话发送「Run bash command sleep 20 then reply exactly ok」→ 运行中切到插件页关闭 `Web Search` 开关 → 回会话。

- 开关确实生效：`plugins.json` 中 `web-search.enabled=false`（其余 true）。
- 本轮未被中断：run 于 17:33:26 启动，`Process · 1 tools · 24s`，最终回答 `ok`，无停止/失败记录 —— 证据 `R02-01-running.png`、`R02-02-plugins-during-run.png`、`R02-03-plugin-toggled.png`、`R02-04-back-to-chat.png`、`R02-05-answer-ok.png`。
- 结束后应用重配：`mcp attach session=01a186e9-…` 于 17:33:51（run 结束之后）出现，说明 reconfigure 走的是「本轮结束应用」而非运行中关闭重开 —— 证据 `R02-06-reconfigure-log.txt`。
- 测试后已把 `Web Search` 开关恢复为开启（`enabled=true`）。

## 聊天流性能基线（优化前）

采集方式与原始输出：`artifacts/validation/round38/perf-before.txt`（含 `dumpsys gfxinfo` 与 `framestats` 全文）。

| 场景 | 窗口 | 总帧数 | Janky 帧 | Janky 率 | Slow UI thread | Slow issue draw |
|---|---|---|---|---|---|---|
| 长会话滚动 10s（262 条消息会话，54 次 swipe） | 10s | 1216 | 19 | 1.56% | 12 | 13 |
| 流式长回答（800 词英文，1069 output tokens / 15.2s） | ~20s | 1866 | 158 | 8.47% | 154 | 6 |
| 同上（第一次测量，含键盘开合） | ~25s | 1907 | 176 | 9.23% | 173 | 5 |

分位：滚动 50/90/95/99 = 8/9/10/17ms；流式 50/90/95/99 = 8/17/19/22ms。
结论：滚动流畅；流式期间 Janky 率约为滚动的 5 倍，且慢帧集中在 UI thread（154），与「流式增量重解析 Markdown」的假设一致，可作为 Markdown 增量解析优化的对照基线。

## 附带回归

- 抽屉排序：测试会话（首条消息 `network down retry test`）在 Home 下排第一且显示 `Just now` —— 证据 `REG-01-drawer-sort.png`。
- `.corrupt`：会话目录下仍只有 round37 遗留的 1 个 `2026-10-25T06-24-52-006Z_01a171bb-….jsonl.corrupt`，本次无新增 —— 证据 `baseline-pre.txt`、`REG-02-corrupt-files.txt`。
- 崩溃：全程 `adb logcat -d | grep "FATAL EXCEPTION"` 为 0，App 进程 pid 7733 未变化 —— 证据 `REG-02-corrupt-files.txt` 同批采集。

## 缺陷清单

| 编号 | 级别 | 项 | 描述 | 位置/线索 | 证据 |
|---|---|---|---|---|---|
| D1 | P2（UI） | B03 | 自动重试期间状态行显示 `Thinking`，不显示 `Retrying n/5`；倒计时文案在真机不可见 | `ChatViewModel.maybeAutoRetry` 设置 `activity=label` 后疑似被 `RunStarted`/`ActivityChanged(Thinking)` 覆盖；单元测试未断言文案 | `B03-05-auto-retry-screenrecord.mp4`、`B03-06-screenrecord-status-ocr.txt` |
| D2 | P1 | B04 | 断网触发重装/升级失败后，已安装的 `lark-cli` 被删除，插件状态变为「需要重新安装」，CLI 不可用；安装记录与技能仍在 | `FeishuPlugin.install()` catch → `restoreOrRemoveBinary()` 在无 `.lark-cli.old` 时 `rm -f` 已安装二进制 | `B04-03-after-failure.png`、`B04-04-after-failure-files.txt` |

## 未完成 / 限制

- 无未执行项。B03 的「状态行出现自动重试 n/5」为**观察失败**，已按缺陷 D1 记录，未按通过计。
- 因插件当前已是 1.0.98（最新），B04 使用详情页 `Reinstall` 触发下载失败；若后续提供更高版本，「Update」路径需复测同一函数。
- 测试在新建的 Home 会话（首条消息 `network down retry test`）中进行，失败轮保留在该会话内（可随时删除）；未删除任何用户会话，未改动用户数据，未 `pm clear`/卸载。

## 环境恢复

- 网络：`wifi_on=1`、`mobile_data1=1`、`airplane_mode_on=0`、`Active default network: 147`，与测试前一致（`mobile_data` 全局值测试前后均为 0）—— 证据 `FINAL-network-state.txt`。
- 插件：`feishu` 已恢复可用（已连接、CLI 1.0.98、技能在）；`web-search` 开关恢复为开启。
- 未改任何 App 设置项、语言、字体、模型配置。

## 证据文件

`artifacts/validation/round38/`：

- 基线：`baseline-pre.txt`、`FINAL-network-state.txt`
- B03：`B03-00-auto-retry-baseline.txt`、`B03-01-status-during-retry.png`、`B03-02-after-stop.png`、`B03-02b-cycle-end.png`、`B03-03-after-4s.png`、`B03-04-runtime-log.txt`、`B03-05-auto-retry-screenrecord.mp4`、`B03-06-screenrecord-status-ocr.txt`、`B03-07-baseline-cycle-end.png`、`B03-08-network-restored.png`、`B03-09-restored-runtime-log.txt`
- B04：`B04-00-plugin-files-before.txt`、`B04-01-feishu-detail-before.png`、`B04-02-reinstall-tapped.png`、`B04-03-after-failure.png`、`B04-04-after-failure-files.txt`、`B04-05-reinstall-retry.png`、`B04-06-reinstall-done.png`、`B04-07-after-repair-files.txt`、`B04-08-larkcli-bash.png`、`B04-09-larkcli-version-bash.txt`
- R02：`R02-01-running.png`、`R02-02-plugins-during-run.png`、`R02-03-plugin-toggled.png`、`R02-04-back-to-chat.png`、`R02-05-answer-ok.png`、`R02-06-reconfigure-log.txt`
- 性能：`perf-before.txt`、`PERF-00-long-session.png`、`PERF-01-scroll-gfxinfo.txt`、`PERF-02-stream-gfxinfo.txt`、`PERF-03-stream-answer.png`、`PERF-04-framestats.txt`、`PERF-05-stream-gfxinfo-clean.txt`
- 回归：`REG-01-drawer-sort.png`、`REG-02-corrupt-files.txt`

## 修复复测（D1/D2）与性能对比

说明：第二轮复测原分配给 device-qa 子任务，其会话中途异常退出（未留报告），由 Lead 在同一设备上补做；设备网络与插件状态已恢复。

构建：`2026-10-29 17:51`（含 round37 + round38 R08 + D1/D2 修复 + Markdown 分块解析），`lastUpdateTime=2026-10-29 17:52:09`，pid=19047 全程未变，无 `FATAL EXCEPTION`。

### D1 自动重试状态行：通过

- 断网（`Active default network: none`）发送消息后，用 `uiautomator dump` 轮询状态行：捕获到 `Retrying 1/5`、`Retrying 3/5`、`Retrying 4/5`，随后 5 次重试全部执行、失败轮显示 `Request failed. Try again.` + `Retry`。证据 `RETEST-01-D1-frame00..02.txt`、`RETEST-02-D1-runtime-log.txt`。
- 结论：修复生效，倒计时标签不再被宿主的终态 `ActivityChanged` 覆盖。停止取消重试在上一轮已通过，本轮未重复。

### D2 插件升级失败保留 CLI：通过

- 基线：`feishu 1.0.98` 已安装，`lark-cli` 45,678,754 字节、md5 `1763a7240ed0750ef9107a75539ebe0f`、时间戳 17:31（`RETEST-00-baseline.txt`）。
- 断网后点 Reinstall，失败通知：`安装失败：Unable to resolve host "github.com": ...`（`RETEST-03-D2-failure-notification.txt`、录像 `RETEST-06-D2-reinstall-failure.mp4`）。
- 失败后：`lark-cli` 仍在、字节数与 md5 与基线完全一致（`RETEST-04-D2-cli-md5-after.txt`）；`plugins.json` 仍为 `feishu 1.0.98 installed/enabled`（`RETEST-05-D2-plugins-json.txt`）；详情页 Status 仍为「已连接 · 黄宇鹏」、Version/CLI version 仍 1.0.98（`RETEST-07-D2-after-failure.png`）。
- 结论：插件内部回滚不再删除已安装二进制；安装记录、技能、CLI 与连接状态保持一致。

### Markdown 分块解析：单元层有效，真机帧指标无法得出改善结论

- 单元层（JVM，12970 字符 / 540 批）：解析层 67ms → 41ms（约 1.6x），稳定块 0 次重复解析（`app-entry` 报告，临时 probe 已删除，未进代码）。
- 真机（同一长会话，`dumpsys gfxinfo`）：
  - 滚动 10s：Janky 12 / 1203 帧（1.00%），优化前 19 / 1216（1.56%）。
  - 流式长回答两次：183 / 1651（11.08%）、244 / 1732（14.09%）；优化前 158 / 1866（8.47%）。原始输出见 `perf-after.txt`。
- 结论：滚动基本持平（1.56% → 1.00%，在噪声范围）；流式两份结果与优化前不具备可比性——提示词不同、采集窗口包含输入与空闲帧、设备刷新率和总帧数不同，且本次两次结果之间本身波动 3 个百分点。**不把该优化记为“真机性能改善”**；下一步应使用固定输入、固定窗口的 Macrobenchmark 或 Perfetto trace 才能判断流式卡顿来源（文本排版/绘制还是解析）。
- 证据：`perf-after.txt`（三份 raw gfxinfo）、`shot00/04/08/12/16/20.png`（重试状态帧）。

### 环境恢复

Wi-Fi 与移动数据已重新开启（`Active default network: 155`），飞书插件状态与二进制未被破坏、无重装，未 `pm clear`/卸载/删除会话与插件。

### R08 持久化补充验证（Lead，同一构建）

- **Job 持久化**：让模型用 `job_start sleep 90` 起任务（`files/jobs/jobs.json` 出现 `status=RUNNING` 记录）→ `am force-stop` 杀进程 → 重开 App → 让模型 `job_list`，返回 `job-307d1af8 — interrupted (restart-test)`，结果未知但可见，不静默消失。证据 `RETEST-11-R08-jobs-after.txt`（文件仍记 RUNNING，内存按 interrupted 投影；下一次写盘会被覆盖）。
- **待答问题持久化**：`ask_user_question` 等待中 `files/questions/pending.json` 有 PENDING 记录 → 杀进程重开 → 文件被清空为 `questions:[]`；聊天里出现本地提示「Questions interrupted from the last session: 1. They can no longer be answered; tell me to continue」；通知栏出现 id=400（`session_results`）标题「有提问未完成」/正文「上次有 1 个提问未完成，已失效。点击打开会话继续。」。证据 `RETEST-08-R08-interrupted-notice.png`、`RETEST-09-R08-pending-cleared.txt`、`RETEST-10-R08-notification.txt`。
- **提醒投递 outbox**：未做设备验证——要制造「投递调用返回前进程死亡」的毫秒级窗口，人工无法稳定复现；由 `ScheduleServiceTest` 的 intent 预置/补投/幂等用例覆盖。
