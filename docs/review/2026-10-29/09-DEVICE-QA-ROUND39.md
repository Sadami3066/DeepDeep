# 真机验收（round39，2026-10-29）

设备：Redmi 22101316UCP / Android 13 / MIUI 14 / arm64-v8a（`jbizr44lkbnbromb`，adb USB）。
两阶段：阶段一在 round38 包上（`lastUpdateTime` 17:52，构建 68adbf2），阶段二在 round39 包上（`lastUpdateTime` 18:52:23）。数据保留，未 `pm clear`/卸载/删会话/删插件。

## 结论总览

| 项 | 阶段 | 结果 | 摘要 |
|---|---|---|---|
| B06 凭据连写 | 一 | **通过（有限观察）** | 快速连写 invalid→real 后最终落盘 = real；重开 App 聊天可用 |
| R03 续跑窗口 | 一 | **命中 1 次**（另 3 次未命中） | 轮次结束后 50ms 内暂停 → 无新轮；重开无自动续跑 |
| R04 超大附件 | 一/二 | **通过** | 30MB 分享 → 提示「文件超过 25MB，无法添加」；附件目录无残留 |
| 运行中断提示 smoke | 二 | **部分通过** | 提示/通知均出现；但发消息后提示**不消失**（缺陷 D3） |
| 固定提示词性能 2 次 | 二 | **已完成** | A 94/965（9.74%）、B 105/1037（10.13%），Slow UI 90/102 |
| 回归 | 一/二 | **通过** | 启动、会话恢复/切换、抽屉排序、提醒投递、无 FATAL |

## 阶段一（round38 包）

### B06 凭据连写：通过（有限观察）

观察方法：`files/datastore/secrets.preferences_pb` 中 `api_key_deepseek` 为 base64(IV12+密文+GCM tag16)，base64 长度可反推明文长度：真实 key 35 字符 → 84；无效 key 21 字符 → 68。用长度变化判断「最终落盘的是哪一次写入」。

| 步骤 | 结果 |
|---|---|
| 基线 | `len=84`（真实 key） |
| 保存 invalid(21) | `len=68`（写入生效） |
| 保存 real(35) | `len=84` |
| 快速连写 invalid→real（两次 Save 间隔约 0.8s，整串 1.6s） | **`len=84`，最后一次保存生效** |
| force-stop + 重开（pid 27171） | provider 仍 Configured、会话恢复、发消息模型回复 `PONG`（stopReason=stop） |

证据：`B06-00-secrets-baseline.txt`、`B06-01..03-secrets-*.txt`、`B06-04-restart.png`、`B06-05-chat-after-restart.png`、`B06-06-result.txt`。
观察边界：无法观测磁盘中间态（DataStore 原子替换、无逐次写入日志）；也无法从 UI 制造 <100ms 的并发写，实测两次保存间隔约 0.8s。结论依据最终密文长度 + 端到端聊天可用，属于「最后一次意图生效」级别的验证，不等价于单元测试里的乱序写覆盖。

### R03 续跑窗口：命中 1 次

目标 `write a three-part es`（多轮，避免一轮即完成）。检测手段：设备端脚本轮询会话 JSONL mtime（30ms 间隔），mtime 变化即视为该轮消息落盘 → 立刻 `input tap` 目标栏 Pause 按钮。

| 尝试 | 时间 | 结果 |
|---|---|---|
| A | 18:45 | 未命中：Pause 坐标量错（点到上下文计量条，弹出 Usage sheet），goal 继续跑完 round 1 |
| B | 18:46 | 未命中：键盘展开时误触停止键；观察到 `pauseOnStop`——运行中点停止会自动把 goal 置为 PAUSED |
| C | 18:49 | 未命中：watcher 基线取早（捕获到 user 消息写入），在 turn 运行中暂停 |
| D | 18:49–18:50 | **命中**：goal round 结束（JSONL mtime 18:50:09.211）→ 18:50:09.261 点 Pause（约 50ms，窗口 400ms 内）→ 之后无第 3 个 `run session=`；goal `phase=PAUSED, roundsStarted=2` |

命中后验证：force-stop + 重开 App → 重开后 **0 个 `run session=`**，旧轮未自动续跑（符合 R03 修复预期）。
补充观察：该 goal 在 18:50:16 被模型 `update_goal` 置为 COMPLETE（在暂停之后），不影响「暂停窗口内无新轮」的结论，但说明该 goal 本身也会自然完成，属于本测试的不确定项。
证据：`R03-00-goal-created.png`、`R03-01-attempt1-after.png`、`R03-02-attempt2.png`、`R03-03-window-hit.txt`。

### R04 超大附件：通过

`dd` 造 30MB 文件到 `/sdcard/Download/round39-big30mb.bin` → `MEDIA_SCANNER_SCAN_FILE` 广播生成 `content://media/external/file/1000001134` → `am start -a android.intent.action.SEND -t application/octet-stream --eu android.intent.extra.STREAM <uri> -n io.github.whywood.deepdeep/.MainActivity`。

- App 弹出提示：**「文件超过 25MB，无法添加：round39-big30mb.bin」**（`R04-02-toast-1.png`、`R04-03-toast-2.png`）。
- `files/home/attachments/` 分享前后均为 15 个文件，逐项 diff 无差异（无残留）——`R04-00-attachments-baseline.txt`、`REG-R39.txt`。
- 测试后已删除 30MB 文件及 media 记录。
- 注：该用例实际在 round39 包安装后（18:52:07）执行，行为与 round38 一致。

## 阶段二（round39 包）

包信息：`lastUpdateTime=2026-10-29 18:52:23`，pid 2859（除主动 force-stop 的 smoke 外未重启）。

### 运行中断提示 smoke：部分通过

步骤：会话内发送 `run bash sleep 25 then reply ok` → 2.5s 后 `am force-stop` → 重开 App。

- ✅ 重开后该会话出现灰字提示：`Runs interrupted from the last session: 1. Their result is unknown; tell me to continue`（`INT-01-reopen-hint.png`）。
- ✅ 通知汇总：id=400、channel=`session_results`，标题 **「有未完成项」**，正文「上次有 1 项未完成（提问或运行中断），已失效。点击打开会话继续。」（`INT-02-notification.txt`、`INT-03-notification-title.txt`）。
- ❌ **缺陷 D3：发消息后提示不消失**。发送 `hello` 并开始新一轮后，提示仍显示（4s 后 dump 仍在，`INT-04-after-message.png`）；切到其它会话再切回仍显示（`INT-05-after-switch.png`）；只有 force-stop 重开 App 后才消失（`INT-06-after-restart.png`）。
  - 代码线索：`ChatViewModel.dismissInterruptedNotice()` 只调用持久化 dismiss lambda（清 `interruptedRuns`/`interruptedQuestions`），未从 `mutableState.messages` / `conversationCache` / `interruptedRunNoticeSessions` 移除已插入的 notice；`withCachedInterruptedNotices` 会在再次打开会话时从缓存重新插入。

### 固定提示词性能对比（2 次）

固定流程：点 composer 打开键盘 → 输入固定提示词 → `dumpsys gfxinfo reset` → 发送 → 等 27s → `dumpsys gfxinfo`。
固定提示词：`Write a 700 word essay in English about glaciers, their beauty and their decline. Use headings and three bullet lists.`

| 运行 | 总帧数 | Janky 帧 | Janky 率 | Slow UI thread | Slow issue draw | 50/90/95/99 分位 |
|---|---|---|---|---|---|---|
| Run A | 965 | 94 | 9.74% | 90 | 2 | 8/17/20/24 ms |
| Run B | 1037 | 105 | 10.13% | 102 | 2 | 8/17/20/24 ms |
| 参考：round38 stream-run2 | 1651 | 183 | 11.1% | — | — | — |

原始输出：`artifacts/validation/round39/perf-after-round39.txt`（含三次完整 `dumpsys gfxinfo`）。
**不确定性**（不宣称严格提速）：round38 基准 183/1651 的采集窗口约 20s、回答长度与本次不同；本次显示处于 120Hz 模式（`mActiveModeId=2`），基准采集时的刷新模式未记录；流式增量频率取决于模型输出节奏。因此只宜比较 Janky 率（9.7%/10.1% vs 11.1%，略有下降）与 Slow UI 数量级（90–105 vs 183），帧数不可按比例外推。

### 回归

- 启动/会话恢复/会话打开与切换：正常（重开后恢复上次会话，切换多会话无异常）。
- 抽屉排序：最近使用的会话显示 `Just now` 并排在 Home 第一（`REG-R39.txt` 同批采集）。
- `.corrupt`：仍只有 round37 遗留的 1 个，无新增。
- `FATAL EXCEPTION`：0（阶段二全程）。
- **提醒投递**：让 Agent 创建 19:02 一次性提醒 `REMINDER-TEST-R39`，`schedules.json` 中 `lastDeliveredAt=19:02:00.006`，会话内出现 `Scheduled reminder · REMINDER-TEST-R39` 消息（`REM-01-created.png`、`REM-02-after.png`、`REG-R39.txt`）。投递准时、无 lastError。

## 缺陷清单

| 编号 | 级别 | 项 | 描述 | 线索 | 证据 |
|---|---|---|---|---|---|
| D3 | P2（UI） | round39 运行中断提示 | 发消息后「Runs interrupted…」灰字提示不消失，切换会话也仍在，只有杀进程重开才清 | `ChatViewModel.dismissInterruptedNotice` 未清理已插入 notice 与缓存集合 | `INT-04-after-message.png`、`INT-05-after-switch.png`、`INT-06-after-restart.png` |

## 未完成 / 限制

- R03 仅命中 1 次窗口（3 次未命中已按约定标注，不算失败）；结论为「命中时行为正确」。
- B06 无法观测磁盘中间态，见上「观察边界」。
- 设备在本轮测试期间出现其它来源的 schedule 探针（`probe-1900`/`probe-1901`/`probe-2000`）与会话消息，说明有并发操作；本报告只对上述带证据的观察负责。

## 证据文件

`artifacts/validation/round39/`：`B06-00..06-*`、`R03-00..03-*`、`R04-00..03-*`、`R39-00-new-package.png`、`INT-01..06-*`、`PERF-R39-A.txt`、`PERF-R39-B.txt`、`PERF-R39-01-run1.txt`、`perf-after-round39.txt`、`REM-01-created.png`、`REM-02-after.png`、`REG-R39.txt`。

## D3 复测（修复后，2026-10-29 19:07 包）

包信息：`lastUpdateTime=2026-10-29 19:07:55`（含 D3 修复），pid 5081。原失败记录保留在上一节，本节只记复测结果。

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | 发 `run bash sleep 30 then reply ok` → 2.5s 后 `am force-stop` → 重开 App | 灰字提示出现：`Runs interrupted from the last session: 1. Their result is unknown; tell me to continue`（`RETEST-D3-01-hint-appears.png`） |
| 2 | 发送 `hello` → 4s 后 `uiautomator dump` | **灰字节点为空（提示已消失）**；logcat 出现新 `run session=… chars=5`；6s 后输入框恢复 Send=新一轮正常完成，JSONL 末条 `stopReason=stop`（`RETEST-D3-02-after-hello.png`、`RETEST-D3-03-run-done.png`） |
| 3 | 切到 `start` 会话再切回 `network down retry test` | 灰字提示**未重现**（`RETEST-D3-04-after-switch.png`） |
| 4 | 启动时通知（可选） | id=400、channel=`session_results`，标题「有未完成项」，正文「上次有 1 项未完成（提问或运行中断），已失效。点击打开会话继续。」出现一次（`RETEST-D3-05-notification.txt`） |
| 5 | 回归 | `FATAL EXCEPTION`=0，会话打开/切换正常，pid 5081 全程未重启（`RETEST-D3-06-final.png`、`RETEST-D3-07-summary.txt`） |

结论：**D3 通过**。发消息后灰字提示立即消失、切会话不再重现，与原失败现象（提示常驻到杀进程）相比已修复。
